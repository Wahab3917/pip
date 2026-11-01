package one.mindly.pip.core.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes 16-bit mono PCM as a standard .wav file (a 44-byte header followed by the samples). */
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
