# Pip

A personal, voice-first, offline Android agent with one job: catch a thought the moment it
appears. Say "Hey Pip" → short "listening" cue → speak → after ~2 s of silence a "caught" cue
plays and the note is saved. Works with the screen off and the phone locked, with no UI.
Later, in the background, each note is transcribed offline, categorised, and any reminder
is scheduled. Everything stays on the device.

Package `one.mindly.pip` · Kotlin + Jetpack Compose + Material 3 · minSdk 29 · target/compile 37.
Test phone: Samsung Galaxy A32 (Android 13, arm64-v8a).

## Principles (do not break these)
- **Never lose a thought.** Save the audio FIRST. Transcript and category are derived and can
  be redone. The "caught" cue plays only after the WAV and the Room row are written.
- **Capture is dumb, intelligence is later.** The capture path only listens, records, saves.
  It never transcribes or calls anything slow.
- **Local-first.** No network calls except an optional one-time model download. No analytics.
  The manifest deliberately has no INTERNET permission. `allowBackup=false` (no cloud backup).
- **Function first, design later.** Plain Material 3 defaults. All colors, fonts, shapes,
  motion, cue sounds and copy go through the theme layer (below). Screens never hard-code them.
- **Connectors later.** The core must not depend on any integration. Only keep the seam
  open: a stable UUID per thought and a `PLANTED` state. Don't build connector code.

## Theme layer (the only files the design pass should need to touch)
- `ui/theme/PipColors.kt`: color schemes (M3 baseline; dynamic color deliberately off)
- `ui/theme/PipType.kt`: typography
- `ui/theme/PipShapes.kt`: shapes + `PipSpacing` (layout dimensions)
- `ui/theme/PipMotion.kt`: animation timings
- `res/raw/cue_listening.wav`, `cue_caught.wav`, `cue_missed.wav`: placeholder tones made
  by `tools/generate_cues.py` (`python3 tools/generate_cues.py` to regenerate)
- `res/values/strings.xml`: all copy
- Also design-adjacent: haptic patterns + the `HAPTICS_ALWAYS` flag in `core/feedback/CuePlayer.kt`
  (they live in core because the UI-less service plays them); notification icon `res/drawable/ic_stat_pip.xml`.

## Architecture
```
one.mindly.pip
├── PipApp.kt              Application; builds AppGraph (manual DI: settings, DB, repository, engine status)
├── MainActivity.kt        permissions (RECORD_AUDIO, POST_NOTIFICATIONS) + hosts HomeScreen
├── core/                  NO Android UI imports
│   ├── audio/             Microphone (AudioRecord 16 kHz mono PCM16), WavFile, PreRollBuffer, ThoughtPlayer
│   ├── wakeword/          WakeWordDetector (our own "Hey Pip" model on onnxruntime), WakeWordTuning (sensitivity → threshold)
│   ├── vad/               SpeechDetector (sherpa-onnx silero VAD)
│   ├── capture/           ListeningEngine (the loop), CaptureSession (pure, unit-tested rules), EngineStatus
│   ├── feedback/          CuePlayer (SoundPool on notification volume + haptics)
│   ├── storage/           Room: Thought entity, ThoughtDao, PipDatabase, Converters, ThoughtRepository
│   ├── processing/        ThoughtProcessor seam (Phase 1: LogOnlyProcessor; Phase 2: WorkManager)
│   └── settings/          PipSettings (SharedPreferences mirrored into a StateFlow)
├── service/               Android glue: ListeningService (foreground, type=microphone), BootReceiver
└── ui/                    StatusText, home/ (HomeScreen + HomeViewModel), theme/
```

### Capture flow
1. `ListeningService` (foreground, type microphone, low-priority "Pip is listening" notification
   with "Pause for 1 hour") runs `ListeningEngine.run()`.
2. The engine reads 100 ms chunks of 16 kHz mono PCM continuously and feeds `WakeWordDetector`.
   It keeps the last 0.5 s in a `PreRollBuffer` so words said right after "Hey Pip" aren't clipped.
3. On "Hey Pip": listening cue (+ haptic in silent/vibrate). The same stream then goes to
   `SpeechDetector` (VAD) and into a `CaptureSession`:
   - no speech within 4 s → missed cue, nothing saved;
   - speech, then 2 s of silence (or the 2-minute cap) → `ThoughtRepository.saveCaught()`:
     WAV to `filesDir/thoughts/<uuid>.wav` → Room row (status CAUGHT) → `ThoughtProcessor.enqueue()`
     → caught cue (or missed cue if saving failed).
4. About once a second the engine checks whether to pause: user pause (`pausedUntilMs`), phone/VoIP
   call (`AudioManager.mode`), battery saver (if enabled). Paused = mic released. If another app
   takes the mic, Android feeds us silence (`isClientSilenced` / exact zeros); we wait it out.
5. `BootReceiver` restarts listening after reboot on Android ≤ 13. On Android 14+ starting a
   microphone foreground service from BOOT_COMPLETED is forbidden, so it posts a "tap to resume"
   notification; opening the app restarts the service (`MainActivity.onResume`).

### Data model (Room, `pip.db`, schema exported to `app/schemas/`)
`Thought(id: UUID, createdAt: Instant, audioPath (relative to filesDir), durationMs, transcript?,
category: IDEA|TASK|REMINDER|SHOPPING|THOUGHT (default THOUGHT), categorySource: RULE|USER,
remindAt?, status: CAUGHT|TRANSCRIBING|READY|FAILED, state: POCKET|PLANTED|LET_GO)`.
Schema changes need a version bump + Migration. Never use destructive migration.

## Models (in `app/src/main/assets`, ~4.0 MB total)
| File | Size | What |
|---|---|---|
| `wake/melspectrogram.onnx` | 1.09 MB | frozen front-end step 1 (openWakeWord v0.5.1, Apache-2.0) |
| `wake/embedding_model.onnx` | 1.33 MB | frozen front-end step 2: Google speech embedding (openWakeWord v0.5.1) |
| `wake/hey_pip.onnx` | 0.86 MB | OUR classifier, trained in `training/` on the user's voice (`training/export_to_app.py` copies it here) |
| `vad/silero_vad.onnx` | 0.64 MB | voice activity detection via sherpa-onnx (`asr-models` release) |

Wake word: `core/wakeword/WakeWordDetector.kt` runs the three `wake/` models through `onnxruntime-android`
(1.28.0) every 80 ms. Its streaming rules must match `training/pipww/features.py`; the instrumented test
`WakeWordDetectorTest` (androidTest, run `ANDROID_SERIAL=<device> ./gradlew connectedDebugAndroidTest`)
checks Kotlin against Python scores. Last run: embeddings within 3e-5, scores within 2e-6.
GOTCHA that cost a day: the mel model scales its output relative to the loudest value in its input, so it
must be run ONE 1760-sample chunk at a time, never batched, in training too.

Native libraries: `app/libs/sherpa-onnx-static-link-onnxruntime-2.0.1.aar` (VAD; ONNX Runtime linked inside,
so it doesn't clash with onnxruntime-android's `libonnxruntime.so`; symbol versions VERS_1.28.2 vs VERS_1.28.0).
Only `arm64-v8a` is packaged (`abiFilters`; other ABIs excluded in `packaging` to avoid duplicate-file errors).
The old pretrained sherpa keyword spotter was removed: it could not hear the user's "Hey Pip" (see below).

Slider → threshold: `WakeWordTuning` maps sensitivity 0..1 to threshold 0.97..0.50, default 0.90.
Debug builds log `[Engine] wake score` lines for every score >= 0.3 (use them to tune the threshold).

### History: why the pretrained KWS model was dropped (A32, 2026-11-01)
- The real user's "Hey Pip" (3 min of attempts, captured via `ListeningDiagnostics`): 1 detection.
  Replayed on desktop: even a clip Whisper transcribed as "Hey, Pip. Hey, Pip." was never detected,
  at any threshold down to 0.01 and with 17 spelling variants. The model barely detects plain "Hey"
  in this audio either. Audio format and levels are fine (16 kHz, no clipping, normal speech spectrum).
- Whisper tiny.en itself often hears this "Hey Pip" as "Hey babe" / "Hip it" / "bib": "Pip" at
  arm's length is acoustically weak (soft plosives, one short vowel).
- Capture → VAD → WAV → Room works end to end (one 6.3 s thought saved after a real detection).
- Debug builds log `[Diag] mic level` every 2 s and keep `files/debug/last10s.wav`
  (`adb exec-out run-as one.mindly.pip cat files/debug/last10s.wav > last10s.wav`).

### Custom "Hey Pip" model (in progress): `training/`
Decision: replace the pretrained KWS with our own classifier on openWakeWord's frozen front-end
(mel spectrogram → Google speech embedding → small trained net). See `training/README.md`.
- Setup is DONE and verified: `training/setup.sh`, `record_phone.py` (records via the debug build's
  `ListeningDiagnostics`), `make_tts.py` (28 Mac voices), `make_piper.py` (1,013 Piper speakers; runs in
  `.venv-tts` with sherpa-onnx 1.12 because 2.0 can't phonemize Piper voices), `build_dataset.py`,
  `train.py`, `replay.py`. Python front-end matches openWakeWord streaming to 2e-5.
- Synthetic-only dry run (no real voice yet): false wakes/hour on 10.7 h validation audio fell
  4.6 → 1.1 (threshold 0.95, `--neg-weight 60`) after adding dropout, weight decay, ACAV negatives
  from 4 places in the dataset, and 1,000 speakers. Target is ≤ 0.5. Recall is unmeasured until the
  user records their own clips (`record_phone.py positive` / `negative`).
- RESULT with the user's voice (46 "Hey Pip" clips from 2 sessions, 9 min of negative talk; 11 clips held out),
  measured AFTER fixing the batching bug, on features identical to what the app computes: 10/11 clean clips
  caught at every threshold 0.3-0.95 (60-72% with added noise/rooms, lower at higher thresholds);
  false wakes on 4.3 h of held-out unrelated speech: 0.70/h at 0.7, 0.23/h at 0.9, 0 at 0.95.
  Default threshold 0.9. Test sets are small: confirm on the phone, and record more clips to improve.
  What mattered: the user's own recordings, 1,000 synthetic speakers, dropout/weight decay, and training on
  the first 60% of the validation audio (time-split; the last 40% is the untouched test).
- Retrain recipe: `record_phone.py positive|negative` (more sessions add to the old ones),
  `build_dataset.py --tts-aug 2`, `train.py --neg-weight 60`, `export_to_app.py`, then run the instrumented test.
### First real-device run (A32, 2026-11-01)
Installed the custom model and tried it by hand (screen on, screen off/locked, background noise, phone in
pocket): it worked in all of them. Log of the first ~9 minutes: 20 wakes heard, 15 thoughts saved, 5 wakes
where the user said nothing (correctly saved nothing, missed cue), no errors. Wake scores were 0.91-1.00.
- CPU cost: the wake word takes ~30 ms per 80 ms chunk on the A32 (~37% of one core, always on) vs 2 ms on the
  emulator. This is the main battery risk. Idea if it hurts: only run the wake-word model when the audio is
  loud enough to be speech (cheap energy gate).
- NOT yet measured: false wakes in everyday life (TV, conversation) and all-day battery use.
- NEXT: an all-day run (note battery % before and after, Pip set to "Unrestricted" in Samsung battery
  settings), count false wakes from `adb logcat -s Pip`, then decide on the energy gate. Then Phase 2.

## Build / run
```
./gradlew installDebug          # build + install on the connected device
./gradlew testDebugUnitTest     # JVM unit tests (CaptureSession, WavFile, WakeWordTuning)
adb logcat -s Pip               # Pip's own log lines ([Engine], [Service], [Storage], …)
adb logcat -s Pip sherpa-onnx   # + native VAD logs
adb logcat --pid=$(adb shell pidof one.mindly.pip)   # everything from the app process
```
If the shell can't find Java: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`;
adb lives in `~/Library/Android/sdk/platform-tools`.

## Phase plan
1. **Hey Pip + capture + storage** ← current. Service, wake word, cues/haptics, VAD capture to WAV,
   Room (status CAUGHT), simple home screen (toggle, sensitivity, recordings with tap-to-play).
2. Transcription, categories, reminders: Whisper base.en via sherpa-onnx in a WorkManager job
   (replaces `LogOnlyProcessor`); keyword categories Idea/Task/Reminder/Shopping/Thought in one
   editable Kotlin file; on-device reminder time parsing + exact alarms.
3. All screens working (Material defaults).
4. Quick-settings tile, onboarding (incl. Samsung "Unrestricted" battery setting), battery tuning.
5. Search, export, nudge.

Later and separate: design pass (theme layer only), connectors.

## Known limitations / notes
- Pre-roll (0.5 s) means a recording may start with the tail of "…Pip". Phase 2 transcription
  should strip a leading "pip".
- Capture buffers the note in memory (≤ 2 min ≈ 3.8 MB) and writes it at the end; a crash
  *during* the note loses that note.
- Samsung may kill background apps unless Pip's battery setting is "Unrestricted" (Phase 4 onboarding).
- A native crash (ONNX Runtime / sherpa) can't be caught in Kotlin; START_STICKY makes Android restart the service.
