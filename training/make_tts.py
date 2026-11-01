"""Synthesise extra training audio with the Mac's built-in voices (`say`).

    .venv/bin/python make_tts.py

  data/tts/pos/   "Hey Pip" in every English macOS voice, at several speeds and phrasings
  data/tts/neg/   near-misses ("Hey Pete", "hip hip hooray", "pick it up", ...) and ordinary sentences

Synthetic voices teach the model what "Hey Pip" sounds like in general; your own phone
recordings (record_phone.py) teach it YOUR voice and YOUR phone's mic, and matter more.
"""
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent

POSITIVE = ["Hey Pip", "Hey, Pip", "Hey Pip!", "hey pip."]
RATES = [140, 175, 210]

# Things that share sounds with "Hey Pip" but must NOT wake it.
NEAR_MISSES = [
    "Hey", "Hey there", "Hey!", "Pip", "Pip pip", "Hey Pete", "Hey Phil", "Hey Kip", "Hey Tip",
    "Hey Pick", "Hey, pick that up", "Hey, people", "hip hip hooray", "happy", "Happy birthday",
    "hippo", "hit it", "zip it", "flip it", "keep it", "a pinch of salt", "pepper please",
    "the pipe is leaking", "a pit stop", "Hey Bill", "Hey Pam", "Hey Pat", "Hey Peter", "pipe up",
    "Hey, it's a big deal", "hey hey hey", "a tip for you", "chip and dip", "ship it", "Hey Siri",
    "Okay Google", "Alexa", "Hey Ben", "Hey Jim", "Hey Tim", "Hey, beep", "Hey, papa",
]
# Ordinary speech so the model sees lots of non-wake-word talking in synthetic voices too.
SENTENCES = [
    "I think we should leave a little earlier tomorrow morning.",
    "Can you remind me to call my mother this evening?",
    "The meeting was moved to Thursday afternoon at three.",
    "Let's pick up some milk, bread and eggs on the way home.",
    "It has been raining all week and the roads are flooded.",
    "My phone battery keeps dying faster than it used to.",
    "Hey, did you see the game last night? It was amazing.",
    "We need to finish the report before the deadline on Friday.",
    "Please turn off the lights when you leave the room.",
    "The kids were playing outside until it got dark.",
    "I'll send you the address in a message later today.",
    "That restaurant has the best pizza in the whole city.",
    "Happy to help, just let me know what you need.",
    "Hip hop music was playing loudly from the car next door.",
    "He picked up the pieces and put them back in the box.",
    "Keep it simple and people will understand it better.",
]
NOVELTY = {"Albert", "Bad News", "Bahh", "Bells", "Boing", "Bubbles", "Cellos", "Good News", "Jester",
           "Organ", "Superstar", "Trinoids", "Whisper", "Wobble", "Zarvox"}


def english_voices() -> list:
    out = subprocess.run(["say", "-v", "?"], capture_output=True, text=True, check=True).stdout
    voices = []
    for line in out.splitlines():
        m = re.match(r"^(.+?)\s+(en[_-]\w+)\s+#", line)
        name = m.group(1).strip() if m else None
        if name and name.split(" (")[0] not in NOVELTY and name not in voices:
            voices.append(name)
    return voices


def say(voice: str, text: str, rate: int, dest: Path) -> None:
    if dest.exists():
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        aiff = Path(tmp) / "x.aiff"
        subprocess.run(["say", "-v", voice, "-r", str(rate), "-o", str(aiff), text], check=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", str(aiff), str(dest)], check=True)


def slug(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


def main() -> None:
    voices = english_voices()
    print(f"{len(voices)} English voices")
    n = 0
    for v in voices:
        for k, text in enumerate(POSITIVE):  # k keeps "Hey Pip" / "Hey, Pip" / ... apart
            for rate in RATES:
                say(v, text, rate, ROOT / f"data/tts/pos/{slug(v)}_{k}_{rate}.wav")
                n += 1
    print(f"positives: {n}")
    n = 0
    for i, v in enumerate(voices):
        for text in NEAR_MISSES:
            say(v, text, RATES[i % 3], ROOT / f"data/tts/neg/{slug(v)}_{slug(text)}.wav")
            n += 1
        for j, text in enumerate(SENTENCES):
            if (i + j) % 4 == 0:  # a quarter of voices per sentence keeps it quick
                say(v, text, 175, ROOT / f"data/tts/neg/{slug(v)}_s{j:02d}.wav")
                n += 1
    print(f"negatives: {n}")


if __name__ == "__main__":
    main()
