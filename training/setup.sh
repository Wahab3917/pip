#!/usr/bin/env bash
# One-time setup for training Pip's "Hey Pip" wake-word model.
# Creates training/.venv and downloads the frozen audio front-end + negative data into training/data/.
# Safe to re-run: finished downloads are skipped.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -x .venv/bin/python ]; then
  python3 -m venv .venv
  .venv/bin/pip install -q --upgrade pip
fi
.venv/bin/pip install -q -r requirements.txt

mkdir -p data/models data/negatives

fetch() { # url dest [byte-range]
  if [ -s "$2" ]; then echo "have   $2"; return; fi
  echo "fetch  $2"
  if [ -n "${3:-}" ]; then curl -fL --retry 3 -r "$3" -o "$2.part" "$1"; else curl -fL --retry 3 -o "$2.part" "$1"; fi
  mv "$2.part" "$2"
}

# Frozen front-end from openWakeWord v0.5.1 (Apache-2.0): audio -> mel spectrogram -> Google speech embedding.
OWW=https://github.com/dscripka/openWakeWord/releases/download/v0.5.1
fetch "$OWW/melspectrogram.onnx" data/models/melspectrogram.onnx
fetch "$OWW/embedding_model.onnx" data/models/embedding_model.onnx

# Negative examples ("anything that is NOT Hey Pip"), already in front-end feature space.
HF=https://huggingface.co/datasets/davidscripka/openwakeword_features/resolve/main
# ~10.7 h of continuous embeddings, used only to MEASURE false wakes per hour.
fetch "$HF/validation_set_features.npy" data/negatives/validation_set_features.npy
# The full ACAV100M set is 17 GB; the first ~1.2 GB (400k windows of 16x96 float16) is plenty here.
# 128-byte .npy header + 400000 * 16 * 96 * 2 bytes.
fetch "$HF/openwakeword_features_ACAV100M_2000_hrs_16bit.npy" data/negatives/acav_head.bin "0-$((128 + 400000*16*96*2 - 1))"

# Multi-speaker Piper TTS voices (run through sherpa-onnx) for many different synthetic speakers:
# libritts_r = 904 US-English speakers, vctk = 109 British-English speakers.
TTS=https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models
for v in vits-piper-en_US-libritts_r-medium vits-piper-en_GB-vctk-medium; do
  if [ ! -d "data/models/$v" ]; then
    fetch "$TTS/$v.tar.bz2" "data/models/$v.tar.bz2"
    tar xjf "data/models/$v.tar.bz2" -C data/models && rm "data/models/$v.tar.bz2"
  fi
done

# Separate tiny environment for make_piper.py (needs the older sherpa-onnx 1.12; see that file).
if [ ! -x .venv-tts/bin/python ]; then
  python3 -m venv .venv-tts
fi
.venv-tts/bin/pip install -q "sherpa-onnx==1.12.40" numpy

echo "setup done"
