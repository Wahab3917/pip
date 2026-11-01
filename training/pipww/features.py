"""The frozen audio front-end: 16 kHz audio -> one 96-number "embedding" every 80 ms.

This is openWakeWord's front-end (a mel-spectrogram ONNX model followed by Google's
pretrained speech-embedding ONNX model). Because the embedding model was trained on a huge
amount of speech, our own model only has to learn "is this window Hey Pip?" on top of it,
which is why a few dozen of your recordings are enough.

THE STREAMING RULES (the Android port in Kotlin must do exactly the same):
  * Audio is processed in chunks of CHUNK = 1280 samples (80 ms), int16 values as float32
    (NOT divided by 32768).
  * For each chunk, the mel model runs on the last CONTEXT + CHUNK = 1760 samples
    (history starts as zeros). Its output frames are transformed with x / 10 + 2 and appended
    to a mel buffer that starts as MEL_WINDOW rows of 1.0.
  * Then the embedding model runs on the last MEL_WINDOW = 76 mel rows -> one 96-dim embedding.
  * The wake-word classifier looks at the last N_EMB = 16 embeddings (~1.3 s).
"""
from pathlib import Path

import numpy as np
import onnxruntime as ort

SAMPLE_RATE = 16_000
CHUNK = 1280
CONTEXT = 480
MEL_WINDOW = 76
MEL_BINS = 32
EMB_DIM = 96
N_EMB = 16


class FrontEnd:
    def __init__(self, models_dir: Path):
        opts = ort.SessionOptions()
        opts.inter_op_num_threads = 1
        opts.intra_op_num_threads = 4
        self.mel = ort.InferenceSession(str(models_dir / "melspectrogram.onnx"), opts, providers=["CPUExecutionProvider"])
        self.emb = ort.InferenceSession(str(models_dir / "embedding_model.onnx"), opts, providers=["CPUExecutionProvider"])
        self.mel_in = self.mel.get_inputs()[0].name
        self.emb_in = self.emb.get_inputs()[0].name

    def embeddings(self, audio: np.ndarray) -> np.ndarray:
        """All embeddings for `audio` (int16 or float in [-1,1]), exactly as streaming would produce them.

        Returns shape (n_chunks, 96): row k is the embedding available after chunk k.
        Trailing samples that don't fill a whole chunk are ignored.
        """
        x = to_int16_float(audio)
        n_chunks = len(x) // CHUNK
        if n_chunks == 0:
            return np.zeros((0, EMB_DIM), np.float32)
        x = np.concatenate([np.zeros(CONTEXT, np.float32), x[: n_chunks * CHUNK]])
        # Every chunk's mel input is the 1760 samples ending at that chunk. They must be run ONE AT A
        # TIME: the mel model clips quiet values relative to the loudest value in its whole input
        # (ReduceMax, "80 dB below max"), so batching chunks lets one chunk's loudness change the
        # others, and the features would no longer match what the app computes while streaming.
        mel = np.stack([
            self.mel.run(None, {self.mel_in: x[k * CHUNK:k * CHUNK + CONTEXT + CHUNK][None]})[0]
            for k in range(n_chunks)
        ])
        mel = mel.reshape(n_chunks, -1, MEL_BINS) / 10 + 2
        frames = mel.shape[1]
        buf = np.concatenate([np.ones((MEL_WINDOW, MEL_BINS), np.float32), mel.reshape(-1, MEL_BINS)])
        # After chunk k the buffer holds MEL_WINDOW + (k+1)*frames rows; the window is the last 76.
        ends = MEL_WINDOW + (np.arange(n_chunks) + 1) * frames
        win = buf[ends[:, None] - MEL_WINDOW + np.arange(MEL_WINDOW)[None, :]]  # (n_chunks, 76, 32)
        out = []
        for i in range(0, n_chunks, 512):
            out.append(self.emb.run(None, {self.emb_in: win[i:i + 512, :, :, None].astype(np.float32)})[0])
        return np.concatenate(out).reshape(n_chunks, EMB_DIM).astype(np.float32)


def windows(emb: np.ndarray, stride: int = 1) -> np.ndarray:
    """Every run of N_EMB consecutive embeddings: (n, 16, 96). Row i ends at embedding i + 15."""
    if len(emb) < N_EMB:
        return np.zeros((0, N_EMB, EMB_DIM), np.float32)
    starts = np.arange(0, len(emb) - N_EMB + 1, stride)
    return emb[starts[:, None] + np.arange(N_EMB)[None, :]]


def to_int16_float(audio: np.ndarray) -> np.ndarray:
    """The front-end wants int16-scale values stored as float32."""
    if audio.dtype == np.int16:
        return audio.astype(np.float32)
    return (np.clip(audio, -1, 1) * 32767).astype(np.float32)
