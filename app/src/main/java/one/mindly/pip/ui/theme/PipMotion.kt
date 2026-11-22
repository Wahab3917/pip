package one.mindly.pip.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Animation timings. Placeholder values close to Material defaults; the design pass tunes these.
 * (Cue sounds live in res/raw/cue_*.wav; haptic patterns in core/feedback/CuePlayer.kt,
 * because the background service, which has no UI, plays them.)
 */
object PipMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 300

    /** How list rows slide when a new thought appears at the top. */
    val listItemPlacement: FiniteAnimationSpec<IntOffset> = tween(MEDIUM_MS)

    /** How often the note screen's progress bar moves while audio plays. */
    const val playerTickMs = 200L
}
