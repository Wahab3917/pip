package one.mindly.pip.ui.note

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import one.mindly.pip.R
import one.mindly.pip.core.audio.ThoughtPlayer
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipTheme
import java.time.Instant
import java.util.UUID

/**
 * One note: the audio player on top, the transcript below. Stateless, like HomeScreen.
 *
 * The transcript is what the note *is*; the recording is kept as the safety net (the
 * transcript can be wrong, the audio can't), so it's always one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    thought: Thought,
    player: ThoughtPlayer.State,
    positionMs: () -> Int,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        DateUtils.formatDateTime(
                            context, thought.createdAt.toEpochMilli(),
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.note_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = PipSpacing.m)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(PipSpacing.m),
        ) {
            AudioBar(
                durationMs = thought.durationMs.toInt(),
                player = player.takeIf { it.id == thought.id } ?: ThoughtPlayer.State(),
                positionMs = positionMs,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
            )
            Transcript(thought, onRetry)
        }
    }
}

@Composable
private fun AudioBar(
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

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = PipSpacing.s, vertical = PipSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    painterResource(if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                    stringResource(if (player.isPlaying) R.string.note_pause else R.string.note_play),
                )
            }
            Slider(
                value = position.coerceIn(0f, total.toFloat()),
                valueRange = 0f..total.toFloat(),
                onValueChange = { dragging = true; position = it },
                onValueChangeFinished = { dragging = false; onSeek(position.toInt()) },
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.note_time, clock(position.toInt()), clock(total)),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = PipSpacing.s),
            )
        }
    }
}

@Composable
private fun Transcript(thought: Thought, onRetry: () -> Unit) {
    when (thought.status) {
        ThoughtStatus.CAUGHT, ThoughtStatus.TRANSCRIBING -> Column(verticalArrangement = Arrangement.spacedBy(PipSpacing.s)) {
            Text(
                stringResource(R.string.note_transcribing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        ThoughtStatus.FAILED -> Column {
            Text(
                stringResource(R.string.note_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.note_retry)) }
        }
        ThoughtStatus.READY -> if (thought.transcript.isNullOrBlank()) {
            Text(
                stringResource(R.string.note_no_words),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SelectionContainer { Text(thought.transcript, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** 75_000 ms → "1:15". */
private fun clock(ms: Int): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Preview(showBackground = true)
@Composable
private fun NoteScreenPreview() {
    PipTheme {
        NoteScreen(
            thought = Thought(
                UUID.randomUUID(), Instant.now(), "thoughts/a.wav", 7_400,
                transcript = "Idea for the app: let people pin their favourite thoughts to the top.",
                status = ThoughtStatus.READY,
            ),
            player = ThoughtPlayer.State(),
            positionMs = { 0 },
            onTogglePlay = {}, onSeek = {}, onRetry = {}, onBack = {},
        )
    }
}
