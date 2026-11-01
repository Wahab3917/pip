package one.mindly.pip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Wraps every screen. It only assembles the theme-layer files; it holds no values itself. */
@Composable
fun PipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PipColors.dark else PipColors.light,
        typography = PipTypography,
        shapes = PipShapes,
        content = content,
    )
}
