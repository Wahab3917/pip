"""WAV I/O and the augmentations that turn a few dozen recordings into thousands of examples.

All audio here is float32 mono at 16 kHz in [-1, 1].
"""
import wave
from pathlib import Path

import numpy as np

SR = 16_000


def load_wav(path: Path) -> np.ndarray:
    with wave.open(str(path)) as w:
        assert w.getsampwidth() == 2, f"{path}: need 16-bit PCM"
        a = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
        if w.getnchannels() > 1:
            a = a.reshape(-1, w.getnchannels()).mean(1)
        if w.getframerate() != SR:
            a = resample(a, w.getframerate() / SR)
    return a


def save_wav(path: Path, audio: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(audio, -1, 1) * 32767).astype(np.int16).tobytes())


def resample(a: np.ndarray, factor: float) -> np.ndarray:
    """Plays `a` `factor` times faster (factor > 1 = shorter, higher pitch). Linear interpolation."""
    n = int(len(a) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(a)), a).astype(np.float32)


def rms(a: np.ndarray) -> float:
    return float(np.sqrt(np.mean(a ** 2) + 1e-12))


def set_peak_db(a: np.ndarray, db: float) -> np.ndarray:
    return a * (10 ** (db / 20) / (np.abs(a).max() + 1e-9))


def colour(a: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """Random band-limiting, like different mics / distances: a soft high-pass and low-pass."""
    spec = np.fft.rfft(a)
    f = np.fft.rfftfreq(len(a), 1 / SR)
    hp = rng.uniform(60, 350)
    lp = rng.uniform(2800, 7800)
    gain = 1 / np.sqrt(1 + (hp / (f + 1e-3)) ** 4) / np.sqrt(1 + (f / lp) ** 4)
    return np.fft.irfft(spec * gain, n=len(a)).astype(np.float32)


def reverb(a: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """A synthetic room: exponentially decaying noise as impulse response."""
    rt60 = rng.uniform(0.15, 0.7)
    n = int(rt60 * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-6.9 * t / rt60)
    ir[0] = 1.0 / rng.uniform(0.3, 1.0)  # direct path, louder when closer
    ir /= np.sqrt(np.sum(ir ** 2))
    m = len(a) + n - 1
    size = 1 << (m - 1).bit_length()
    wet = np.fft.irfft(np.fft.rfft(a, size) * np.fft.rfft(ir, size), size)[: len(a)]
    return wet.astype(np.float32)


def noise_scale(signal: np.ndarray, noise: np.ndarray, snr_db: float) -> float:
    """The factor to multiply `noise` by so that `signal` is `snr_db` dB louder than it."""
    return rms(signal) / (rms(noise) * 10 ** (snr_db / 20))


def random_crop(pool: list, length: int, rng: np.random.Generator) -> np.ndarray:
    """A random `length`-sample piece of a random long recording from `pool` (looped if short)."""
    src = pool[rng.integers(len(pool))]
    if len(src) < length:
        src = np.tile(src, length // len(src) + 1)
    start = rng.integers(0, len(src) - length + 1)
    return src[start:start + length].copy()


def coloured_noise(length: int, rng: np.random.Generator) -> np.ndarray:
    """White-to-brown noise (fans, hiss, rumble)."""
    spec = np.fft.rfft(rng.standard_normal(length))
    f = np.fft.rfftfreq(length, 1 / SR)
    spec /= (f + 20) ** rng.uniform(0, 1.0)
    n = np.fft.irfft(spec, n=length)
    return (n / (rms(n) + 1e-9) * 0.01).astype(np.float32)
