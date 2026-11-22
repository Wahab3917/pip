package one.mindly.pip.ui.note

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import one.mindly.pip.R
import one.mindly.pip.core.audio.ThoughtPlayer
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipType

/** Play/pause, a seek bar and "0:03 / 0:07" for one recording. */
@Composable
fun AudioBar(
    durationMs: Int,
    player: ThoughtPlayer.State,
    positionMs: () -> Int,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val total = (if (player.durationMs > 0) player.durationMs else durationMs).coerceAtLeast(1)
    var position by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // While playing, follow the player's position a few times a second.
    LaunchedEffect(player.isPlaying, player.id) {
        if (player.id == null) position = 0f
        while (player.isPlaying) {
            if (!dragging) position = positionMs().toFloat()
            delay(PipMotion.playerTickMs)
        }
        if (player.id != null && !dragging) position = positionMs().toFloat()
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onTogglePlay, shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
            Icon(
                painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                stringResource(if (player.isPlaying) R.string.note_pause else R.string.note_play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(10.dp),
            )
        }
        Slider(
            value = position.coerceIn(0f, total.toFloat()),
            valueRange = 0f..total.toFloat(),
            onValueChange = { dragging = true; position = it },
            onValueChangeFinished = { dragging = false; onSeek(position.toInt()) },
            modifier = Modifier.weight(1f).padding(horizontal = PipSpacing.s),
        )
        Text(
            stringResource(R.string.note_time, clock(position.toInt()), clock(total)),
            style = PipType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 75_000 ms → "1:15". */
private fun clock(ms: Int): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
