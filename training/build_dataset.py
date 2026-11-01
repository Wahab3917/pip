"""Turn recordings into training examples (front-end feature windows) for the classifier.

    .venv/bin/python build_dataset.py            # uses whatever is in data/phone and data/tts

Every example is one (16 x 96) window: what the classifier sees at one moment in time.
  positive  = a window that ends just after a complete "Hey Pip"
  negative  = anything else (talk, noise, near-misses, and half-finished "Hey Pi...")

Held out for testing (never trained on):
  * every 4th of YOUR "Hey Pip" clips (data/phone/pos), sorted by name
  * the last 25 % of each of your negative recordings (data/phone/negative_*.wav)

Writes data/features/dataset.npz.
"""
import argparse
import time
from pathlib import Path

import numpy as np

from pipww.audio import (SR, coloured_noise, colour, load_wav, noise_scale, random_crop, resample, reverb,
                         set_peak_db)
from pipww.features import CHUNK, N_EMB, FrontEnd, windows

ROOT = Path(__file__).resolve().parent
# 28 chunks = 2.24 s: the last 16 embeddings need ~10 chunks of mel history before them.
WINDOW_SAMPLES = 28 * CHUNK


def augment(clip: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    c = clip
    if rng.random() < 0.8:
        c = resample(c, rng.uniform(0.88, 1.12))
    if rng.random() < 0.5:
        c = reverb(c, rng)
    if rng.random() < 0.7:
        c = colour(c, rng)
    return set_peak_db(c, rng.uniform(-30, -3))


def place(clip: np.ndarray, background: list, rng: np.random.Generator, end_gap_s: tuple, clean: bool = False):
    """Puts `clip` into a WINDOW_SAMPLES-long window so it ends `end_gap_s` seconds before the window's end."""
    if clean:
        window = coloured_noise(WINDOW_SAMPLES, rng) * 0.02
    elif background and rng.random() < 0.85:
        window = random_crop(background, WINDOW_SAMPLES, rng)
    else:
        window = coloured_noise(WINDOW_SAMPLES, rng)
    gap = int(rng.uniform(*end_gap_s) * SR)
    end = WINDOW_SAMPLES - gap
    c = clip[-end:] if len(clip) > end else clip
    if clean:
        out = window.copy()
    else:
        # Background loudness relative to the voice: SNR from 30 dB (barely audible) to 3 dB (noisy).
        out = window * noise_scale(c, window, rng.uniform(3, 30))
    out[end - len(c):end] += c
    return np.clip(out, -1, 1)


def last_window(fe: FrontEnd, audio: np.ndarray) -> np.ndarray:
    return fe.embeddings(audio)[-N_EMB:]


def load_dir(d: Path) -> list:
    return [load_wav(p) for p in sorted(d.glob("*.wav"))] if d.exists() else []


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--user-aug", type=int, default=60, help="augmented copies per training clip of YOUR voice")
    p.add_argument("--tts-aug", type=int, default=3, help="augmented copies per synthetic clip")
    p.add_argument("--seed", type=int, default=0)
    args = p.parse_args()
    rng = np.random.default_rng(args.seed)
    fe = FrontEnd(ROOT / "data/models")
    t0 = time.time()

    user_pos = load_dir(ROOT / "data/phone/pos")
    user_test = user_pos[3::4]
    user_train = [c for i, c in enumerate(user_pos) if i % 4 != 3]
    # Synthetic speakers: the Mac's voices plus ~1000 Piper speakers (make_piper.py), if generated.
    tts_pos = load_dir(ROOT / "data/tts/pos") + load_dir(ROOT / "data/piper/pos")
    tts_neg = load_dir(ROOT / "data/tts/neg") + load_dir(ROOT / "data/piper/neg")
    phone_neg = [load_wav(p) for p in sorted((ROOT / "data/phone").glob("negative_*.wav"))]
    neg_train_audio = [a[: int(len(a) * 0.75)] for a in phone_neg]
    neg_test_audio = [a[int(len(a) * 0.75):] for a in phone_neg]
    print(f"your clips: {len(user_train)} train / {len(user_test)} test   tts: {len(tts_pos)} pos, {len(tts_neg)} neg   "
          f"your negative recordings: {sum(len(a) for a in phone_neg) / SR / 60:.1f} min")
    if not user_pos:
        print("NOTE: no recordings of your voice yet (data/phone/pos). Training on synthetic voices only.")

    # Background beds to mix under the keyword: your room/talk recordings, else synthetic talk.
    if neg_train_audio:
        background = neg_train_audio
    elif tts_neg:
        background = [np.concatenate(tts_neg[:200])]
    else:
        background = []

    pos = []
    for clip in user_train:
        pos += [last_window(fe, place(augment(clip, rng), background, rng, (0.0, 0.3))) for _ in range(args.user_aug)]
    n_user = len(pos)
    for clip in tts_pos:
        pos += [last_window(fe, place(augment(clip, rng), background, rng, (0.0, 0.3))) for _ in range(args.tts_aug)]
    print(f"positives: {n_user} from your voice + {len(pos) - n_user} synthetic   ({time.time() - t0:.0f} s)")

    neg = []
    for clip in tts_neg:
        for _ in range(2):
            neg.append(last_window(fe, place(augment(clip, rng), background, rng, (0.0, 0.8))))
    # Half-said "Hey Pi..." must not count: cut positives early and label them negative.
    for clip in user_train + tts_pos:
        cut = clip[: int(len(clip) * rng.uniform(0.25, 0.45))]
        neg.append(last_window(fe, place(augment(cut, rng), background, rng, (0.0, 0.3))))
    for _ in range(500):
        neg.append(last_window(fe, coloured_noise(WINDOW_SAMPLES, rng) * 10 ** rng.uniform(-3, 0.5)))
    # Long negative recordings: every window, in three loudness/mic variants.
    for a in neg_train_audio:
        for variant in (a, set_peak_db(a, -6), colour(set_peak_db(a, -25), rng)):
            neg += list(windows(fe.embeddings(variant), stride=2))
    print(f"negatives: {len(neg)}   ({time.time() - t0:.0f} s)")

    test_clean = [last_window(fe, place(c, [], rng, (0.15, 0.15), clean=True)) for c in user_test]
    test_aug = [last_window(fe, place(augment(c, rng), background, rng, (0.0, 0.3))) for c in user_test for _ in range(10)]
    neg_test = [windows(fe.embeddings(a), stride=1) for a in neg_test_audio]
    neg_test_hours = sum(len(a) for a in neg_test_audio) / SR / 3600

    out = ROOT / "data/features/dataset.npz"
    out.parent.mkdir(parents=True, exist_ok=True)
    empty = np.zeros((0, N_EMB, 96), np.float32)
    np.savez_compressed(
        out,
        pos=np.array(pos, np.float32), neg=np.array(neg, np.float32),
        test_clean=np.array(test_clean, np.float32) if test_clean else empty,
        test_aug=np.array(test_aug, np.float32) if test_aug else empty,
        neg_test=np.concatenate(neg_test).astype(np.float32) if neg_test else empty,
        neg_test_hours=np.float32(neg_test_hours),
    )
    print(f"wrote {out.relative_to(ROOT)} in {time.time() - t0:.0f} s")


if __name__ == "__main__":
    main()
