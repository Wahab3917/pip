package one.mindly.pip.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileTest {
    @Test fun `header describes 16 kHz mono 16-bit PCM`() {
        val pcm = shortArrayOf(1, -1, 32767, -32768)
        val bytes = WavFile.encode(pcm, 16_000)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(36 + 8, b.getInt(4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(1.toShort(), b.getShort(20)) // PCM
        assertEquals(1.toShort(), b.getShort(22)) // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(16.toShort(), b.getShort(34)) // bits per sample
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(8, b.getInt(40))
        assertEquals(-32768, b.getShort(44 + 6).toInt())
        assertEquals(44 + 8, bytes.size)
    }
}
