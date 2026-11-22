package one.mindly.pip.ui.note

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import one.mindly.pip.R
import one.mindly.pip.core.audio.ThoughtPlayer
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipType

/**
 * One thought, as a bottom sheet over the Pocket ("Pip — Design Guidelines" → Detail sheet):
 * when it was caught with Edit and Delete, then the text large in the serif, then the player.
 *
 * Edit opens the full-screen EditScreen (typing in a sheet fights the keyboard for space). It's
 * offered only once the text is done (READY) or has failed, so the background job can't
 * overwrite the user's words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    thought: Thought,
    player: ThoughtPlayer.State,
    positionMs: () -> Int,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable(thought.id) { mutableStateOf(false) }
    val canEdit = thought.status == ThoughtStatus.READY || thought.status == ThoughtStatus.FAILED
    val seconds = (thought.durationMs / 1000).toInt()
    val caught = DateUtils.formatDateTime(
        context, thought.createdAt.toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    ) + " · " + stringResource(R.string.thought_duration, seconds / 60, seconds % 60)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PipSpacing.l)
                .padding(bottom = PipSpacing.l)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(PipSpacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(caught, style = PipType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.note_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.note_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            when (thought.status) {
                ThoughtStatus.CAUGHT, ThoughtStatus.TRANSCRIBING ->
                    Text(stringResource(R.string.note_transcribing), style = PipType.thoughtLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ThoughtStatus.FAILED -> Column {
                    Text(stringResource(R.string.note_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.note_retry)) }
                }
                ThoughtStatus.READY -> if (thought.transcript.isNullOrBlank()) {
                    Text(stringResource(R.string.note_no_words), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer { Text(thought.transcript, style = PipType.thoughtLarge, color = MaterialTheme.colorScheme.onSurface) }
                }
            }

            AudioBar(
                durationMs = thought.durationMs.toInt(),
                player = player.takeIf { it.id == thought.id } ?: ThoughtPlayer.State(),
                positionMs = positionMs,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.note_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.note_cancel)) } },
        )
    }
}
