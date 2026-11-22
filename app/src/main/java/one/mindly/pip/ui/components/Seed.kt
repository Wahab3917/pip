package one.mindly.pip.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import one.mindly.pip.R
import one.mindly.pip.ui.theme.LocalSeedColors
import one.mindly.pip.ui.theme.PipType

/**
 * Pip's mark (res/values/logo.xml), the sunflower seed. Decorative: callers describe what it does.
 *
 * Full (default): the seed filled and drawn with its lines, in the theme's seed colours. Use it at
 * about 24dp and up. [solid]: just the filled outline in [tint], for small sizes where the lines
 * would vanish (the dot of the i, small markers).
 */
@Composable
fun Seed(size: Dp, solid: Boolean = false, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    if (solid) {
        Icon(painterResource(R.drawable.ic_seed_solid), contentDescription = null, tint = tint, modifier = modifier.size(size))
        return
    }
    val colors = LocalSeedColors.current
    Box(modifier.size(size)) {
        Icon(painterResource(R.drawable.ic_seed_solid), contentDescription = null, tint = colors.fill, modifier = Modifier.size(size))
        Icon(painterResource(R.drawable.ic_seed_lines), contentDescription = null, tint = colors.line, modifier = Modifier.size(size))
    }
}

/** The "Pip" wordmark, set in the interface face. (The guidelines drew it lowercase; the user chose a capital P.) */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.app_name), style = PipType.wordmark, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}
