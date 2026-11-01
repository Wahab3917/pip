"""Run the trained model over any recording, exactly as the app would in real time.

    .venv/bin/python replay.py some.wav [more.wav ...] [--threshold 0.5]

Prints each moment the model would wake, plus the highest score it reached, which is handy for
checking phone recordings (e.g. the app's debug/last10s.wav) or a new negative recording.
"""
import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort

from pipww.audio import load_wav
from pipww.features import FrontEnd, windows

ROOT = Path(__file__).resolve().parent


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("wavs", nargs="+", type=Path)
    p.add_argument("--threshold", type=float, default=None)
    args = p.parse_args()
    threshold = args.threshold or json.loads((ROOT / "out/hey_pip.json").read_text())["threshold"]
    fe = FrontEnd(ROOT / "data/models")
    clf = ort.InferenceSession(str(ROOT / "out/hey_pip.onnx"), providers=["CPUExecutionProvider"])
    for wav in args.wavs:
        win = windows(fe.embeddings(load_wav(wav)))
        scores = np.array([clf.run(None, {"embeddings": w[None]})[0][0, 0] for w in win])
        # Window i ends at embedding i + 15, i.e. after (i + 16) chunks of 80 ms.
        times = (np.arange(len(scores)) + 16) * 0.08
        wakes, last = [], -1e9
        for t, s in zip(times, scores):
            if s >= threshold and t - last > 1.0:
                wakes.append(f"{t:.2f}s ({s:.2f})")
                last = t
        peak = f"{scores.max():.3f} at {times[scores.argmax()]:.2f}s" if len(scores) else "-"
        print(f"{wav.name}: {len(wakes)} wake(s) {wakes}   max score {peak}")


if __name__ == "__main__":
    main()
