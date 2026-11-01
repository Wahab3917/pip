package one.mindly.pip.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner shapes. Placeholder: Material 3 defaults. */
val PipShapes = Shapes()

/**
 * Layout geometry (spacing, sizes). Kept next to shapes so that "how things are laid out"
 * is also controlled from the theme layer rather than sprinkled through screens.
 */
object PipSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
}
