package one.mindly.pip.ui.home

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import one.mindly.pip.R
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.core.wakeword.WakeWordTuning
import one.mindly.pip.ui.statusText
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipTheme
import java.time.Instant
import java.util.UUID

/** Permission facts the screen needs; MainActivity owns the actual permission requests. */
data class PermissionState(
    val micGranted: Boolean,
    val micPermanentlyDenied: Boolean,
    val notificationsGranted: Boolean,
)

/**
 * Home screen: the "Hey Pip" switch and its sensitivity, and the list of caught thoughts.
 * Tapping a thought opens it (NoteScreen). Long-pressing one starts selecting, for deleting
 * several at once; back (or ✕) stops selecting.
 * Stateless apart from the selection, which only matters on this screen: it draws [state]
 * and reports taps through callbacks, which makes it easy to preview and, later, to restyle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    permissions: PermissionState,
    onListeningChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onPauseInBatterySaverChange: (Boolean) -> Unit,
    onThoughtClick: (Thought) -> Unit,
    onDeleteThoughts: (List<Thought>) -> Unit,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Ids (as strings, so they survive rotation) of the selected thoughts; selecting while non-empty.
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Thoughts deleted elsewhere (e.g. on the note screen) drop out of the selection by themselves.
    val selected = state.thoughts.filter { it.id.toString() in selectedIds }
    val selecting = selected.isNotEmpty()
    fun toggle(thought: Thought) {
        val id = thought.id.toString()
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.home_selection_close))
                        }
                    },
                    title = { Text(pluralStringResource(R.plurals.home_selected, selected.size, selected.size)) },
                    actions = {
                        if (selected.size < state.thoughts.size) {
                            TextButton(onClick = { selectedIds = state.thoughts.map { it.id.toString() }.toSet() }) {
                                Text(stringResource(R.string.home_select_all))
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.note_delete))
                        }
                    },
                )
            } else {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + PipSpacing.s,
                bottom = padding.calculateBottomPadding() + PipSpacing.l,
                start = PipSpacing.m,
                end = PipSpacing.m,
            ),
            verticalArrangement = Arrangement.spacedBy(PipSpacing.m),
        ) {
            if (!permissions.micGranted) {
                item { MicPermissionCard(permissions.micPermanentlyDenied, onRequestMic, onOpenSettings) }
            }
            item {
                ListeningCard(
                    state = state,
                    showNotificationHint = permissions.micGranted && !permissions.notificationsGranted,
                    onListeningChange = onListeningChange,
                    onSensitivityChange = onSensitivityChange,
                    onPauseInBatterySaverChange = onPauseInBatterySaverChange,
                )
            }
            item {
                Text(
                    stringResource(R.string.home_recordings_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = PipSpacing.s),
                )
            }
            if (state.thoughts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_recordings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.thoughts, key = { it.id }) { thought ->
                Column(Modifier.animateItem(placementSpec = PipMotion.listItemPlacement)) {
                    val isSelected = thought.id.toString() in selectedIds
                    ThoughtRow(
                        thought,
                        selected = if (selecting) isSelected else null,
                        onClick = { if (selecting) toggle(thought) else onThoughtClick(thought) },
                        onLongClick = { toggle(thought) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteSelectedDialog(
            count = selected.size,
            onConfirm = {
                onDeleteThoughts(selected)
                selectedIds = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DeleteSelectedDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.home_delete_title, count, count)) },
        text = { Text(stringResource(R.string.home_delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.note_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.note_cancel)) } },
    )
}

@Composable
private fun MicPermissionCard(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PipSpacing.m), verticalArrangement = Arrangement.spacedBy(PipSpacing.s)) {
            Text(
                stringResource(if (permanentlyDenied) R.string.perm_mic_denied_forever else R.string.perm_mic_needed),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (permanentlyDenied) {
                Button(onClick = onOpenSettings) { Text(stringResource(R.string.perm_open_settings)) }
            } else {
                Button(onClick = onRequest) { Text(stringResource(R.string.perm_mic_grant)) }
            }
        }
    }
}

@Composable
private fun ListeningCard(
    state: HomeUiState,
    showNotificationHint: Boolean,
    onListeningChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onPauseInBatterySaverChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PipSpacing.m), verticalArrangement = Arrangement.spacedBy(PipSpacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_listening_label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(state.engineStatus.statusText()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.settings.listeningEnabled, onCheckedChange = onListeningChange)
            }
            if (showNotificationHint) {
                Text(
                    stringResource(R.string.perm_notifications_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SensitivitySlider(state.settings.sensitivity, onSensitivityChange)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_battery_saver_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.settings.pauseInBatterySaver, onCheckedChange = onPauseInBatterySaverChange)
            }
        }
    }
}

/**
 * The slider moves freely while dragging but only saves when released, because every saved
 * change makes the service reload the wake-word model.
 */
@Composable
private fun SensitivitySlider(saved: Float, onChange: (Float) -> Unit) {
    var dragging by remember(saved) { mutableFloatStateOf(saved) }
    val threshold = WakeWordTuning.thresholdFor(dragging)
    Column {
        Text(stringResource(R.string.home_sensitivity_label), style = MaterialTheme.typography.titleSmall)
        Slider(value = dragging, onValueChange = { dragging = it }, onValueChangeFinished = { onChange(dragging) })
        Row {
            Text(
                stringResource(R.string.home_sensitivity_strict),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.home_sensitivity_loose), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            stringResource(R.string.home_sensitivity_detail, threshold),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The note's text (or where its transcript is at), then when it was caught and how long it is.
 * [selected] is null outside selection mode; otherwise a checkbox shows it.
 */
@Composable
private fun ThoughtRow(thought: Thought, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val seconds = (thought.durationMs / 1000).toInt()
    val text = thought.transcript?.takeIf { thought.status == ThoughtStatus.READY && it.isNotBlank() }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        leadingContent = selected?.let { { Checkbox(checked = it, onCheckedChange = { onClick() }) } },
        headlineContent = {
            Text(
                text ?: stringResource(thought.status.placeholder()),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(
                DateUtils.formatDateTime(
                    context, thought.createdAt.toEpochMilli(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                ) + " · " + stringResource(R.string.thought_duration, seconds / 60, seconds % 60)
            )
        },
    )
}

/** Shown instead of the text when there's no transcript (yet). */
private fun ThoughtStatus.placeholder(): Int = when (this) {
    ThoughtStatus.CAUGHT, ThoughtStatus.TRANSCRIBING -> R.string.thought_transcribing
    ThoughtStatus.FAILED -> R.string.thought_failed
    ThoughtStatus.READY -> R.string.thought_no_words
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PipTheme {
        HomeScreen(
            state = HomeUiState(
                settings = PipSettings.Values(true, 0.5f, 0L, false),
                engineStatus = EngineStatus.Listening,
                thoughts = listOf(
                    Thought(UUID.randomUUID(), Instant.now(), "thoughts/a.wav", 7_400),
                    Thought(
                        UUID.randomUUID(), Instant.now().minusSeconds(3600), "thoughts/b.wav", 63_000,
                        transcript = "Buy oat milk, eggs, and a new phone charger.", status = ThoughtStatus.READY,
                    ),
                ),
            ),
            permissions = PermissionState(micGranted = true, micPermanentlyDenied = false, notificationsGranted = true),
            onListeningChange = {}, onSensitivityChange = {}, onPauseInBatterySaverChange = {},
            onThoughtClick = {}, onDeleteThoughts = {}, onRequestMic = {}, onOpenSettings = {},
        )
    }
}
