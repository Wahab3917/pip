package one.mindly.pip.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * All of Pip's colors live here. Screens read them through MaterialTheme.colorScheme,
 * never as hard-coded Color(...) values. (Dynamic "wallpaper" color is deliberately off.)
 *
 * Source: "Pip — Design Guidelines" → Color. Warm paper, ink-black text, one seedling green.
 * Each guideline token is mapped onto the Material 3 role that plays the same part:
 *
 *   Paper    → background, surface            Card     → surfaceContainer* (cards, sheets)
 *   Ink      → onBackground, onSurface         Graphite → onSurfaceVariant (times, secondary text)
 *   Hairline → outlineVariant (borders)        Sprout   → primary (the seed, listening, Catch)
 *   Clay     → error (missed, failures)        Amber    → tertiary (reminders; unused for now)
 *
 * The home-screen widget can't use Compose, so res/values(-night)/colors.xml repeats these.
 */
object PipColors {
    /** The seed mark (res/values/logo.xml): a paper-coloured seed drawn in deep green (logo option 1). */
    val seedLight = SeedColors(fill = Color(0xFFFBF9F4), line = Color(0xFF2A6643))
    val seedDark = SeedColors(fill = Color(0xFFEDE9E1), line = Color(0xFF2A6643))

    private object Light {
        val paper = Color(0xFFF6F3EC)
        val card = Color(0xFFFBF9F4)
        val ink = Color(0xFF1D1C1A)
        val graphite = Color(0xFF6B6760)
        val hairline = Color(0xFFE4DFD5)
        val sprout = Color(0xFF3E8E5E)
        val sproutTint = Color(0xFFDDEBDF) // Soft Sprout circle behind the seed button.
        val amber = Color(0xFFC98A2B)
        val clay = Color(0xFFB4553F)
    }

    private object Dark { // Warm charcoal, never pure black.
        val paper = Color(0xFF161513)
        val card = Color(0xFF201F1C)
        val ink = Color(0xFFEDE9E1)
        val graphite = Color(0xFF9C978D)
        val hairline = Color(0xFF2E2C28)
        val sprout = Color(0xFF6CC08A)
        val sproutTint = Color(0xFF243529)
        val amber = Color(0xFFE0A94F)
        val clay = Color(0xFFD9785F)
    }

    val light: ColorScheme = with(Light) {
        lightColorScheme(
            primary = sprout, onPrimary = card, primaryContainer = sproutTint, onPrimaryContainer = ink,
            secondary = graphite, onSecondary = card, secondaryContainer = hairline, onSecondaryContainer = ink,
            tertiary = amber, onTertiary = card,
            background = paper, onBackground = ink,
            surface = paper, onSurface = ink, surfaceVariant = hairline, onSurfaceVariant = graphite,
            surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = card, surfaceContainerHighest = card,
            outline = graphite, outlineVariant = hairline,
            error = clay, onError = card,
            inverseSurface = ink, inverseOnSurface = paper, scrim = ink,
        )
    }

    val dark: ColorScheme = with(Dark) {
        darkColorScheme(
            primary = sprout, onPrimary = paper, primaryContainer = sproutTint, onPrimaryContainer = ink,
            secondary = graphite, onSecondary = paper, secondaryContainer = hairline, onSecondaryContainer = ink,
            tertiary = amber, onTertiary = paper,
            background = paper, onBackground = ink,
            surface = paper, onSurface = ink, surfaceVariant = hairline, onSurfaceVariant = graphite,
            surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = card, surfaceContainerHighest = card,
            outline = graphite, outlineVariant = hairline,
            error = clay, onError = paper,
            inverseSurface = ink, inverseOnSurface = paper, scrim = Color.Black,
        )
    }
}

/** The two colours of the full seed mark: its fill and its lines. */
data class SeedColors(val fill: Color, val line: Color)

/** Provided by PipTheme (light or dark); read by ui/components/Seed. */
val LocalSeedColors = staticCompositionLocalOf { PipColors.seedLight }
