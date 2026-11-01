"""Record training audio through Pip's own microphone path on the phone.

    .venv/bin/python record_phone.py positive --seconds 150   # say "Hey Pip" every ~3 s
    .venv/bin/python record_phone.py negative --seconds 240   # talk normally, NEVER say "Hey Pip"

Needs: the phone connected over adb, a DEBUG build of Pip installed, and Listening switched ON.
It works by creating files/debug/record.flag inside the app (see ListeningDiagnostics.kt);
while that file exists the app appends everything it hears to files/debug/session.pcm.

Output:
  data/phone/<kind>_<time>.wav          the whole session
  data/phone/pos/<time>_NNN.wav         (positive only) one clip per "Hey Pip", cut with the VAD
"""
import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

from pipww.audio import SR, save_wav

ROOT = Path(__file__).resolve().parent
PKG = "one.mindly.pip"
ADB = os.environ.get("ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb"))


def pick_device() -> str:
    """The phone's adb serial. Prefers a real device over an emulator; honours $ANDROID_SERIAL."""
    if os.environ.get("ANDROID_SERIAL"):
        return os.environ["ANDROID_SERIAL"]
    lines = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout.splitlines()[1:]
    serials = [l.split()[0] for l in lines if l.strip().endswith("device")]
    real = [s for s in serials if not s.startswith("emulator-")]
    if len(real) == 1:
        return real[0]
    if not real:
        sys.exit("No phone found over USB. Plug it in, unlock it, and allow USB debugging.")
    sys.exit(f"Several phones connected {real}; set ANDROID_SERIAL=<serial> and retry.")


DEVICE = None


def adb(*args: str, binary: bool = False):
    global DEVICE
    DEVICE = DEVICE or pick_device()
    out = subprocess.run([ADB, "-s", DEVICE, *args], capture_output=True)
    if out.returncode != 0:
        sys.exit(f"adb failed: {' '.join(args)}\n{out.stderr.decode().strip()}")
    return out.stdout if binary else out.stdout.decode().strip()


def run_as(cmd: str) -> str:
    return adb("shell", f"run-as {PKG} sh -c '{cmd}'")


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("kind", choices=["positive", "negative"])
    p.add_argument("--seconds", type=int, default=150)
    args = p.parse_args()

    services = adb("shell", f"dumpsys activity services {PKG}")
    if "ListeningService" not in services:
        sys.exit("Pip isn't listening on the phone. Open Pip, switch Listening ON, then retry.")

    run_as("mkdir -p files/debug && rm -f files/debug/session.pcm && touch files/debug/record.flag")
    if args.kind == "positive":
        print("RECORDING. Say \"Hey Pip\" once every 3 seconds, like you would for real.")
        print("Vary it: normal, quiet, louder, phone near, phone at arm's length, phone on the table,")
        print("facing away, fast and slow. Don't say anything else in between.")
    else:
        print("RECORDING. Talk normally (read something aloud, chat, TV on is fine), and include")
        print("sounds like 'hey', 'hip', 'pick it up', 'happy'. NEVER say \"Hey Pip\".")
    try:
        for left in range(args.seconds, 0, -1):
            print(f"\r  {left:3d} s left ", end="", flush=True)
            time.sleep(1)
    finally:
        run_as("rm -f files/debug/record.flag")
        print("\r  stopped.        ")
    time.sleep(1.5)  # let the app notice the flag is gone and close the file

    raw = adb("exec-out", f"run-as {PKG} cat files/debug/session.pcm", binary=True)
    audio = np.frombuffer(raw, "<i2").astype(np.float32) / 32768
    if len(audio) < SR:
        sys.exit("Got almost no audio. Is Listening switched on and is this a debug build?")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    whole = ROOT / f"data/phone/{args.kind}_{stamp}.wav"
    save_wav(whole, audio)
    print(f"saved {whole.relative_to(ROOT)} ({len(audio) / SR:.0f} s, peak {20 * np.log10(np.abs(audio).max() + 1e-9):.1f} dBFS)")

    if args.kind == "positive":
        clips = split_utterances(audio)
        for i, c in enumerate(clips):
            save_wav(ROOT / f"data/phone/pos/{stamp}_{i:03d}.wav", c)
        lens = [len(c) / SR for c in clips]
        print(f"cut {len(clips)} clips into data/phone/pos/ (length {min(lens):.2f}-{max(lens):.2f} s)" if clips
              else "no clips found! Was the phone hearing you?")
        print("Listen to a few of them to be sure they are clean \"Hey Pip\"s (afplay data/phone/pos/<file>).")


def split_utterances(audio: np.ndarray) -> list:
    """Uses the silero VAD to find each spoken "Hey Pip"; keeps 0.6-2.2 s pieces with a little padding."""
    import sherpa_onnx

    vad_model = ROOT / "data/models/silero_vad.onnx"
    if not vad_model.exists():
        vad_model = ROOT.parent / "app/src/main/assets/vad/silero_vad.onnx"
    cfg = sherpa_onnx.VadModelConfig()
    cfg.silero_vad.model = str(vad_model)
    cfg.silero_vad.threshold = 0.4
    cfg.silero_vad.min_silence_duration = 0.35
    cfg.silero_vad.min_speech_duration = 0.2
    cfg.silero_vad.max_speech_duration = 4.0
    cfg.sample_rate = SR
    vad = sherpa_onnx.VoiceActivityDetector(cfg, buffer_size_in_seconds=600)
    for i in range(0, len(audio), 512):
        vad.accept_waveform(audio[i:i + 512])
    vad.flush()
    pad = int(0.15 * SR)
    clips = []
    while not vad.empty():
        seg = vad.front
        start, end = max(0, seg.start - pad), min(len(audio), seg.start + len(seg.samples) + pad)
        if 0.45 * SR <= end - start <= 2.4 * SR:
            clips.append(audio[start:end].copy())
        vad.pop()
    return clips


if __name__ == "__main__":
    main()
