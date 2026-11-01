package one.mindly.pip.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * All of Pip's colors live here. Screens read them through MaterialTheme.colorScheme,
 * never as hard-coded Color(...) values.
 *
 * For now these are the plain Material 3 baseline schemes. The design pass replaces them.
 * (Dynamic "wallpaper" color is deliberately off so the design pass fully controls the palette.)
 */
object PipColors {
    val light: ColorScheme = lightColorScheme()
    val dark: ColorScheme = darkColorScheme()
}
