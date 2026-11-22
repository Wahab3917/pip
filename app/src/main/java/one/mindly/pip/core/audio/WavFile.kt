package one.mindly.pip.core.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes and reads 16-bit mono PCM as a standard .wav file (a 44-byte header followed by the samples). */
object WavFile {
    private const val HEADER_BYTES = 44

    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        file.parentFile?.mkdirs()
        val bytes = encode(pcm, sampleRate)
        // Write to a temp file then rename: a crash mid-write can never leave a half-written .wav
        // with a real name. (Rename within one directory is atomic.)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(file)) { "Could not rename $tmp to $file" }
    }

    /** A recording read back from disk. */
    class Audio(val pcm: ShortArray, val sampleRate: Int)

    /** Reads a file written by [write] (16-bit mono PCM). Other WAV layouts are rejected. */
    fun read(file: File): Audio = decode(file.readBytes())

    fun decode(bytes: ByteArray): Audio {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= HEADER_BYTES && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") {
            "Not a WAV file"
        }
        val channels = buffer.getShort(22).toInt()
        val sampleRate = buffer.getInt(24)
        val bits = buffer.getShort(34).toInt()
        require(channels == 1 && bits == 16) { "Expected 16-bit mono, got $channels channels, $bits bits" }
        val dataBytes = minOf(buffer.getInt(40), bytes.size - HEADER_BYTES)
        val pcm = ShortArray(dataBytes / 2)
        buffer.position(HEADER_BYTES)
        buffer.asShortBuffer().get(pcm)
        return Audio(pcm, sampleRate)
    }

    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val buffer = ByteBuffer.allocate(HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        // "fmt " chunk: PCM format (1), 1 channel, sample rate, byte rate, block align, 16 bits.
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataBytes)
        pcm.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
