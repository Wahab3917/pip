#!/usr/bin/env python3
"""Generates Pip's three PLACEHOLDER cue sounds into app/src/main/res/raw.

The design pass will replace these WAVs with real sound design; the app only
references them by name (cue_listening, cue_caught, cue_missed).

Usage (from the project root):  python3 tools/generate_cues.py
Uses only the Python standard library.
"""
import math
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 22050
OUT_DIR = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def tone(freq_start, freq_end, ms, volume=0.5):
    """A sine sweep with a short fade in/out so it doesn't click."""
    n = int(SAMPLE_RATE * ms / 1000)
    fade = int(SAMPLE_RATE * 0.008)  # 8 ms
    phase, out = 0.0, []
    for i in range(n):
        freq = freq_start + (freq_end - freq_start) * i / n
        phase += 2 * math.pi * freq / SAMPLE_RATE
        env = min(1.0, i / fade, (n - 1 - i) / fade)
        out.append(volume * env * math.sin(phase))
    return out


def silence(ms):
    return [0.0] * int(SAMPLE_RATE * ms / 1000)


def write(name, samples):
    path = OUT_DIR / f"{name}.wav"
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s * 32767)) for s in samples))
    print(f"wrote {path.relative_to(OUT_DIR.parent.parent.parent.parent.parent)}")


if __name__ == "__main__":
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    # "I'm listening": a quick rising chirp.
    write("cue_listening", tone(660, 990, 140))
    # "Caught it": two short bright notes going up.
    write("cue_caught", tone(880, 880, 70) + silence(40) + tone(1320, 1320, 90))
    # "Missed": one soft falling note.
    write("cue_missed", tone(440, 300, 220, volume=0.4))
