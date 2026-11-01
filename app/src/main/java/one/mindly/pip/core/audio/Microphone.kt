package one.mindly.pip.core.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.Closeable

/**
 * Thin wrapper around AudioRecord: 16 kHz, mono, 16-bit PCM, which is exactly what
 * the wake-word and VAD models expect, so no resampling is needed.
 */
class Microphone : Closeable {
    private var record: AudioRecord? = null

    /** Opens and starts the mic. Returns false if the OS refused (e.g. another app holds it). */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun open(): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false
        val newRecord = AudioRecord(
            // VOICE_RECOGNITION = raw-ish audio tuned for speech recognisers (no auto gain tricks).
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL, ENCODING,
            // A generous 1 s buffer, so short pauses on our side (saving a file,
            // reloading the wake-word model) never drop audio.
            maxOf(minBuffer, SAMPLE_RATE * BYTES_PER_SAMPLE),
        )
        if (newRecord.state != AudioRecord.STATE_INITIALIZED) {
            newRecord.release()
            return false
        }
        newRecord.startRecording()
        if (newRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            newRecord.release()
            return false
        }
        record = newRecord
        return true
    }

    /** Blocks until [buffer] is full (~100 ms for a CHUNK). Returns samples read, or < 0 on error. */
    fun read(buffer: ShortArray): Int = record?.read(buffer, 0, buffer.size) ?: -1

    /**
     * True when Android is feeding us silence because a higher-priority app (a call,
     * a voice recorder, an assistant) took the mic. We keep the mic open and resume
     * automatically when this turns false.
     */
    val isSilencedBySystem: Boolean
        get() = record?.activeRecordingConfiguration?.isClientSilenced == true

    override fun close() {
        record?.run {
            runCatching { stop() }
            release()
        }
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        /** 100 ms of audio per read: small enough for snappy detection, big enough to be cheap. */
        const val CHUNK_SAMPLES = SAMPLE_RATE / 10
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2
    }
}

/** The models want floats in [-1, 1]; the mic gives 16-bit integers. */
fun ShortArray.toFloatSamples(): FloatArray = FloatArray(size) { this[it] / 32768f }
