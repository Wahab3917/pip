#!/usr/bin/env python3
"""Generates Pip's three cue sounds into app/src/main/res/raw.

Source: "Pip — Design Guidelines" → Sound and haptics. The three cues share one timbre, a soft
wooden pluck like a kalimba, so they sound like one family:

  cue_listening  "Go ahead"           two rising notes a fifth apart (A5 → E6), ~180 ms
  cue_caught     "Got it"             the same two notes falling and settling (E6 → A5), ~250 ms + ring
  cue_missed     "Didn't catch that"  one low, muted tick, ~100 ms

No cue is louder than a normal notification: peaks are kept at -9 dBFS.

Usage (from the project root):  python3 tools/generate_cues.py
Uses only the Python standard library.
"""
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100
PEAK = 10 ** (-9 / 20)  # -9 dBFS
OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

A5, E6, E4 = 880.0, 1318.51, 329.63


def pluck(freq: float, ms: float, decay_ms: float, partials=((1.0, 1.0, 1.0), (2.0, 0.06, 0.6), (5.93, 0.10, 0.25))):
    """A kalimba-like pluck: a few sine partials (ratio, level, decay factor) with a 2 ms attack.

    A kalimba tine has a strong fundamental and a bright, slightly out-of-tune overtone (~5.9x)
    that dies away much faster; that overtone is what makes it sound plucked rather than beeped.
    """
    n = int(SAMPLE_RATE * ms / 1000)
    attack = int(SAMPLE_RATE * 0.002)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        s = 0.0
        for ratio, level, decay in partials:
            tau = decay_ms / 1000 * decay
            s += level * math.exp(-t / tau) * math.sin(2 * math.pi * freq * ratio * t)
        out.append(s * min(1.0, i / attack))
    return out


def low_pass(samples, cutoff_hz: float):
    """One-pole low-pass: softens the tick so it sounds muted, not clicky."""
    a = math.exp(-2 * math.pi * cutoff_hz / SAMPLE_RATE)
    y, out = 0.0, []
    for x in samples:
        y = (1 - a) * x + a * y
        out.append(y)
    return out


def mix(*voices):
    """Each voice is (start_ms, samples); returns their sum."""
    length = max(int(SAMPLE_RATE * start / 1000) + len(s) for start, s in voices)
    out = [0.0] * length
    for start, s in voices:
        offset = int(SAMPLE_RATE * start / 1000)
        for i, v in enumerate(s):
            out[offset + i] += v
    return out


def finish(samples):
    """Normalise to PEAK and fade the last 10 ms so nothing clicks."""
    top = max(abs(s) for s in samples) or 1.0
    fade = int(SAMPLE_RATE * 0.010)
    n = len(samples)
    return [s / top * PEAK * min(1.0, (n - 1 - i) / fade) for i, s in enumerate(samples)]


def write(name: str, samples) -> None:
    path = OUT_DIR / f"{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))
    print(f"wrote {path.name}: {len(samples) * 1000 // SAMPLE_RATE} ms")


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    # "Go ahead": A5 then E6, a fifth up, quick.
    write("cue_listening", finish(mix((0, pluck(A5, 120, 60)), (75, pluck(E6, 150, 70)))))
    # "Got it": E6 then A5, the second note settles and rings a little longer.
    write("cue_caught", finish(mix((0, pluck(E6, 140, 70)), (100, pluck(A5, 330, 130)))))
    # "Didn't catch that": one low, muted tick.
    write("cue_missed", finish(low_pass(pluck(E4, 100, 35, partials=((1.0, 1.0, 1.0), (2.0, 0.15, 0.5))), 900)))
