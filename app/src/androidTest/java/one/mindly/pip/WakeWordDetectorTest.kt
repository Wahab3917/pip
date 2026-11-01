package one.mindly.pip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import one.mindly.pip.core.wakeword.WakeWordDetector
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Checks the Kotlin wake-word pipeline gives the SAME scores as the Python training code.
 * The reference (androidTest/assets/wake_reference.*) is made by training/export_to_app.py.
 * Run on a device or emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class WakeWordDetectorTest {

    @Test
    fun scoresMatchPythonReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val reference = JSONObject(testAssets.open("wake_reference.json").bufferedReader().readText())
        val expected = reference.getJSONArray("scores")
        val firstScored = reference.getInt("first_scored_chunk")
        val threshold = reference.getDouble("threshold").toFloat()
        val audio = readWav(testAssets.open("wake_reference.wav").readBytes())

        val detector = WakeWordDetector(instrumentation.targetContext.assets, threshold)
        val probes = reference.getJSONObject("embeddings")
        var maxDiff = 0f
        var maxEmbeddingDiff = 0f
        var fired = false
        // One 1280-sample chunk per call, so lastScore is exactly the score after that chunk.
        for (chunk in 0 until audio.size / 1280) {
            fired = detector.accept(audio.copyOfRange(chunk * 1280, (chunk + 1) * 1280)) || fired
            if (probes.has("$chunk")) {
                val want = probes.getJSONArray("$chunk")
                val got = detector.latestEmbedding!!
                for (i in got.indices) maxEmbeddingDiff = maxOf(maxEmbeddingDiff, abs(got[i] - want.getDouble(i).toFloat()))
            }
            if (chunk >= firstScored) {
                val want = expected.getDouble(chunk - firstScored).toFloat()
                maxDiff = maxOf(maxDiff, abs(detector.lastScore - want))
            }
        }
        android.util.Log.i("PipTest", "wake word costs %.1f ms per 80 ms chunk".format(detector.averageChunkMs))
        detector.close()

        assertEquals("chunks scored", expected.length(), audio.size / 1280 - firstScored)
        android.util.Log.i("PipTest", "max embedding diff $maxEmbeddingDiff, max score diff $maxDiff")
        // Embeddings are ~±50 in size, so 0.01 is float rounding; anything more is a real difference.
        assertTrue("embeddings differ by up to $maxEmbeddingDiff", maxEmbeddingDiff < 0.01f)
        assertTrue("Kotlin and Python scores differ by up to $maxDiff", maxDiff < 1e-3f)
        assertTrue("the reference \"Hey Pip\" should wake the detector", fired)
    }

    /** 16-bit mono PCM .wav → samples (skips the standard 44-byte header). */
    private fun readWav(bytes: ByteArray): ShortArray {
        val buffer = ByteBuffer.wrap(bytes, 44, bytes.size - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }
}
