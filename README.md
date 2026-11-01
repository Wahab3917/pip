# Pip

A personal, voice-first, **offline** Android app that catches a thought the moment it appears.

Say **"Hey Pip"** → a short cue plays → speak → after ~2 s of silence a second cue plays and the
note is saved. It works with the screen off and the phone locked, with no UI. Everything stays on
the device: no network permission, no analytics, no cloud backup.

> Status: Phase 1 (wake word, capture, storage) works on a real phone. Transcription,
> categories and reminders (Phase 2) are next. See [CLAUDE.md](CLAUDE.md) for the full plan.

## How it works

```
mic (16 kHz) ─▶ "Hey Pip" detector ─▶ voice-activity detection ─▶ WAV on disk + Room row ─▶ "caught" cue
                 (custom model)         (Silero VAD)               (audio is saved FIRST)
```

- **Wake word:** our own small classifier trained on openWakeWord's frozen front-end
  (mel spectrogram → Google speech embedding → classifier), run with ONNX Runtime. Training
  code is in [`training/`](training/README.md).
- **Capture:** a foreground microphone service. It never transcribes or does anything slow;
  it only listens, records and saves. Transcription and categories come later, in the background.
- **Storage:** Room database plus WAV files in the app's private storage.

## Build

Requirements: Android Studio (or JDK 17+ and the Android SDK), a device with USB debugging.

```
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
| `training/` | Python tools to record, build a dataset, train and export the wake-word model |
| `tools/` | Small helpers, e.g. `generate_cues.py` makes the placeholder cue sounds |
| `CLAUDE.md` | Architecture, decisions, results and the phase plan |

## Third-party models and libraries

- openWakeWord v0.5.1 front-end models (Apache-2.0)
- Silero VAD, run through sherpa-onnx (`app/libs/`)
- ONNX Runtime for Android
