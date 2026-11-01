package one.mindly.pip.core.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordTuningTest {
    @Test fun `default is the threshold training recommended`() {
        assertEquals(0.9f, WakeWordTuning.thresholdFor(WakeWordTuning.DEFAULT_SENSITIVITY), 0.005f)
    }

    @Test fun `more sensitive means a lower threshold`() {
        assertTrue(WakeWordTuning.thresholdFor(1f) < WakeWordTuning.thresholdFor(0f))
        assertEquals(0.97f, WakeWordTuning.thresholdFor(0f), 1e-6f)
        assertEquals(0.5f, WakeWordTuning.thresholdFor(1f), 1e-6f)
    }

    @Test fun `out of range input is clamped`() {
        assertEquals(WakeWordTuning.thresholdFor(1f), WakeWordTuning.thresholdFor(7f))
    }
}
