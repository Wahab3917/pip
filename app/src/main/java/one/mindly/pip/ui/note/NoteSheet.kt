package one.mindly.pip.ui.note

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import one.mindly.pip.R
import one.mindly.pip.core.audio.ThoughtPlayer
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.ui.components.ConfirmDialog
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipType

/**
 * One thought, as a bottom sheet over the Pocket ("Pip — Design Guidelines" → Detail sheet):
 * when it was caught with Edit and Delete, then the text large in the serif, then the player.
 *
 * Edit turns the same sheet into the editor: it grows smoothly to full height (animateContentSize),
 * with ✕ and Save at the top. While editing, swiping the sheet away is off and back cancels the
 * edit, so typed words are never lost by accident. Edit is offered only once the text is done
 * (READY) or has failed, so the background job can't overwrite the user's words.
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
    onStartEdit: () -> Unit,
    onSaveText: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Reading or editing. Saveable, so rotating doesn't drop out of the editor.
    var editing by rememberSaveable(thought.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(thought.id) { mutableStateOf(false) }
    val isEditing by rememberUpdatedState(editing)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !isEditing },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !editing),
    ) {
        BackHandler(enabled = editing) { editing = false }
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(tween(PipMotion.SHEET_MS))
                .then(if (editing) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = PipSpacing.l)
                .padding(bottom = PipSpacing.l)
                .navigationBarsPadding(),
        ) {
            if (editing) {
                Editor(
                    initialText = thought.transcript.orEmpty(),
                    onCancel = { editing = false },
                    onSave = { onSaveText(it); editing = false },
                )
            } else {
                Reader(
                    thought = thought,
                    player = player,
                    positionMs = positionMs,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    onRetry = onRetry,
                    onEdit = {
                        onStartEdit()
                        editing = true
                    },
                    onDelete = { confirmDelete = true },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.note_delete_title),
            text = stringResource(R.string.note_delete_text),
            confirm = stringResource(R.string.note_delete),
            dismiss = stringResource(R.string.note_cancel),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Reader(
    thought: Thought,
    player: ThoughtPlayer.State,
    positionMs: () -> Int,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val canEdit = thought.status == ThoughtStatus.READY || thought.status == ThoughtStatus.FAILED
    val seconds = (thought.durationMs / 1000).toInt()
    val caught = DateUtils.formatDateTime(
        context, thought.createdAt.toEpochMilli(),
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
    ) + " · " + stringResource(R.string.thought_duration, seconds / 60, seconds % 60)

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PipSpacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caught, style = PipType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.note_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
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

/** The thought's words, large, in the serif, with the cursor at the end; ✕ and Save above. */
@Composable
private fun Editor(initialText: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val label = stringResource(R.string.note_transcript_label)
    Column(Modifier.fillMaxHeight().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.note_cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = PipType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSave(value.text) }) { Text(stringResource(R.string.note_save)) }
        }
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = PipType.thoughtLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = PipSpacing.s)
                .focusRequester(focus)
                .semantics { contentDescription = label },
        )
    }
}
