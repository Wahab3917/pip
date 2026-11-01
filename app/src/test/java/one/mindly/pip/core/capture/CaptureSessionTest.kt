package one.mindly.pip.core.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives CaptureSession with fake 100 ms chunks: S = speech, _ = silence. */
class CaptureSessionTest {
    private val chunk = ShortArray(1_600) // 100 ms at 16 kHz

    private fun run(pattern: String, preRoll: ShortArray = ShortArray(0)): Pair<CaptureSession.Outcome, Int> {
        val session = CaptureSession(CaptureConfig(), preRoll)
        pattern.forEachIndexed { i, c ->
            val outcome = session.onChunk(chunk, isSpeech = c == 'S')
            if (outcome != CaptureSession.Outcome.Continue) return outcome to i + 1
        }
        return CaptureSession.Outcome.Continue to pattern.length
    }

    @Test fun `no speech for 4 s is a miss`() {
        val (outcome, chunks) = run("_".repeat(60))
        assertEquals(CaptureSession.Outcome.Missed, outcome)
        assertEquals(40, chunks) // 40 × 100 ms = 4 s
    }

    @Test fun `speech then 2 s of silence is caught`() {
        val (outcome, chunks) = run("__SSSSS" + "_".repeat(30))
        assertTrue(outcome is CaptureSession.Outcome.Caught)
        assertEquals(7 + 20, chunks)
    }

    @Test fun `short pauses between words do not end the note`() {
        val (outcome, _) = run("SSS" + "_".repeat(15) + "SSS" + "_".repeat(15))
        assertEquals(CaptureSession.Outcome.Continue, outcome)
    }

    @Test fun `speech starting just before the timeout still counts`() {
        val (outcome, _) = run("_".repeat(39) + "S" + "_".repeat(25))
        assertTrue(outcome is CaptureSession.Outcome.Caught)
    }

    @Test fun `caught audio includes the pre-roll`() {
        val (outcome, chunks) = run("S" + "_".repeat(20), preRoll = ShortArray(8_000))
        assertEquals(8_000 + chunks * 1_600, (outcome as CaptureSession.Outcome.Caught).pcm.size)
    }

    @Test fun `continuous speech stops at the 2 minute cap`() {
        val (outcome, chunks) = run("S".repeat(1_300))
        assertTrue(outcome is CaptureSession.Outcome.Caught)
        assertEquals(1_200, chunks)
    }

    @Test fun `interrupted capture keeps speech but drops silence`() {
        val withSpeech = CaptureSession().apply { onChunk(chunk, true) }
        assertTrue(withSpeech.finishEarly() is CaptureSession.Outcome.Caught)
        val silentOnly = CaptureSession().apply { onChunk(chunk, false) }
        assertEquals(CaptureSession.Outcome.Missed, silentOnly.finishEarly())
    }
}
