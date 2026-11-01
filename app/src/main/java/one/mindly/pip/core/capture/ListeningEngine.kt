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
import kotlinx.coroutines.flow.first
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

/**
 * The capture path: mic → "Hey Pip" → record until silence → save. Deliberately dumb:
 * it never transcribes or thinks, it only makes sure the audio lands on disk.
 *
 * One audio stream feeds everything. While idle, each 100 ms chunk goes to the wake-word
 * detector. After a wake word, the same stream goes to the VAD and into the recording.
 *
 * [run] loops until its coroutine is cancelled (the service stops), releasing the mic
 * whenever listening should pause (call, user pause, battery saver) and reopening it after.
 */
class ListeningEngine(
    private val context: Context,
    private val settings: PipSettings,
    private val cues: CuePlayer,
    /** Where engine status is published (read by the notification and the home screen). */
    private val status: MutableStateFlow<EngineStatus>,
    /** Persists a finished recording. Throwing means "not saved". */
    private val saveThought: suspend (pcm: ShortArray) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private lateinit var wakeWord: WakeWordDetector
    private lateinit var speech: SpeechDetector

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
        loadWakeWord(settings.values.value.sensitivity)
        try {
            while (isActive) {
                val pause = pauseCondition()
                if (pause != null) {
                    if (status.value != pause) Log.i(PIP_LOG_TAG, "[Engine] paused: $pause")
                    status.value = pause
                    // Re-check every 2 s, or immediately if a setting changes (e.g. "Resume").
                    val current = settings.values.value
                    withTimeoutOrNull(PAUSE_RECHECK_MS) { settings.values.first { it != current } }
                    continue
                }
                listenUntilPaused()
            }
        } finally {
            wakeWord.close()
            speech.close()
            status.value = EngineStatus.Off
        }
    }

    /** Opens the mic and listens for the wake word until we should pause, or the mic fails. */
    @SuppressLint("MissingPermission") // run() requires RECORD_AUDIO, and only run() calls this.
    private suspend fun listenUntilPaused() {
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

                // About once a second: time to pause? Did the sensitivity slider move?
                if (++chunkCount % CHECK_EVERY_CHUNKS == 0L) {
                    if (pauseCondition() != null) return
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
                outcome = session.onChunk(pcm, speech.isSpeech(pcm.toFloatSamples()))
            }
        } finally {
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
                Log.i(PIP_LOG_TAG, "[Engine] no speech after wake word, nothing saved")
                cues.play(Cue.MISSED)
            }
            CaptureSession.Outcome.Continue -> Unit
        }
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

    private fun loadWakeWord(sensitivity: Float) {
        val started = SystemClock.elapsedRealtime()
        wakeWord = WakeWordDetector(context.assets, WakeWordTuning.thresholdFor(sensitivity))
        Log.i(
            PIP_LOG_TAG,
            "[Engine] wake word model loaded in ${SystemClock.elapsedRealtime() - started} ms, " +
                "threshold ${wakeWord.threshold}",
        )
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
