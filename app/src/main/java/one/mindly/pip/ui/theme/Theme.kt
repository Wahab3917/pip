package one.mindly.pip.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Wraps every screen. It only assembles the theme-layer files; it holds no values itself.
 *
 * Switching light/dark crossfades every colour over [PipMotion.THEME_MS] instead of jumping.
 * (MainActivity also handles the uiMode change itself, so the screen isn't rebuilt.)
 */
@Composable
fun PipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val spec = tween<Color>(PipMotion.THEME_MS)
    val seed = if (darkTheme) PipColors.seedDark else PipColors.seedLight
    CompositionLocalProvider(
        LocalSeedColors provides SeedColors(fill = animate(seed.fill, spec), line = animate(seed.line, spec)),
    ) {
        MaterialTheme(
            colorScheme = animated(if (darkTheme) PipColors.dark else PipColors.light, spec),
            typography = PipTypography,
            shapes = PipShapes,
            content = content,
        )
    }
}

@Composable
private fun animate(target: Color, spec: AnimationSpec<Color>): Color = animateColorAsState(target, spec, label = "theme").value

/** [target] with every colour Pip uses animated towards it. */
@Composable
private fun animated(target: ColorScheme, spec: AnimationSpec<Color>): ColorScheme = target.copy(
    primary = animate(target.primary, spec),
    onPrimary = animate(target.onPrimary, spec),
    primaryContainer = animate(target.primaryContainer, spec),
    onPrimaryContainer = animate(target.onPrimaryContainer, spec),
    secondary = animate(target.secondary, spec),
    onSecondary = animate(target.onSecondary, spec),
    secondaryContainer = animate(target.secondaryContainer, spec),
    onSecondaryContainer = animate(target.onSecondaryContainer, spec),
    tertiary = animate(target.tertiary, spec),
    onTertiary = animate(target.onTertiary, spec),
    background = animate(target.background, spec),
    onBackground = animate(target.onBackground, spec),
    surface = animate(target.surface, spec),
    onSurface = animate(target.onSurface, spec),
    surfaceVariant = animate(target.surfaceVariant, spec),
    onSurfaceVariant = animate(target.onSurfaceVariant, spec),
    surfaceContainerLowest = animate(target.surfaceContainerLowest, spec),
    surfaceContainerLow = animate(target.surfaceContainerLow, spec),
    surfaceContainer = animate(target.surfaceContainer, spec),
    surfaceContainerHigh = animate(target.surfaceContainerHigh, spec),
    surfaceContainerHighest = animate(target.surfaceContainerHighest, spec),
    outline = animate(target.outline, spec),
    outlineVariant = animate(target.outlineVariant, spec),
    error = animate(target.error, spec),
    onError = animate(target.onError, spec),
    inverseSurface = animate(target.inverseSurface, spec),
    inverseOnSurface = animate(target.inverseOnSurface, spec),
)
