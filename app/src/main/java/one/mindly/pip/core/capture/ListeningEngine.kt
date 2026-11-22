package one.mindly.pip.core.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.Microphone
import one.mindly.pip.core.audio.PreRollBuffer
import one.mindly.pip.core.audio.toFloatSamples
import one.mindly.pip.core.feedback.Cue
import one.mindly.pip.core.feedback.CuePlayer
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.vad.SpeechDetector
import one.mindly.pip.core.wakeword.WakeWordDetector
import one.mindly.pip.core.wakeword.WakeWordTuning
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The capture path: trigger → record until silence → save. Deliberately dumb:
 * it never transcribes or thinks, it only makes sure the audio lands on disk.
 *
 * Two triggers start a capture:
 * - "Hey Pip", while the user has it switched on. One audio stream feeds everything: while
 *   idle, each 100 ms chunk goes to the wake-word detector; after a wake word, the same stream
 *   goes to the VAD and into the recording.
 * - [requestCatch], from the quick-settings tile or the widget. It works whether "Hey Pip" is
 *   on, off or paused (the user asked explicitly, so a pause doesn't apply).
 *
 * [run] returns when there is nothing left to do ("Hey Pip" is off and no catch is pending),
 * or is cancelled when the service stops. While "Hey Pip" is on it releases the mic whenever
 * listening should pause (call, user pause, battery saver) and reopens it after.
 */
class ListeningEngine(
    private val context: Context,
    private val settings: PipSettings,
    private val cues: CuePlayer,
    /** Where engine status is published (read by the notification and the home screen). */
    private val status: MutableStateFlow<EngineStatus>,
    /** Voice level 0..1 while capturing (0 otherwise), for the capture overlay's breathing seed. */
    private val level: MutableStateFlow<Float>,
    /** Persists a finished recording. Throwing means "not saved". */
    private val saveThought: suspend (pcm: ShortArray) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** Loaded the first time "Hey Pip" listening starts, so a one-off catch doesn't pay for it. */
    private var wakeWord: WakeWordDetector? = null
    private lateinit var speech: SpeechDetector

    /** Set by [requestCatch], cleared when the capture starts. */
    private val catchRequested = MutableStateFlow(false)

    /** Set by [requestFinish] ("Tap to finish"), checked once per chunk while capturing. */
    @Volatile private var finishRequested = false

    /** End the current capture now, keeping what was said (as if the 2 s of silence had passed). */
    fun requestFinish() {
        finishRequested = true
    }

    /**
     * Start a capture now, without the wake word. Safe to call from any thread.
     * Ignored while a capture is already running: that one is already catching the thought.
     */
    fun requestCatch() {
        if (status.value != EngineStatus.Capturing) catchRequested.value = true
    }

    /** Mic level logs + rolling audio dump, only in debuggable (development) builds. */
    private val diagnostics =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            ListeningDiagnostics(File(context.filesDir, "debug"), Microphone.SAMPLE_RATE)
        } else {
            null
        }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun run() = withContext(Dispatchers.IO) {
        status.value = EngineStatus.Starting
        speech = SpeechDetector(context.assets)
        try {
            while (isActive) {
                if (catchRequested.value) {
                    catchOnce()
                    continue
                }
                if (!settings.values.value.listeningEnabled) break // Only here for a one-off catch.

                val pause = pauseCondition()
                if (pause != null) {
                    if (status.value != pause) Log.i(PIP_LOG_TAG, "[Engine] paused: $pause")
                    status.value = pause
                    // Re-check every 2 s, or at once if a setting changes (e.g. "Resume") or a catch is requested.
                    val current = settings.values.value
                    withTimeoutOrNull(PAUSE_RECHECK_MS) {
                        merge(settings.values.filter { it != current }, catchRequested.filter { it }).first()
                    }
                    continue
                }
                listenUntilPaused(wakeWord ?: loadWakeWord(settings.values.value.sensitivity))
            }
        } finally {
            wakeWord?.close()
            speech.close()
            status.value = EngineStatus.Off
        }
    }

    /** A capture asked for by the tile or widget while the mic isn't already open. */
    @SuppressLint("MissingPermission") // run() requires RECORD_AUDIO, and only run() calls this.
    private suspend fun catchOnce() {
        Log.i(PIP_LOG_TAG, "[Engine] catch requested")
        val mic = Microphone()
        if (!mic.open()) {
            catchRequested.value = false
            Log.w(PIP_LOG_TAG, "[Engine] mic unavailable, can't catch")
            cues.play(Cue.MISSED)
            return
        }
        Log.i(PIP_LOG_TAG, "[Engine] mic open for the catch")
        try {
            capture(mic, preRoll = ShortArray(0))
        } finally {
            mic.close()
        }
    }

    /** Opens the mic and listens for the wake word until we should pause, or the mic fails. */
    @SuppressLint("MissingPermission") // run() requires RECORD_AUDIO, and only run() calls this.
    private suspend fun listenUntilPaused(wakeWord: WakeWordDetector) {
        val mic = Microphone()
        if (!mic.open()) {
            Log.w(PIP_LOG_TAG, "[Engine] mic unavailable, retrying in ${MIC_RETRY_MS / 1000} s")
            status.value = EngineStatus.MicUnavailable
            delay(MIC_RETRY_MS)
            return
        }
        Log.i(PIP_LOG_TAG, "[Engine] mic open, listening for Hey Pip")
        val preRoll = PreRollBuffer(PRE_ROLL_CHUNKS)
        val buffer = ShortArray(Microphone.CHUNK_SAMPLES)
        var chunkCount = 0L
        var zeroChunks = 0
        try {
            while (currentCoroutineContext().isActive) {
                val read = mic.read(buffer)
                if (read <= 0) {
                    Log.w(PIP_LOG_TAG, "[Engine] mic read error $read, reopening")
                    return
                }
                val pcm = buffer.copyOf(read)
                diagnostics?.onChunk(pcm)

                // The tile or widget asked for a catch: no wake word needed, and the mic is already open.
                if (catchRequested.value) {
                    Log.i(PIP_LOG_TAG, "[Engine] catch requested")
                    capture(mic, preRoll = ShortArray(0))
                    wakeWord.reset()
                    continue
                }

                // About once a second: time to pause, or to stop? Did the sensitivity slider move?
                if (++chunkCount % CHECK_EVERY_CHUNKS == 0L) {
                    if (pauseCondition() != null || !settings.values.value.listeningEnabled) return
                    wakeWord.threshold = WakeWordTuning.thresholdFor(settings.values.value.sensitivity)
                }

                // When another app takes the mic, Android feeds us exact digital zeros
                // (a real mic always has a little noise). Wait it out without releasing the mic.
                zeroChunks = if (pcm.all { it.toInt() == 0 }) zeroChunks + 1 else 0
                if (mic.isSilencedBySystem || zeroChunks >= MIC_BUSY_AFTER_CHUNKS) {
                    if (status.value !is EngineStatus.Paused) {
                        Log.i(PIP_LOG_TAG, "[Engine] mic silenced by the system (another app?)")
                    }
                    status.value = EngineStatus.Paused(PauseReason.MIC_BUSY)
                    preRoll.clear()
                    continue
                }
                status.value = EngineStatus.Listening

                // While recording training data you'll say "Hey Pip" a lot; don't react to it.
                if (diagnostics?.isRecordingSession == true) continue

                preRoll.add(pcm)
                val heard = wakeWord.accept(pcm)
                diagnostics?.onWakeWordScore(wakeWord.lastScore, wakeWord.threshold, wakeWord.averageChunkMs)
                if (heard) {
                    Log.i(PIP_LOG_TAG, "[Engine] Hey Pip heard")
                    capture(mic, preRoll.drain())
                    wakeWord.reset() // Don't let audio from before/while capturing re-trigger.
                    status.value = EngineStatus.Listening
                }
            }
        } finally {
            mic.close()
            Log.i(PIP_LOG_TAG, "[Engine] mic released")
        }
    }

    /** Records from the open [mic] until CaptureSession decides it's caught or missed. */
    private suspend fun capture(mic: Microphone, preRoll: ShortArray) {
        catchRequested.value = false
        finishRequested = false
        status.value = EngineStatus.Capturing
        cues.play(Cue.LISTENING)
        speech.reset()

        val session = CaptureSession(preRoll = preRoll)
        val buffer = ShortArray(Microphone.CHUNK_SAMPLES)
        var outcome: CaptureSession.Outcome = CaptureSession.Outcome.Continue
        try {
            while (outcome == CaptureSession.Outcome.Continue && currentCoroutineContext().isActive) {
                val read = mic.read(buffer)
                if (read <= 0) break
                val pcm = buffer.copyOf(read)
                level.value = voiceLevel(pcm)
                outcome = session.onChunk(pcm, speech.isSpeech(pcm.toFloatSamples()))
                if (finishRequested && outcome == CaptureSession.Outcome.Continue) break
            }
        } finally {
            level.value = 0f
            // Interrupted (mic error or service stopping)? Keep whatever speech we got.
            if (outcome == CaptureSession.Outcome.Continue) outcome = session.finishEarly()
            // NonCancellable: even if the service is stopping, finish saving first.
            withContext(NonCancellable) { finish(outcome) }
        }
    }

    private suspend fun finish(outcome: CaptureSession.Outcome) {
        when (outcome) {
            is CaptureSession.Outcome.Caught -> {
                val saved = runCatching { saveThought(outcome.pcm) }
                    .onFailure { Log.e(PIP_LOG_TAG, "[Engine] SAVE FAILED", it) }
                    .isSuccess
                // The "caught" cue plays only after the file and row are written, so it's a
                // promise that the thought is safe. A failed save gets the "missed" cue.
                cues.play(if (saved) Cue.CAUGHT else Cue.MISSED)
            }
            CaptureSession.Outcome.Missed -> {
                Log.i(PIP_LOG_TAG, "[Engine] no speech heard, nothing saved")
                cues.play(Cue.MISSED)
            }
            CaptureSession.Outcome.Continue -> Unit
        }
    }

    /**
     * How loud this chunk is, 0..1, for the overlay only: RMS level mapped from -55 dBFS (quiet
     * room) to -15 dBFS (speaking close to the phone).
     */
    private fun voiceLevel(pcm: ShortArray): Float {
        if (pcm.isEmpty()) return 0f
        var sum = 0.0
        for (s in pcm) sum += s.toDouble() * s
        val rms = sqrt(sum / pcm.size) / 32768.0
        val db = if (rms > 0) 20 * log10(rms) else -100.0
        return ((db + 55) / 40).toFloat().coerceIn(0f, 1f)
    }

    /** Returns why we should not be listening right now, or null if we should. */
    private fun pauseCondition(): EngineStatus.Paused? {
        val values = settings.values.value
        return when {
            values.pausedUntilMs > System.currentTimeMillis() ->
                EngineStatus.Paused(PauseReason.USER, values.pausedUntilMs)
            audioManager.mode in CALL_MODES -> EngineStatus.Paused(PauseReason.CALL)
            values.pauseInBatterySaver && powerManager.isPowerSaveMode ->
                EngineStatus.Paused(PauseReason.BATTERY_SAVER)
            else -> null
        }
    }

    private fun loadWakeWord(sensitivity: Float): WakeWordDetector {
        val started = SystemClock.elapsedRealtime()
        val detector = WakeWordDetector(context.assets, WakeWordTuning.thresholdFor(sensitivity))
        Log.i(
            PIP_LOG_TAG,
            "[Engine] wake word model loaded in ${SystemClock.elapsedRealtime() - started} ms, " +
                "threshold ${detector.threshold}",
        )
        wakeWord = detector
        return detector
    }

    private companion object {
        /** 0.5 s of audio from just before the wake word fired (see PreRollBuffer). */
        const val PRE_ROLL_CHUNKS = 5
        const val CHECK_EVERY_CHUNKS = 10L // 10 × 100 ms = 1 s
        const val MIC_BUSY_AFTER_CHUNKS = 20 // 2 s of perfect silence
        const val MIC_RETRY_MS = 3_000L
        const val PAUSE_RECHECK_MS = 2_000L

        /** Ringing, on a phone call, or on a VoIP call (WhatsApp, Meet…). */
        val CALL_MODES = setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
        )
    }
}
