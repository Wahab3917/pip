# Pip

A personal, voice-first, **offline** Android app that catches an idea the moment it appears.

Say **"Hey Pip"**, or tap the **Pip** tile in quick settings or **Catch** on the home-screen widget → a short
cue plays → speak → after ~2 s of silence a second cue plays and the note is saved. It works with the
screen off and the phone locked. In the background each note is transcribed offline; open a note to
read it, with the recording above. Everything stays on the device: no network permission, no
analytics, no cloud backup.

> Status: "Hey Pip" capture works on a real phone. The Catch tile, widget and offline transcription
> work on the emulator and are being tried on the phone. See [CLAUDE.md](CLAUDE.md) for details.

## How it works

```
"Hey Pip" / Catch tile / widget ─▶ record until 2 s of silence ─▶ WAV + Room row ─▶ "caught" cue
                                    (Silero VAD)                   (audio saved FIRST)
                                                                         │
                                          background job (WorkManager) ◀─┘
                                          Whisper base.en, offline ─▶ transcript in the note
```

- **Wake word:** our own small classifier trained on openWakeWord's frozen front-end
  (mel spectrogram → Google speech embedding → classifier), run with ONNX Runtime. Training
  code is in [`training/`](training/README.md).
- **Capture:** a foreground microphone service. It never transcribes or does anything slow;
  it only listens, records and saves. Transcription and categories come later, in the background.
- **Storage:** Room database plus WAV files in the app's private storage.
- **Transcription:** Whisper base.en through sherpa-onnx, in a WorkManager job, fully offline. Long
  notes are cut at pauses first. `training/compare_asr.py` compares speech models.

## Build

Requirements: Android Studio (or JDK 17+ and the Android SDK), a device with USB debugging.

```
tools/fetch_speech_model.sh     # once: downloads the ~160 MB speech model (not kept in git)
./gradlew installDebug          # build and install on the connected device
./gradlew testDebugUnitTest     # JVM unit tests
adb logcat -s Pip               # Pip's own log lines
```

The wake-word model in `app/src/main/assets/wake/hey_pip.onnx` was trained on one person's voice.
To make it hear **you**, record your own clips and retrain: see [`training/README.md`](training/README.md).
Your recordings stay in `training/data/`, which is git-ignored.

## Project layout

| Path | What |
|---|---|
| `app/` | The Android app (Kotlin, Jetpack Compose, Material 3; minSdk 29) |
| `training/` | Python tools for the wake-word model, plus `compare_asr.py` to compare speech-to-text models |
| `tools/` | Small helpers, e.g. `generate_cues.py` makes the placeholder cue sounds |
| `CLAUDE.md` | Architecture, decisions, results and the phase plan |

## Credits

- **Logo:** "Sunflower Seed" by Amethyst Studio from [the Noun Project](https://thenounproject.com), licensed
  under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). Recoloured and filled for Pip.
- **Fonts:** Newsreader and Instrument Sans, SIL Open Font License (see [`licenses/`](licenses/)).

## Third-party models and libraries

- openWakeWord v0.5.1 front-end models (Apache-2.0)
- Silero VAD, run through sherpa-onnx (`app/libs/`)
- Whisper base.en (OpenAI, MIT), run through sherpa-onnx
- ONNX Runtime for Android, WorkManager
