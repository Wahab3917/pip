package one.mindly.pip.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner shapes. Source: "Pip — Design Guidelines" → cards have 16dp corners and a hairline
 * border instead of shadows.
 */
val PipShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp), // Bottom sheet top corners.
)

/**
 * Layout geometry (spacing, sizes) on the guidelines' 8dp grid. Kept next to shapes so that
 * "how things are laid out" is also controlled from the theme layer.
 */
object PipSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp

    /** Hairline border width on cards and pills. */
    val hairline = 1.dp

    /** The seed capture button at the bottom of the pocket. */
    val seedButton = 64.dp
    val seedIcon = 30.dp

    /** The capture overlay's breathing seed and live waveform. */
    val overlaySeed = 112.dp
    val overlayWave = 40.dp

    /** Room at the bottom of the list so the last card can scroll above the seed button. */
    val listBottomForSeed = 104.dp
}
