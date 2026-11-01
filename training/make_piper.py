"""Synthesise "Hey Pip" (and near-misses) with ~1000 different Piper TTS speakers.

    .venv-tts/bin/python make_piper.py        # -> data/piper/pos, data/piper/neg

Runs in its OWN environment (.venv-tts, created by setup.sh) with the older sherpa-onnx 1.12,
because sherpa-onnx 2.0 no longer turns text into phonemes for Piper voices by itself.

The Mac's 28 voices aren't varied enough: a model trained on them learns a vague idea of
"Hey Pip" and wakes up on too much other speech. Many speakers (different ages, accents,
pitches, speeds) teach it what is essential about the phrase. Negatives use the SAME voices,
so the model can't cheat by learning "this kind of voice = wake word".
"""
import argparse
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx

from make_tts import NEAR_MISSES, SENTENCES

ROOT = Path(__file__).resolve().parent
SR = 16_000
VOICES = ["vits-piper-en_US-libritts_r-medium", "vits-piper-en_GB-vctk-medium"]
POSITIVE = ["Hey Pip", "Hey, Pip.", "Hey Pip!", "hey pip"]


def resample(a: np.ndarray, factor: float) -> np.ndarray:
    n = int(len(a) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(a)), a).astype(np.float32)


def save_wav(path: Path, audio: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes())


def load_tts(name: str):
    d = ROOT / "data/models" / name
    onnx = next(d.glob("*.onnx"))
    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(onnx), tokens=str(d / "tokens.txt"), data_dir=str(d / "espeak-ng-data")),
            num_threads=4,
        ),
    )
    return sherpa_onnx.OfflineTts(cfg)


def synth(tts, text: str, sid: int, speed: float) -> np.ndarray:
    audio = tts.generate(text, sid=sid, speed=speed)
    a = np.asarray(audio.samples, np.float32)
    return resample(a, audio.sample_rate / SR) if audio.sample_rate != SR else a


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--per-speaker", type=int, default=2, help="positive clips per speaker")
    p.add_argument("--seed", type=int, default=0)
    args = p.parse_args()
    rng = np.random.default_rng(args.seed)
    for name in VOICES:
        tts = load_tts(name)
        tag = "us" if "libritts" in name else "gb"
        n_pos = n_neg = 0
        for sid in range(tts.num_speakers):
            for k in range(args.per_speaker):
                dest = ROOT / f"data/piper/pos/{tag}{sid:03d}_{k}.wav"
                if not dest.exists():
                    save_wav(dest, synth(tts, POSITIVE[rng.integers(len(POSITIVE))], sid, rng.uniform(0.8, 1.25)))
                n_pos += 1
            # Two negatives per speaker: one near-miss, one ordinary sentence.
            for k, text in enumerate([NEAR_MISSES[rng.integers(len(NEAR_MISSES))], SENTENCES[rng.integers(len(SENTENCES))]]):
                dest = ROOT / f"data/piper/neg/{tag}{sid:03d}_{k}.wav"
                if not dest.exists():
                    save_wav(dest, synth(tts, text, sid, rng.uniform(0.85, 1.2)))
                n_neg += 1
            if sid % 100 == 0:
                print(f"  {name}: speaker {sid}/{tts.num_speakers}", flush=True)
        print(f"{name}: {n_pos} positives, {n_neg} negatives")


if __name__ == "__main__":
    main()
