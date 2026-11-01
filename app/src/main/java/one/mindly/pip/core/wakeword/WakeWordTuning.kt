package one.mindly.pip.core.wakeword

/**
 * Maps the 0..1 "sensitivity" slider to the wake-word model's probability threshold.
 * Higher threshold = it must be more sure = fewer false wakes, but more misses.
 *
 * sensitivity 0.00 → threshold 0.97  (strictest)
 * sensitivity 0.15 → threshold 0.90  (default; what training/train.py recommended)
 * sensitivity 1.00 → threshold 0.50  (loosest)
 *
 * Measured on held-out data (training/out/report.txt): the model caught 10/11 of the user's clips at
 * every threshold from 0.3 to 0.95, while false wakes on unrelated speech fell from 1.6/hour (0.3)
 * to 0.23/hour (0.9) and 0 (0.95). So a strict default costs almost nothing.
 */
object WakeWordTuning {
    const val DEFAULT_SENSITIVITY = 0.15f

    fun thresholdFor(sensitivity: Float): Float = 0.97f - 0.47f * sensitivity.coerceIn(0f, 1f)
}
