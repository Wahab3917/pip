# Training Pip's "Hey Pip" wake word

The pretrained sherpa-onnx keyword spotter couldn't hear "Hey Pip" in a real voice on the A32
(see CLAUDE.md). This folder trains a small model for exactly one phrase, using your own voice
recorded through the phone's mic, so it learns the voice and mic it will actually hear.

## How it works
```
mic audio ──► mel spectrogram ──► speech embedding ──► our classifier ──► "Hey Pip?" 0..1
 (16 kHz)      (frozen, ONNX)      (frozen, ONNX,        (trained here,
                                    Google, pretrained)   ~210k weights)
```
The two frozen models come from openWakeWord (Apache-2.0). The embedding model was pretrained on
a huge amount of speech, so the classifier on top only has to learn one decision. That's why a
few dozen recordings are enough. A new 96-number embedding arrives every 80 ms, and the
classifier looks at the last 16 (about 1.3 s).
`pipww/features.py` defines the exact streaming rules. The Android code must match them, and it
is checked against this Python code number for number.

## Steps
```bash
cd training
./setup.sh                                   # once: venv + front-end models + ~1.4 GB negative data

# On the phone: debug build of Pip installed, Pip open, Listening ON, phone on USB.
.venv/bin/python record_phone.py positive --seconds 150    # say "Hey Pip" every ~3 s (≈ 40-50 clips)
.venv/bin/python record_phone.py negative --seconds 240    # talk normally, NEVER say "Hey Pip"
#   (repeat either one later in other rooms / with TV on; more variety = better)

.venv/bin/python make_tts.py                 # synthetic voices (Mac `say`), once
.venv/bin/python build_dataset.py            # recordings -> feature windows (a few minutes)
.venv/bin/python train.py                    # trains, prints the report, exports out/hey_pip.onnx
.venv/bin/python replay.py some.wav          # where would it wake in this recording?
```

### Recording tips (the most important part)
- **Positive:** say it the way you will in real life. Mix normal, quiet, louder, fast and slow.
  Hold the phone close, at arm's length, on the table, in your pocket. Leave about 3 s of
  silence between them, and say nothing else.
- **Negative:** read something aloud, have a phone call or a chat, put the TV on. Say things
  like "hey", "hip", "pick it up", "happy". Never say "Hey Pip". This is what teaches it not to
  wake up by mistake.
- Listen to a few cut clips (`afplay data/phone/pos/<file>.wav`). Delete any that aren't a
  clean "Hey Pip".

## Reading the report
`train.py` tests on clips it never trained on:
- **your clips caught**: a quarter of your "Hey Pip" recordings are held back. Clean = as
  recorded; noisy/roomy = with added noise and reverb.
- **false wakes/hour**: measured on ~10.7 h of unrelated audio (podcasts, TV, music) and on
  the held-back quarter of your negative recordings.

It recommends the lowest threshold that stays within the false-wake budget (default 0.5/hour,
`--max-fa-per-hour`). If too many of your clips are missed, record more positives. If there
are too many false wakes, record more negatives (especially whatever triggered it).

## Files
| | |
|---|---|
| `setup.sh` | venv + downloads (`data/models`, `data/negatives`) |
| `record_phone.py` | records through the app (`ListeningDiagnostics.kt`: `files/debug/record.flag` → `session.pcm`) |
| `make_tts.py` | macOS voices → `data/tts/pos`, `data/tts/neg` |
| `build_dataset.py` | augmentation (speed, reverb, mic colour, gain, background) → `data/features/dataset.npz` |
| `train.py` | classifier, evaluation, threshold → `out/hey_pip.onnx`, `out/hey_pip.json`, `out/report.txt` |
| `replay.py` | streaming check of any WAV |
| `pipww/features.py` | the front-end streaming rules (the reference for the Kotlin port) |
| `pipww/audio.py` | WAV I/O + augmentations |

`data/` and `out/` are generated and ignored by git. Negative data: openWakeWord's
precomputed features (ACAV100M subset + validation set) from Hugging Face
`davidscripka/openwakeword_features`.
