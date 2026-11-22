"""Compare offline speech-to-text models on the same recordings, to pick the one the app ships.

    .venv/bin/python compare_asr.py --make-test          # build a synthetic test set (Mac voices + noise)
    .venv/bin/python compare_asr.py data/asr_test         # compare all models on it
    .venv/bin/python compare_asr.py data/notes            # or on real notes pulled from the phone

A folder may contain refs.tsv ("file.wav<TAB>what was said"); with it you get a word error rate
(WER: the share of words wrong, lower is better). Without it you get the transcripts side by side.
Speed is "real-time factor" on this Mac (seconds of work per second of audio, lower is faster);
the phone is several times slower, but the ranking stays the same.

Models live in data/asr/ (downloaded from the sherpa-onnx "asr-models" release).
"""
import argparse
import re
import subprocess
import tempfile
import time
from pathlib import Path

import numpy as np
import sherpa_onnx

from pipww.audio import SR, coloured_noise, load_wav, noise_scale, reverb, save_wav

ROOT = Path(__file__).resolve().parent
MODELS = ROOT / "data/asr"
MAX_PIECE_S = 20  # Same limit as the app's Transcriber.


def load_model(name: str) -> sherpa_onnx.OfflineRecognizer:
    d = MODELS / name
    if name.startswith("sherpa-onnx-whisper-"):
        size = name.removeprefix("sherpa-onnx-whisper-")
        return sherpa_onnx.OfflineRecognizer.from_whisper(
            encoder=str(d / f"{size}-encoder.int8.onnx"), decoder=str(d / f"{size}-decoder.int8.onnx"),
            tokens=str(d / f"{size}-tokens.txt"), num_threads=2,
        )
    if name.startswith("sherpa-onnx-moonshine-"):
        return sherpa_onnx.OfflineRecognizer.from_moonshine(
            preprocessor=str(d / "preprocess.onnx"), encoder=str(d / "encode.int8.onnx"),
            uncached_decoder=str(d / "uncached_decode.int8.onnx"), cached_decoder=str(d / "cached_decode.int8.onnx"),
            tokens=str(d / "tokens.txt"), num_threads=2,
        )
    raise ValueError(f"don't know how to load {name}")


def transcribe(rec: sherpa_onnx.OfflineRecognizer, audio: np.ndarray) -> str:
    pieces = [audio[i:i + MAX_PIECE_S * SR] for i in range(0, len(audio), MAX_PIECE_S * SR)]
    out = []
    for piece in pieces:
        s = rec.create_stream()
        s.accept_waveform(SR, piece.astype(np.float32))
        rec.decode_stream(s)
        out.append(s.result.text.strip())
    return " ".join(out).strip()


def words(text: str) -> list[str]:
    return re.sub(r"[^a-z0-9' ]", " ", text.lower()).split()


def wer(ref: str, hyp: str) -> tuple[int, int]:
    """(word errors, reference words): substitutions + insertions + deletions, by edit distance."""
    r, h = words(ref), words(hyp)
    d = np.arange(len(h) + 1)
    for i in range(1, len(r) + 1):
        prev, d[0] = d.copy(), i
        for j in range(1, len(h) + 1):
            d[j] = min(prev[j] + 1, d[j - 1] + 1, prev[j - 1] + (r[i - 1] != h[j - 1]))
    return int(d[len(h)]), len(r)


# Short notes of the kind Pip catches; names and numbers on purpose, they're the hard part.
NOTES = [
    "Remember to email Sarah about the budget before Friday.",
    "Idea for the app: let people pin their favourite thoughts to the top.",
    "Buy oat milk, eggs, and a new phone charger.",
    "What if the morning walk went past the lake instead of the main road?",
    "Call the dentist tomorrow at ten and move the appointment to next week.",
    "Book recommendation from Ali: Thinking, Fast and Slow by Daniel Kahneman.",
    "The meeting with the design team should be about onboarding, not colours.",
    "Try training the wake word with more recordings in a noisy room.",
    "Gift idea for mom, a framed photo from the trip to Murree.",
    "Check if the gym is open on Sunday evening after eight.",
    "A podcast about people who changed careers after forty.",
    "Pay the electricity bill, it's due on the twenty-third.",
]
VOICES = ["Samantha", "Daniel", "Karen", "Moira", "Rishi", "Aman", "Tessa"]


def make_test(out: Path) -> None:
    """Each note in a few voices, as clean, noisy, and noisy + room echo: like a phone at arm's length."""
    out.mkdir(parents=True, exist_ok=True)
    available = subprocess.run(["say", "-v", "?"], capture_output=True, text=True).stdout
    voices = [v for v in VOICES if re.search(rf"^{v}\s", available, re.M)]
    rng = np.random.default_rng(3)
    refs = []
    with tempfile.TemporaryDirectory() as tmp:
        for i, note in enumerate(NOTES):
            voice = voices[i % len(voices)]
            aiff = Path(tmp) / "x.aiff"
            subprocess.run(["say", "-v", voice, "-o", str(aiff), note], check=True)
            wav = Path(tmp) / "x.wav"
            subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", str(aiff), str(wav)], check=True)
            clean = load_wav(wav) * 0.5
            pad = np.zeros(int(0.5 * SR), np.float32)
            clean = np.concatenate([pad, clean, pad])
            noisy = clean + coloured_noise(len(clean), rng) * noise_scale(clean, coloured_noise(len(clean), rng), 12)
            room = reverb(clean, rng)
            room = room + coloured_noise(len(room), rng) * noise_scale(room, coloured_noise(len(room), rng), 12)
            for kind, audio in [("clean", clean), ("noisy", noisy), ("room", room)]:
                name = f"{i:02d}_{voice}_{kind}.wav"
                save_wav(out / name, np.clip(audio, -1, 1))
                refs.append(f"{name}\t{note}")
    (out / "refs.tsv").write_text("\n".join(refs) + "\n")
    print(f"wrote {len(refs)} files to {out} (voices: {', '.join(voices)})")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", nargs="?", type=Path)
    ap.add_argument("--make-test", action="store_true")
    ap.add_argument("--models", nargs="*", default=sorted(p.name for p in MODELS.iterdir() if p.is_dir()))
    args = ap.parse_args()
    if args.make_test:
        make_test(ROOT / "data/asr_test")
        return

    refs_file = args.folder / "refs.tsv"
    refs = dict(line.split("\t", 1) for line in refs_file.read_text().splitlines() if line) if refs_file.exists() else {}
    files = sorted(args.folder.glob("*.wav"))
    audio = {f.name: load_wav(f) for f in files}
    total_s = sum(len(a) for a in audio.values()) / SR
    print(f"{len(files)} files, {total_s:.0f} s of audio\n")

    results = {}
    for name in args.models:
        rec = load_model(name)
        start = time.perf_counter()
        results[name] = {f: transcribe(rec, a) for f, a in audio.items()}
        rtf = (time.perf_counter() - start) / total_s
        size = sum(p.stat().st_size for p in (MODELS / name).glob("*.int8.onnx")) + \
            sum(p.stat().st_size for p in (MODELS / name).glob("preprocess.onnx"))
        line = f"{name:40s} {size / 1e6:5.0f} MB   speed {rtf:.3f}x real time"
        if refs:
            by_kind = {}
            for f, hyp in results[name].items():
                kind = f.rsplit("_", 1)[-1].removesuffix(".wav") if f.count("_") >= 2 else "all"
                e, n = wer(refs[f], hyp)
                by_kind.setdefault(kind, [0, 0])
                by_kind[kind][0] += e
                by_kind[kind][1] += n
            errs = sum(v[0] for v in by_kind.values()) / sum(v[1] for v in by_kind.values())
            line += f"   WER {errs:.1%}  (" + ", ".join(f"{k} {v[0] / v[1]:.1%}" for k, v in sorted(by_kind.items())) + ")"
        print(line, flush=True)

    print()
    for f in files:
        print(f"## {f.name}" + (f"\n   said:  {refs[f.name]}" if f.name in refs else ""))
        for name in args.models:
            print(f"   {name.removeprefix('sherpa-onnx-'):22s} {results[name][f.name]}")


if __name__ == "__main__":
    main()
