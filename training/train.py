"""Train the "Hey Pip" classifier, measure it honestly, and export it for the app.

    .venv/bin/python train.py

The model is small: (16 x 96 embeddings) -> 128 -> 128 -> 1 probability. It runs every 80 ms.

How it is judged (none of this data is used for training):
  * recall      = share of YOUR held-out "Hey Pip" clips it catches (clean, and with added noise/rooms)
  * false wakes = activations per hour on ~10.7 h of unrelated audio (openWakeWord validation set),
                  and on the held-out part of your own negative recordings.

Writes out/hey_pip.onnx (input "embeddings" [1,16,96] -> output "probability" [1,1]),
out/hey_pip.json (the recommended threshold) and out/report.txt.
"""
import argparse
import json
import time
from pathlib import Path

import numpy as np
import torch
from torch import nn

from pipww.features import EMB_DIM, N_EMB

ROOT = Path(__file__).resolve().parent
REFRACTORY = 12  # after a wake, ignore the next ~1 s (12 x 80 ms), like the app does
THRESHOLDS = [0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95]


class WakeWordNet(nn.Module):
    def __init__(self, hidden: int = 128, dropout: float = 0.3):
        super().__init__()
        # Dropout randomly silences units while training, so the model can't just memorise
        # the training windows (it is switched off automatically at export / evaluation).
        self.net = nn.Sequential(
            nn.Flatten(), nn.Dropout(dropout / 2),
            nn.Linear(N_EMB * EMB_DIM, hidden), nn.LayerNorm(hidden), nn.ReLU(), nn.Dropout(dropout),
            nn.Linear(hidden, hidden), nn.LayerNorm(hidden), nn.ReLU(), nn.Dropout(dropout),
            nn.Linear(hidden, 1),
        )

    def forward(self, x):  # logits
        return self.net(x).squeeze(-1)


class WithSigmoid(nn.Module):
    """What gets exported: probabilities, so the app just compares against a threshold."""
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, embeddings):
        return torch.sigmoid(self.model(embeddings)).unsqueeze(-1)


def load_acav() -> np.ndarray:
    """All downloaded ACAV slices (data/negatives/acav_*.bin), as float32 windows (n, 16, 96).

    acav_head.bin keeps the .npy file's 128-byte header; the other slices are raw window data.
    """
    parts = []
    for f in sorted((ROOT / "data/negatives").glob("acav_*.bin")):
        offset = 128 if f.name == "acav_head.bin" else 0
        n = (f.stat().st_size - offset) // (N_EMB * EMB_DIM * 2)
        parts.append(np.asarray(np.memmap(f, np.float16, "r", offset=offset, shape=(n, N_EMB, EMB_DIM)), np.float32))
    return np.concatenate(parts)


def predict(model, x: np.ndarray, batch: int = 8192) -> np.ndarray:
    model.eval()
    out = []
    with torch.no_grad():
        for i in range(0, len(x), batch):
            out.append(torch.sigmoid(model(torch.from_numpy(np.asarray(x[i:i + batch], np.float32)))).numpy())
    return np.concatenate(out) if out else np.zeros(0)


def count_wakes(scores: np.ndarray, threshold: float) -> int:
    """Activations in a continuous stream of scores, with the app's ~1 s refractory period."""
    n, i = 0, 0
    above = np.flatnonzero(scores >= threshold)
    last = -10 ** 9
    for i in above:
        if i - last > REFRACTORY:
            n += 1
            last = i
    return n


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--steps", type=int, default=6000)
    p.add_argument("--neg-weight", type=float, default=4.0, help="how much more a false wake costs than a miss")
    p.add_argument("--max-fa-per-hour", type=float, default=0.5, help="false-wake budget for choosing the threshold")
    p.add_argument("--dropout", type=float, default=0.3)
    p.add_argument("--weight-decay", type=float, default=0.01)
    p.add_argument("--mining-rounds", type=int, default=3, help="hard-negative mining passes over the ACAV pool")
    p.add_argument("--mine-threshold", type=float, default=0.1, help="ACAV windows scoring above this count as hard")
    p.add_argument("--seed", type=int, default=0)
    args = p.parse_args()
    torch.manual_seed(args.seed)
    rng = np.random.default_rng(args.seed)
    t0 = time.time()

    d = np.load(ROOT / "data/features/dataset.npz")
    pos, neg = d["pos"], d["neg"]
    acav = load_acav()
    val = np.load(ROOT / "data/negatives/validation_set_features.npy", mmap_mode="r")
    val_all = np.lib.stride_tricks.sliding_window_view(np.asarray(val, np.float32), (N_EMB, EMB_DIM))[:, 0]
    # Split the validation audio IN TIME: the first 60% joins the training negatives (and the pool
    # that is mined for hard negatives); the last 40% is never touched and is what we report on.
    cut = int(len(val_all) * 0.6)
    acav = np.concatenate([acav, val_all[:cut]])
    val_windows = val_all[cut + N_EMB:]  # skip a window-length gap so no window straddles the split
    val_hours = len(val_windows) * 0.08 / 3600
    print(f"train: {len(pos)} positive, {len(neg)} own negative, {len(acav)} ACAV negative windows; "
          f"test: {len(d['test_clean'])} of your clips; validation {val_hours:.1f} h")

    model = WakeWordNet(dropout=args.dropout)
    bce = nn.BCEWithLogitsLoss(reduction="none")
    n_pos, n_own, n_acav, n_hard = 256, 256, 512, 256
    hard = np.zeros((0, N_EMB, EMB_DIM), np.float32)

    def run_steps(steps: int, tag: str) -> None:
        opt = torch.optim.AdamW(model.parameters(), lr=1e-3, weight_decay=args.weight_decay)
        sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, steps)
        for step in range(1, steps + 1):
            model.train()
            parts = [pos[rng.integers(0, len(pos), n_pos)], neg[rng.integers(0, len(neg), n_own)],
                     acav[rng.integers(0, len(acav), n_acav)]]
            n_h = n_hard if len(hard) else 0
            if n_h:
                parts.append(hard[rng.integers(0, len(hard), n_h)])
            x = np.concatenate(parts)
            y = np.concatenate([np.ones(n_pos), np.zeros(n_own + n_acav + n_h)]).astype(np.float32)
            w = np.where(y == 1, 1.0, args.neg_weight).astype(np.float32)
            loss = (bce(model(torch.from_numpy(x)), torch.from_numpy(y)) * torch.from_numpy(w)).mean()
            opt.zero_grad()
            loss.backward()
            opt.step()
            sched.step()
            if step % 1000 == 0:
                print(f"  [{tag}] step {step:5d}  loss {loss.item():.4f}  ({time.time() - t0:.0f} s)")

    run_steps(args.steps, "initial")
    # Hard-negative mining: the negatives the model still fires on are the most informative ones,
    # so find them in the ACAV pool (NOT the validation set, which stays untouched for honest
    # testing) and show them to the model much more often.
    for r in range(1, args.mining_rounds + 1):
        scores = predict(model, acav)
        found = acav[scores >= args.mine_threshold]
        hard = np.concatenate([hard, found])
        print(f"mining round {r}: {len(found)} of {len(acav)} ACAV windows scored >= {args.mine_threshold} "
              f"({len(hard)} hard negatives total)")
        run_steps(args.steps // 2, f"mined {r}")

    # ---- honest evaluation ----
    s_clean, s_aug = predict(model, d["test_clean"]), predict(model, d["test_aug"])
    s_val = predict(model, val_windows)
    s_own = predict(model, d["neg_test"])
    own_hours = float(d["neg_test_hours"])
    rows, best = [], None
    for t in THRESHOLDS:
        r_clean = (s_clean >= t).mean() if len(s_clean) else float("nan")
        r_aug = (s_aug >= t).mean() if len(s_aug) else float("nan")
        fa_val = count_wakes(s_val, t) / val_hours
        fa_own = count_wakes(s_own, t) / own_hours if own_hours > 0 else float("nan")
        rows.append((t, r_clean, r_aug, fa_val, fa_own))
        ok = fa_val <= args.max_fa_per_hour and not (fa_own > 2 * args.max_fa_per_hour)
        if ok and best is None:
            best = t
    best = best or THRESHOLDS[-1]

    lines = [
        f"Hey Pip model, trained {time.strftime('%Y-%m-%d %H:%M')}, {args.steps} steps",
        f"training: {len(pos)} positive windows, {len(neg)} own negatives + {len(acav)} ACAV negatives",
        f"settings: neg-weight {args.neg_weight}, dropout {args.dropout}, weight-decay {args.weight_decay}, mining rounds {args.mining_rounds}",
        f"tested on {len(s_clean)} of YOUR clips never seen in training; false wakes on {val_hours:.1f} h validation audio"
        + (f" + {own_hours * 60:.1f} min of your own negative recordings" if own_hours else ""),
        "",
        "threshold | your clips caught (clean) | (noisy/roomy) | false wakes/hour (validation) | (your recordings)",
    ]
    for t, rc, ra, fv, fo in rows:
        mark = "  <- recommended" if t == best else ""
        lines.append(f"   {t:.2f}   |        {rc:6.1%}            |    {ra:6.1%}    |           {fv:6.2f}               |   {fo:6.2f}{mark}")
    lines.append("")
    lines.append(f"recommended threshold {best} = lowest threshold with <= {args.max_fa_per_hour} false wakes/hour")
    report = "\n".join(lines)
    print("\n" + report)

    out = ROOT / "out"
    out.mkdir(exist_ok=True)
    (out / "report.txt").write_text(report + "\n")
    (out / "hey_pip.json").write_text(json.dumps({"threshold": best, "n_embeddings": N_EMB, "embedding_dim": EMB_DIM}, indent=2))
    torch.save(model.state_dict(), out / "hey_pip.pt")
    export = WithSigmoid(model).eval()
    torch.onnx.export(export, torch.zeros(1, N_EMB, EMB_DIM), str(out / "hey_pip.onnx"),
                      input_names=["embeddings"], output_names=["probability"], opset_version=13, dynamo=False)
    print(f"\nexported {(out / 'hey_pip.onnx').relative_to(ROOT)} ({(out / 'hey_pip.onnx').stat().st_size / 1e6:.2f} MB)")


if __name__ == "__main__":
    main()
