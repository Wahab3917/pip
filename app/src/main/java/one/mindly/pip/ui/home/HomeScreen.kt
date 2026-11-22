package one.mindly.pip.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * Tapping a thought opens it (NoteScreen).
 * Stateless: it only draws [state] and reports taps through callbacks, which makes it easy
 * to preview and, later, to restyle.
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
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
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
                    ThoughtRow(thought, onClick = { onThoughtClick(thought) })
                    HorizontalDivider()
                }
            }
        }
    }
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

/** The note's text (or where its transcript is at), then when it was caught and how long it is. */
@Composable
private fun ThoughtRow(thought: Thought, onClick: () -> Unit) {
    val context = LocalContext.current
    val seconds = (thought.durationMs / 1000).toInt()
    val text = thought.transcript?.takeIf { thought.status == ThoughtStatus.READY && it.isNotBlank() }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
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
            onThoughtClick = {}, onRequestMic = {}, onOpenSettings = {},
        )
    }
}
