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
import one.mindly.pip.ui.theme.PipType

/** Pip's mark (res/values/logo.xml), tinted. Decorative: callers describe what it does. */
@Composable
fun Seed(size: Dp, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Icon(painterResource(R.drawable.ic_seed), contentDescription = null, tint = tint, modifier = modifier.size(size))
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
            Seed(size = 9.dp, modifier = Modifier.align(Alignment.TopCenter).offset(y = 3.dp))
        }
        Text("p", style = PipType.wordmark, color = ink)
    }
}
