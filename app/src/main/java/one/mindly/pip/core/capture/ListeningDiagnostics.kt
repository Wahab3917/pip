package one.mindly.pip.core.capture

import android.util.Log
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.WavFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * DEBUG BUILDS ONLY: helps answer "why didn't it hear me?".
 * - Every 2 s, logs the mic level in dBFS (0 = loudest possible; speech at arm's length
 *   is usually around -40..-20 peak; below about -50 the mic is basically hearing nothing).
 * - Every 10 s, overwrites filesDir/debug/last10s.wav with the last 10 s the engine heard,
 *   so it can be pulled with adb and replayed through the model on a computer.
 * - Logs every wake-word score of at least 0.3 (the near-misses you need to tune the threshold),
 *   and every ~30 s how much CPU the wake word costs (the budget is 80 ms per 80 ms chunk).
 * - Recording sessions for wake-word training (training/record_phone.py): while the file
 *   debug/record.flag exists, every chunk is appended to debug/session.pcm (raw 16-bit
 *   little-endian, 16 kHz mono) and the engine ignores the wake word.
 */
class ListeningDiagnostics(private val debugDir: File, private val sampleRate: Int) {
    private val recordFlag = File(debugDir, "record.flag")
    private var session: FileOutputStream? = null

    /** True while a training recording session is running. */
    var isRecordingSession = false
        private set

    private val ring = ShortArray(sampleRate * 10)
    private var ringPos = 0
    private var ringFilled = false
    private var chunks = 0
    private var peak = 0
    private var sumSquares = 0.0
    private var samples = 0

    fun onChunk(pcm: ShortArray) {
        for (s in pcm) {
            ring[ringPos] = s
            ringPos = (ringPos + 1) % ring.size
            if (ringPos == 0) ringFilled = true
            val v = s.toInt()
            peak = max(peak, if (v < 0) -v else v)
            sumSquares += v.toDouble() * v
        }
        samples += pcm.size
        chunks++

        if (chunks % FLAG_CHECK_EVERY_CHUNKS == 0) updateSession()
        session?.write(toLittleEndianBytes(pcm))

        if (chunks % LOG_EVERY_CHUNKS == 0) {
            val rms = sqrt(sumSquares / samples)
            Log.d(PIP_LOG_TAG, "[Diag] mic level: peak ${dbfs(peak.toDouble())} dBFS, rms ${dbfs(rms)} dBFS")
            peak = 0; sumSquares = 0.0; samples = 0
        }
        if (chunks % DUMP_EVERY_CHUNKS == 0 && ringFilled) dump()
    }

    /** Called after every wake-word chunk; logs near-misses and, now and then, the CPU cost. */
    fun onWakeWordScore(score: Float, threshold: Float, averageChunkMs: Float) {
        if (score >= NEAR_MISS_LOG_SCORE) {
            Log.d(PIP_LOG_TAG, "[Engine] wake score %.2f (threshold %.2f)".format(score, threshold))
        }
        if (chunks % COST_LOG_EVERY_CHUNKS == 0) {
            Log.d(PIP_LOG_TAG, "[Engine] wake word costs %.1f ms per 80 ms chunk".format(averageChunkMs))
        }
    }

    /** Starts or stops the training recording when the flag file appears or disappears. */
    private fun updateSession() {
        val wanted = recordFlag.exists()
        if (wanted == isRecordingSession) return
        if (wanted) {
            // The script deletes session.pcm before creating the flag, so this starts fresh.
            session = FileOutputStream(File(debugDir, "session.pcm"), true)
            Log.i(PIP_LOG_TAG, "[Diag] training recording STARTED")
        } else {
            session?.close()
            session = null
            Log.i(PIP_LOG_TAG, "[Diag] training recording STOPPED")
        }
        isRecordingSession = wanted
    }

    private fun toLittleEndianBytes(pcm: ShortArray): ByteArray {
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm)
        return bytes.array()
    }

    private fun dump() {
        // Unroll the ring so the file is in time order (oldest first).
        val ordered = ShortArray(ring.size) { ring[(ringPos + it) % ring.size] }
        runCatching { WavFile.write(File(debugDir, "last10s.wav"), ordered, sampleRate) }
            .onFailure { Log.w(PIP_LOG_TAG, "[Diag] dump failed", it) }
    }

    private fun dbfs(value: Double): String =
        if (value <= 0.0) "-inf" else "%.1f".format(20 * log10(value / 32768.0))

    private companion object {
        const val LOG_EVERY_CHUNKS = 20 // 2 s
        const val DUMP_EVERY_CHUNKS = 100 // 10 s
        const val FLAG_CHECK_EVERY_CHUNKS = 5 // 0.5 s
        const val COST_LOG_EVERY_CHUNKS = 300 // 30 s
        const val NEAR_MISS_LOG_SCORE = 0.3f
    }
}
