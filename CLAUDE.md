# Pip

A personal, voice-first, offline Android app with one job: catch an idea the moment it
appears. Trigger → short "listening" cue → speak → after ~2 s of silence a "caught" cue plays
and the note is saved. Three triggers: say "Hey Pip" (when switched on), tap the "Pip" tile in the
quick-settings panel (works on the lock screen), or tap "Catch" on the home-screen widget.
In the background each note is then transcribed offline; opening a note shows the text with
the recording above it, and lets you edit the text or delete the note. Everything stays on the device.

**Scope is deliberately small (user decision, 2026-11-21): Pip only saves ideas.** No categories,
tasks, reminders or a second tile. Notes are in English.

Package `one.mindly.pip` · Kotlin + Jetpack Compose + Material 3 · minSdk 29 · target/compile 37.
Test phone: Samsung Galaxy A32 (Android 13, arm64-v8a).

## Principles (do not break these)
- **Never lose a thought.** Save the audio FIRST. Transcript and category are derived and can
  be redone. The "caught" cue plays only after the WAV and the Room row are written.
- **Capture is dumb, intelligence is later.** The capture path only listens, records, saves.
  It never transcribes or calls anything slow.
- **Local-first.** No network calls except an optional one-time model download. No analytics.
  The manifest deliberately has no INTERNET permission. `allowBackup=false` (no cloud backup).
- **Design lives in the theme layer.** The look, sound and voice come from `docs/design-guidelines.md`
  (the user's own doc). All colors, fonts, shapes, motion, cue sounds and copy go through the theme
  layer (below). Screens never hard-code them.
- **Connectors later.** The core must not depend on any integration. Only keep the seam
  open: a stable UUID per thought and a `PLANTED` state. Don't build connector code.

## Theme layer (applied from `docs/design-guidelines.md`, 2026-11-22)
- `ui/theme/PipColors.kt`: the guideline tokens (Paper, Card, Ink, Graphite, Hairline, Sprout, Amber, Clay) mapped
  onto M3 roles, light + warm-charcoal dark; dynamic color off
- `ui/theme/PipType.kt` + `res/font/`: Instrument Sans for the interface (`MaterialTheme.typography`), Newsreader
  for the user's words (`PipType.thought` 19sp / `thoughtLarge`), `PipType.meta` (12sp, tabular), wordmark, day label.
  Variable fonts, bundled (OFL, see `/licenses`)
- `ui/theme/PipShapes.kt`: 16dp cards, hairline width, `PipSpacing` (8dp grid, seed button, overlay sizes)
- `ui/theme/PipMotion.kt`: ~200 ms fades/slides, seed breath amount, shimmer speed
- `res/values/logo.xml`: THE SEED: "Sunflower Seed" by Amethyst Studio, Noun Project, CC BY 3.0 (credited in
  README; credit text removed from the SVG). 504×504 viewport. `seed_solid_path` = filled outline (`ic_seed_solid`,
  `ic_stat_pip` notification + tile, widget, wordmark dot, `ic_launcher_monochrome` themed icon);
  `seed_lines_path` = line art (`ic_seed_lines`, thickened with a 10-unit stroke). Full seed = paper fill + deep
  green lines (`PipColors.seedLight/seedDark` via `LocalSeedColors`; app icon colours in `colors.xml`).
- `res/raw/cue_*.wav`: from `tools/generate_cues.py`: kalimba plucks A5→E6 (listening) and E6→A5 (caught), peaks -9 dBFS,
  and "soft sigh" for missed (one note gliding 700→430 Hz, 260 ms, peak -8 dBFS; user's choice 2026-11-22 after the
  first low E4 tick proved too quiet on a phone speaker at low ring volume). Haptics in `core/feedback/CuePlayer.kt` (short tap / two soft taps / dull tap).
- `res/values/strings.xml`: all copy, in the guidelines' voice (caught, pocket, "Writing it down…")
- `res/values(-night)/colors.xml`, `dimens.xml`, `layout/widget_pip.xml`, `drawable/widget_*`: the widget and app
  icon colors (RemoteViews can't use Compose); `res/values/themes.xml`: Paper window background (DayNight)
- `res/drawable/ic_*.xml`: Material icons, tinted by the theme

### Design pass decisions (user, 2026-11-22)
Applied the user's guidelines with: kalimba cues; capture overlay KEPT (shown over the app whenever Pip is
catching, any trigger): round ✕ and ✓ icon buttons side by side at the bottom (thumb reach); ✓ / tap anywhere /
back = finish now and save; ✕ = discard (engine `requestCancel`, missed cue); note = bottom sheet (text first, then player), Edit grows the SAME sheet smoothly to
full height as the editor (animateContentSize; swipe-to-dismiss and back-dismiss off while editing, back cancels the
edit); Settings page: Dark mode (app-own setting, LIGHT by default regardless of the phone; drives Compose,
system-bar icons and, on Android 12+, `UiModeManager.setApplicationNightMode`; MainActivity declares
configChanges="uiMode" so it isn't recreated, and PipTheme crossfades every colour over 350 ms), Pause in battery saver (only pauses
"Hey Pip"), then Hey Pip switch + sensitivity. Top bar: plain "Pip" wordmark, capital P, no seed dot + a "Hey Pip" pill with a
small switch inside (toggles listening). Dialogs use `ConfirmDialog` (usePlatformDefaultWidth=false: Samsung's
default dialog is edge to edge). Tile uses `ic_tile_pip` (seed at 80 %). Tile and widget are available from install
(user adds them; Android has no app-side on/off for them). Widget = "calm card" (final layout 2026-11-22, after three rounds with the user): top row "Pip ›" (opens the app) on
the left and the "Hey Pip" switch with its label UNDER it on the right (tap = toggle listening); everything below is one
big round button (an ImageView of a vector circle, scaleType fitCenter, so it is always the biggest circle that fits)
holding the seed and "Catch a\nthought" (two lines so it always fits). While catching: a stop square and "Listening…"
(no red dot, no hint text); tapping finishes + saves (QuickActionActivity ACTION_FINISH → ListeningService.finishCatch).
Two layouts (widget_pip / widget_pip_dark, colours wl_* / wd_*) chosen from Pip's dark-mode setting; the switch is a
drawable (widget_sw_on/off_l/d); PipWidget.build() renders a state; PipApp refreshes the widget when Hey Pip, dark mode
or the Capturing state changes. Picker text: "Catch a thought, turn on the wake word trigger."; `previewLayout` shows the
real widget in the picker (Android 12+). The user's 2x2 on One UI is a TALL rectangle (~180 x 260 dp on the 1080x2400,
420 dpi phone), not a square. `WidgetRenderTest` (androidTest) renders the real RemoteViews at 150x215…190x275 dp, light/dark,
idle/catching, saves PNGs to files/widget-render/ and asserts text stays ≥ 4 dp inside the circle (sizes ≥ 170x245). Known limitation: boxes smaller than the real one
(150x215, or a launcher with square 2x2 cells ~180x180 → circle ~99 dp) crowd the label; rendered but not asserted.
Round button = circle inset 8 dp each side (≈135 dp on the user's phone); seed 40 dp, label 14 sp. Details the user
asked for: "Pip" centred against the 20 dp switch track, i.e. 2.2 dp (`widget_wordmark_drop`) below the switch's top edge
(measured on the phone; the user first had them top-aligned, then asked for "a few pixels" lower; always done with
padding, NOT a negative margin, which clipped the letters; `widget_hey_top` 4.6 dp is the switch's top padding), and "Listening…" shifted right by `widget_ellipsis_shift` so the WORD,
not the box with its trailing dots, is centred under the stop square (the square itself was centred to the pixel). Missed cue (E4 330 Hz tick)
was too quiet on a phone speaker at low ring volume: replaced by the soft sigh (see sounds above). Not built (out of scope): categories + dots, filter chips,
reminders, Plant / Let go swipes, evening nudge, "Pip caught that" toast, onboarding. Logo: the user rejected the
first generated seed, a Flaticon icon (licence forbids logo use) and four drawn marks; chose the Noun Project
"Sunflower Seed" (CC BY 3.0, attribution in README), filled paper with deep-green lines (option 1).

## Architecture
```
one.mindly.pip
├── PipApp.kt              Application; builds AppGraph (manual DI: settings, DB, processor, repository, engine status);
│                          starts leftover transcriptions; keeps the widget label in step with the setting
├── MainActivity.kt        permissions; shows Settings, the full-screen editor, or the Pocket (+ NoteSheet);
│                          the CaptureOverlay on top while the engine is Capturing
├── core/                  NO Android UI imports
│   ├── audio/             Microphone (AudioRecord 16 kHz mono PCM16), WavFile (write + read), PreRollBuffer, ThoughtPlayer (play/pause/seek)
│   ├── wakeword/          WakeWordDetector (our own "Hey Pip" model on onnxruntime), WakeWordTuning (sensitivity → threshold)
│   ├── vad/               SpeechDetector (sherpa-onnx silero VAD)
│   ├── capture/           ListeningEngine (the loop + requestCatch / requestFinish, publishes the voice level),
│   │                      CaptureSession (pure, unit-tested rules), EngineStatus
│   ├── feedback/          CuePlayer (SoundPool on notification volume + haptics)
│   ├── storage/           Room: Thought entity, ThoughtDao, PipDatabase, Converters, ThoughtRepository
│   ├── processing/        ThoughtProcessor seam → WorkManagerProcessor; TranscriptionWorker
│   ├── transcription/     Transcriber (sherpa-onnx offline ASR, cut at pauses), SpeechModel (which model), TranscriptCleaner
│   └── settings/          PipSettings (SharedPreferences mirrored into a StateFlow)
├── service/               Android glue: ListeningService (foreground, type=microphone), BootReceiver
├── quick/                 CatchTileService ("Pip" tile in quick settings), PipWidget (home screen: Catch, Hey Pip on/off, Open), QuickActionActivity (invisible trampoline)
└── ui/                    StatusText, components/ (Seed, Wordmark), home/ (Pocket: HomeScreen + HomeViewModel),
                           note/ (NoteSheet, EditScreen, AudioBar), capture/ (CaptureOverlay), settings/, theme/
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
5. Tile / widget "Catch": opens `QuickActionActivity` (invisible, `showWhenLocked`, lets taps through), which
   calls `ListeningService.catchNow()` and closes once the engine is capturing (max 3 s). Why the activity:
   Android only lets a foreground service use the mic if it was *started* while the app was in the foreground.
   The engine records with no wake word and no pre-roll, whether "Hey Pip" is on, off or paused; a request
   during a capture is ignored. With "Hey Pip" off the engine's `run()` returns after the catch and the
   service stops itself (`stopSelfResult`, so a newer request isn't lost). The widget's "Hey Pip: on/off"
   goes through the same activity (turning it on starts the mic service).
6. `BootReceiver` restarts listening after reboot on Android ≤ 13. On Android 14+ starting a
   microphone foreground service from BOOT_COMPLETED is forbidden, so it posts a "tap to resume"
   notification; opening the app restarts the service (`MainActivity.onResume`).

### Data model (Room, `pip.db`, schema exported to `app/schemas/`)
`Thought(id: UUID, createdAt: Instant, audioPath (relative to filesDir), durationMs, transcript?,
category: IDEA|TASK|REMINDER|SHOPPING|THOUGHT (default THOUGHT), categorySource: RULE|USER,
remindAt?, status: CAUGHT|TRANSCRIBING|READY|FAILED, state: POCKET|PLANTED|LET_GO)`.
`category`, `categorySource` and `remindAt` are unused since the scope cut (kept: removing columns needs a
migration, and unused columns cost nothing). Schema changes need a version bump + Migration. Never use
destructive migration.

### Transcription
`saveCaught()` → `WorkManagerProcessor.enqueue()` → one unique WorkManager job ("transcribe",
APPEND_OR_REPLACE) that works through every CAUGHT thought, oldest first: status TRANSCRIBING → read WAV →
`Transcriber` (silero VAD cuts the note at pauses into ≤ 20 s pieces, each padded 0.25 s, decoded separately)
→ `TranscriptCleaner` (drops a leading "Hey Pip"/"Pip", non-speech tags like [BLANK_AUDIO], capitalises) →
READY with the text, or FAILED (note screen has "Try again"). On start the job puts TRANSCRIBING rows back to
CAUGHT (a crash mid-job); `PipApp` also starts the job on every launch, so old Phase-1 notes get transcribed.
One job at a time = one copy of the model in memory. Threads: min(cores, 4).

### Edit and delete (note sheet)
Edit (pencil) is offered only when the status is READY or FAILED, so the job can't overwrite the user's words;
it opens the full-screen EditScreen; Save sets the text and status READY (`editTranscript`, trimmed); ✕/back
discards and returns to the sheet.
Delete (bin, with a confirm dialog) is a real delete: Room row first (leaves the list at once), then the WAV.
`state = LET_GO` is not used for this. Bulk delete: long-press a row in the list to start selecting (checkboxes,
"N selected", Select all, bin with confirm; back/✕ stops) → `deleteAll`. `NoteEditingTest` (androidTest) covers
edit, delete and bulk delete on the real database.

## Models (in `app/src/main/assets`: ~4 MB in git + the ~160 MB speech model, fetched)
| File | Size | What |
|---|---|---|
| `wake/melspectrogram.onnx` | 1.09 MB | frozen front-end step 1 (openWakeWord v0.5.1, Apache-2.0) |
| `wake/embedding_model.onnx` | 1.33 MB | frozen front-end step 2: Google speech embedding (openWakeWord v0.5.1) |
| `wake/hey_pip.onnx` | 0.86 MB | OUR classifier, trained in `training/` on the user's voice (`training/export_to_app.py` copies it here) |
| `vad/silero_vad.onnx` | 0.64 MB | voice activity detection via sherpa-onnx (`asr-models` release) |
| `asr/base.en-*.int8.onnx` + tokens | 156 MB | Whisper base.en (MIT) via sherpa-onnx. NOT in git: `tools/fetch_speech_model.sh` |

Speech model choice (`training/compare_asr.py`, 36 synthetic notes: 12 sentences × 7 Mac voices incl. Indian
English, each clean / noisy 12 dB / echo + noise; word error rate, speed on the M3 Mac):
| Model | Size | WER all (clean/noisy/echo) | Speed |
|---|---|---|---|
| Whisper base.en | 160 MB | 15.6% (8.1/11.9/26.7) | 0.05× real time |
| Moonshine base | 286 MB | 14.8% (5.9/10.4/28.1) | 0.017× |
| Whisper tiny.en | 103 MB | 19.0% (9.6/10.4/37.0) | 0.03× |
| Moonshine tiny | 124 MB | 25.2% (8.9/14.1/52.6) | 0.008× |
Most "clean" errors are spelling ("10"/"ten", "favorite"/"favourite", "Murray"/"Murree"). Picked Whisper base.en
(close to best, nearly half the size). OPEN QUESTION: speed on the A32. Whisper always encodes a 30 s window, so
short notes cost the same as long ones; the 1-core emulator took ~25 s to load + ~14 s per 4 s note. If the A32
is too slow, switch to Moonshine base (time scales with note length, 3× faster on the Mac): only
`SpeechModel.kt` + `tools/fetch_speech_model.sh` change. `TranscriptionTest` logs load and decode times.

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
- Later hands-on use (user, 2026-11-21): caught "Hey Pip" ~90-95% of the time. The user won't keep it on all
  day, which is why the tile and widget triggers were added.

## Build / run
```
tools/fetch_speech_model.sh     # once: downloads the speech model into assets (not in git)
./gradlew installDebug          # build + install on the connected device
./gradlew testDebugUnitTest     # JVM unit tests (CaptureSession, WavFile, WakeWordTuning, TranscriptCleaner)
ANDROID_SERIAL=<device> ./gradlew connectedDebugAndroidTest
                                # on-device: WakeWordDetectorTest, TranscriptionTest (incl. save → job → text),
                                # QuickActionTest (widget toggle, catch with Hey Pip off). Restores settings, deletes its note.
adb logcat -s Pip               # Pip's own log lines ([Engine], [Service], [Storage], …)
adb logcat -s Pip sherpa-onnx   # + native VAD logs
adb logcat --pid=$(adb shell pidof one.mindly.pip)   # everything from the app process
```
If the shell can't find Java: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`;
adb lives in `~/Library/Android/sdk/platform-tools`.

## Plan
Order agreed 2026-11-22: edit + delete notes (done) → design pass (done, incl. logo) →
release build for daily use.

1. DONE: "Hey Pip" + capture + storage (custom wake word, works on the A32).
2. DONE and confirmed on the A32 (2026-11-22; tile works from the lock screen, transcripts good, ~3 s per
   note + 5.7 s model load). Also edit + delete notes (emulator-tested). Was: "Pip" quick-settings tile, widget (Catch, Hey Pip
   on/off, Open), offline transcription, note screen (audio player on top, transcript below), notes list shows text.
   NEXT on the phone: `tools/fetch_speech_model.sh`, install, run `connectedDebugAndroidTest` (transcription
   speed!), add the tile (swipe down → ⋮ → Edit buttons) and widget, try the tile on the LOCK SCREEN, catch a few
   real English notes and check the transcripts (`training/compare_asr.py` on notes pulled from the phone).
3. Later, if wanted: onboarding (incl. Samsung "Unrestricted" battery setting), battery tuning (energy gate for
   the wake word: ~30 ms CPU per 80 ms chunk on the A32; user doesn't plan long "Hey Pip" sessions, so not
   needed now), search, export, delete audio after N days.

Dropped (2026-11-21): categories, tasks, reminders, a second "Hey Pip" tile, a timed "listen for 30 min" mode.
Later and separate: design pass (theme layer only), connectors.

## Known limitations / notes
- Pre-roll (0.5 s) means a "Hey Pip" recording may start with the tail of "…Pip"; `TranscriptCleaner`
  strips a leading "Hey Pip"/"Pip". Tile/widget catches have no pre-roll.
- The tile and widget need the mic permission already granted; otherwise they open the app.
- Capture buffers the note in memory (≤ 2 min ≈ 3.8 MB) and writes it at the end; a crash
  *during* the note loses that note.
- Samsung may kill background apps unless Pip's battery setting is "Unrestricted" (Phase 4 onboarding).
- A native crash (ONNX Runtime / sherpa) can't be caught in Kotlin; START_STICKY makes Android restart the service.
- When "Hey Pip" is off, the service stops after a catch; it waits `CUE_TAIL_MS` (800 ms) first, because releasing
  the SoundPool in onDestroy cut off the caught/missed cue (the "no missed sound" bug, 2026-11-22).
