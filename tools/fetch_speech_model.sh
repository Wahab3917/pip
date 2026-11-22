#!/usr/bin/env bash
# Downloads the speech-to-text model into the app's assets (too big to keep in git).
# Run once before building:  tools/fetch_speech_model.sh
# Must match the file names in core/transcription/SpeechModel.kt.
set -euo pipefail
NAME=sherpa-onnx-whisper-base.en
FILES=(base.en-encoder.int8.onnx base.en-decoder.int8.onnx base.en-tokens.txt)
URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$NAME.tar.bz2

cd "$(dirname "$0")/.."
DEST=app/src/main/assets/asr
if [ -f "$DEST/${FILES[0]}" ] && [ -f "$DEST/${FILES[1]}" ] && [ -f "$DEST/${FILES[2]}" ]; then
  echo "Speech model already in $DEST"; exit 0
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
echo "Downloading $NAME (~200 MB)…"
curl -fL --progress-bar -o "$TMP/model.tar.bz2" "$URL"
tar xjf "$TMP/model.tar.bz2" -C "$TMP"
mkdir -p "$DEST"
for f in "${FILES[@]}"; do cp "$TMP/$NAME/$f" "$DEST/"; done
echo "Done: $(du -sh "$DEST" | cut -f1) in $DEST"
