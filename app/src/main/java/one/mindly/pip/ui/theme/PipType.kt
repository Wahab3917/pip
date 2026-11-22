package one.mindly.pip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import one.mindly.pip.R

/**
 * Pip's typography. Screens use MaterialTheme.typography.<style> for the interface and
 * [PipType] for the user's own words; never ad-hoc sizes.
 *
 * Source: "Pip — Design Guidelines" → Typography. Thoughts are set in a serif because they're
 * yours (Newsreader); the interface around them stays quiet (Instrument Sans). Both fonts are
 * bundled in res/font (variable fonts, SIL Open Font License, see /licenses), so they work offline.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private val Newsreader = FontFamily(
    variable(R.font.newsreader, FontWeight.Normal),
    variable(R.font.newsreader, FontWeight.Medium),
)

private val InstrumentSans = FontFamily(
    variable(R.font.instrument_sans, FontWeight.Normal),
    variable(R.font.instrument_sans, FontWeight.Medium),
    variable(R.font.instrument_sans, FontWeight.SemiBold),
    variable(R.font.instrument_sans, FontWeight.Bold),
)

/** The interface: every Material style in Instrument Sans (default sizes, 15sp body). */
val PipTypography: Typography = Typography().run {
    fun TextStyle.ui() = copy(fontFamily = InstrumentSans)
    Typography(
        displayLarge = displayLarge.ui(), displayMedium = displayMedium.ui(), displaySmall = displaySmall.ui(),
        headlineLarge = headlineLarge.ui(), headlineMedium = headlineMedium.ui(), headlineSmall = headlineSmall.ui(),
        titleLarge = titleLarge.ui(), titleMedium = titleMedium.ui(), titleSmall = titleSmall.ui(),
        bodyLarge = bodyLarge.ui().copy(fontSize = 15.sp), bodyMedium = bodyMedium.ui(), bodySmall = bodySmall.ui(),
        labelLarge = labelLarge.ui(), labelMedium = labelMedium.ui(), labelSmall = labelSmall.ui(),
    )
}

/** Styles Material doesn't have a name for. */
object PipType {
    /** A thought on a card: 19sp serif, line height 1.4. */
    val thought = TextStyle(fontFamily = Newsreader, fontSize = 19.sp, lineHeight = 1.4.em)

    /** A thought opened in the sheet or the editor: larger. */
    val thoughtLarge = TextStyle(fontFamily = Newsreader, fontSize = 23.sp, lineHeight = 1.35.em)

    /** Times and durations: 12sp, tabular numbers so they line up. */
    val meta = TextStyle(fontFamily = InstrumentSans, fontSize = 12.sp, fontFeatureSettings = "tnum")

    /** The "pip" wordmark in the top bar. */
    val wordmark = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.02).em)

    /** Quiet day labels above each group of thoughts. */
    val dayLabel = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 13.sp)
}
