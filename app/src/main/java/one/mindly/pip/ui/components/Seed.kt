package one.mindly.pip.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/**
 * The "pip" wordmark: lowercase, and the dot of the i is the seed in Sprout
 * ("Pip — Design Guidelines" → Logo). Drawn from text so it uses the bundled font.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    val ink = MaterialTheme.colorScheme.onSurface
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = name }) {
        Text("p", style = PipType.wordmark, color = ink)
        Box {
            Text("ı", style = PipType.wordmark, color = ink) // Dotless i: the seed is its dot.
            Seed(size = 10.dp, solid = true, modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp))
        }
        Text("p", style = PipType.wordmark, color = ink)
    }
}
