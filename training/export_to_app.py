"""Copy the trained wake-word model into the Android app, plus a reference for the Kotlin port.

    .venv/bin/python export_to_app.py

  app/src/main/assets/wake/        melspectrogram.onnx, embedding_model.onnx, hey_pip.onnx
  app/src/androidTest/assets/      wake_reference.wav + wake_reference.json

The reference is a short recording (synthetic "Hey Pip" between noise and a near-miss) with the
classifier score Python computes after every 80 ms chunk. WakeWordDetectorTest feeds the same
audio through the Kotlin code on a device and checks it gets the same numbers.
"""
import json
import shutil
from pathlib import Path

import numpy as np
import onnxruntime as ort

from pipww.audio import SR, coloured_noise, load_wav, save_wav, set_peak_db
from pipww.features import N_EMB, FrontEnd, windows

ROOT = Path(__file__).resolve().parent
APP = ROOT.parent / "app/src"


def scores_for(fe: FrontEnd, clf, audio_i16: np.ndarray) -> np.ndarray:
    win = windows(fe.embeddings(audio_i16))
    return np.array([clf.run(None, {"embeddings": w[None]})[0][0, 0] for w in win], np.float32)


def main() -> None:
    assets = APP / "main/assets/wake"
    assets.mkdir(parents=True, exist_ok=True)
    for src in [ROOT / "data/models/melspectrogram.onnx", ROOT / "data/models/embedding_model.onnx", ROOT / "out/hey_pip.onnx"]:
        shutil.copy(src, assets / src.name)
        print(f"copied {src.name} ({src.stat().st_size / 1e6:.2f} MB)")
    threshold = json.loads((ROOT / "out/hey_pip.json").read_text())["threshold"]

    fe = FrontEnd(ROOT / "data/models")
    clf = ort.InferenceSession(str(ROOT / "out/hey_pip.onnx"), providers=["CPUExecutionProvider"])
    rng = np.random.default_rng(7)

    # Pick a synthetic "Hey Pip" the model clearly detects, so the test also checks that it fires.
    best, best_score = None, -1.0
    for f in sorted((ROOT / "data/piper/pos").glob("*.wav"))[:40]:
        clip = set_peak_db(load_wav(f), -6)
        a = np.concatenate([coloured_noise(SR, rng) * 0.05, clip, coloured_noise(SR, rng) * 0.05])
        s = scores_for(fe, clf, (a * 32767).astype(np.int16)).max()
        if s > best_score:
            best, best_score = clip, s
    near_miss = set_peak_db(load_wav(sorted((ROOT / "data/piper/neg").glob("*_0.wav"))[0]), -6)

    noise = lambda s: coloured_noise(int(SR * s), rng) * 0.05
    audio = np.concatenate([noise(1.5), best, noise(1.5), near_miss, noise(1.0)])
    audio_i16 = (np.clip(audio, -1, 1) * 32767).astype(np.int16)
    scores = scores_for(fe, clf, audio_i16)
    emb = fe.embeddings(audio_i16)
    probe_chunks = [10, 23, 29, 48, 50]

    test_assets = APP / "androidTest/assets"
    save_wav(test_assets / "wake_reference.wav", audio_i16.astype(np.float32) / 32767)
    # save_wav rounds back to the same int16 values; re-read to be certain the test sees exactly these.
    reread = (load_wav(test_assets / "wake_reference.wav") * 32768).round().astype(np.int16)
    assert np.array_equal(reread, audio_i16), "WAV round trip changed samples"
    (test_assets / "wake_reference.json").write_text(json.dumps({
        "about": "score[i] = classifier probability after chunk (i + 15); chunks are 1280 samples",
        "threshold": threshold,
        "first_scored_chunk": N_EMB - 1,
        "scores": [round(float(s), 6) for s in scores],
        # The 96-number embedding after these chunks, to check the front-end on its own.
        "embeddings": {str(c): [round(float(v), 5) for v in emb[c]] for c in probe_chunks},
    }, indent=1))
    print(f"reference: {len(audio_i16) / SR:.1f} s, peak score {scores.max():.3f}, "
          f"{int((scores >= threshold).sum())} chunks above threshold {threshold}")


if __name__ == "__main__":
    main()
