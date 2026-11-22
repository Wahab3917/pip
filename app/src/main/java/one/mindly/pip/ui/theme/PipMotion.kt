package one.mindly.pip.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Animation timings. Source: "Pip — Design Guidelines" → Motion: everything is a simple fade
 * or slide of about 200 ms. Compose's animations follow Android's "remove animations" setting.
 * (Cue sounds live in res/raw/cue_*.wav; haptic patterns in core/feedback/CuePlayer.kt,
 * because the background service, which has no UI, plays them.)
 */
object PipMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 200

    /** How list rows slide when a new thought appears at the top. */
    val listItemPlacement: FiniteAnimationSpec<IntOffset> = tween(MEDIUM_MS)

    /** How often the player's progress bar moves while audio plays. */
    const val playerTickMs = 200L

    /** How much the overlay's seed grows at full voice (0.35 = 35 % bigger). */
    const val seedBreath = 0.35f

    /** One sweep of the "Writing it down…" shimmer. */
    const val shimmerMs = 1_600
}
