package one.mindly.pip.ui.home

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.mindly.pip.R
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.ui.components.ConfirmDialog
import one.mindly.pip.ui.components.Seed
import one.mindly.pip.ui.components.Wordmark
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipTheme
import one.mindly.pip.ui.theme.PipType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/** Permission facts the screen needs; MainActivity owns the actual permission requests. */
data class PermissionState(
    val micGranted: Boolean,
    val micPermanentlyDenied: Boolean,
    val notificationsGranted: Boolean,
)

/**
 * The Pocket: every caught thought, newest first, grouped by day ("Pip — Design Guidelines" →
 * Screen layouts). Top bar: the wordmark, a status pill that turns "Hey Pip" on and off, and
 * Settings. The seed button at the bottom catches a thought.
 *
 * Tapping a thought opens it (MainActivity shows the NoteSheet). Long-pressing one starts
 * selecting, for deleting several at once; back (or ✕) stops selecting.
 * Stateless apart from the selection: it draws [state] and reports taps through callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    permissions: PermissionState,
    onToggleListening: () -> Unit,
    onCatch: () -> Unit,
    onOpenSettings: () -> Unit,
    onThoughtClick: (Thought) -> Unit,
    onPlay: (Thought) -> Unit,
    onDeleteThoughts: (List<Thought>) -> Unit,
    onRequestMic: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    // Ids (as strings, so they survive rotation) of the selected thoughts; selecting while non-empty.
    var selectedIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Thoughts deleted elsewhere (e.g. in the sheet) drop out of the selection by themselves.
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
                TopAppBar(
                    title = { Wordmark() },
                    actions = {
                        HeyPipToggle(state.settings.listeningEnabled, state.engineStatus, onToggleListening)
                        IconButton(onClick = onOpenSettings) {
                            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_open))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = PipSpacing.m, end = PipSpacing.m, top = PipSpacing.s,
                    bottom = padding.calculateBottomPadding() + PipSpacing.listBottomForSeed,
                ),
                verticalArrangement = Arrangement.spacedBy(PipSpacing.s),
            ) {
                if (!permissions.micGranted) {
                    item { MicPermissionCard(permissions.micPermanentlyDenied, onRequestMic, onOpenAppSettings) }
                }
                if (state.thoughts.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_recordings_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PipSpacing.l),
                        )
                    }
                }
                for ((day, thoughts) in groupByDay(state.thoughts)) {
                    item(key = "day-$day") {
                        Text(
                            dayLabel(day),
                            style = PipType.dayLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PipSpacing.s, bottom = PipSpacing.xs).animateItem(),
                        )
                    }
                    items(thoughts, key = { it.id }) { thought ->
                        ThoughtCard(
                            thought,
                            selected = if (selecting) thought.id.toString() in selectedIds else null,
                            onClick = { if (selecting) toggle(thought) else onThoughtClick(thought) },
                            onLongClick = { toggle(thought) },
                            onPlay = { onPlay(thought) },
                            modifier = Modifier.animateItem(placementSpec = PipMotion.listItemPlacement),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !selecting,
                enter = fadeIn(tween(PipMotion.MEDIUM_MS)),
                exit = fadeOut(tween(PipMotion.MEDIUM_MS)),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = PipSpacing.l),
            ) {
                SeedButton(onClick = onCatch)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.home_delete_title, selected.size, selected.size),
            text = stringResource(R.string.home_delete_text),
            confirm = stringResource(R.string.note_delete),
            dismiss = stringResource(R.string.note_cancel),
            onConfirm = {
                onDeleteThoughts(selected)
                selectedIds = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * "Hey Pip" with a small switch: reads as a setting you can flip, not just a status. Tapping
 * anywhere on it turns "Hey Pip" on or off. While on but paused (a call, battery saver…), it says so.
 */
@Composable
private fun HeyPipToggle(enabled: Boolean, status: EngineStatus, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val paused = enabled && (status is EngineStatus.Paused || status == EngineStatus.MicUnavailable)
    val label = stringResource(R.string.home_listening_label) + if (paused) " · " + stringResource(R.string.pill_paused) else ""
    val action = stringResource(if (enabled) R.string.pill_turn_off else R.string.pill_turn_on)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.surface,
        border = BorderStroke(PipSpacing.hairline, colors.outlineVariant),
        modifier = Modifier.semantics { contentDescription = action },
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = PipType.meta, color = colors.onSurface)
            MiniSwitch(checked = enabled)
        }
    }
}

/** A small, display-only switch (the pill around it takes the tap). */
@Composable
private fun MiniSwitch(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val thumb by animateDpAsState(if (checked) PipSpacing.miniSwitchTravel else 0.dp, tween(PipMotion.MEDIUM_MS), label = "thumb")
    Box(
        Modifier
            .size(PipSpacing.miniSwitchWidth, PipSpacing.miniSwitchHeight)
            .clip(CircleShape)
            .background(if (checked) colors.primary else colors.outlineVariant)
            .padding(PipSpacing.miniSwitchPad),
    ) {
        Box(
            Modifier
                .offset(x = thumb)
                .size(PipSpacing.miniSwitchHeight - PipSpacing.miniSwitchPad * 2)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.surface)
        )
    }
}

/**
 * A thought: its text in the serif, up to 3 lines, then the time and length. Before there's
 * text, a shimmer and "Writing it down…" (or what went wrong) with a play button, so the
 * recording is always one tap away. [selected] is null outside selection mode.
 */
@Composable
private fun ThoughtCard(
    thought: Thought,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val text = thought.transcript?.takeIf { thought.status == ThoughtStatus.READY && it.isNotBlank() }
    val seconds = (thought.durationMs / 1000).toInt()
    val meta = DateFormat.getTimeFormat(context).format(Date(thought.createdAt.toEpochMilli())) +
        " · " + stringResource(R.string.thought_duration, seconds / 60, seconds % 60)

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected == true) colors.primaryContainer else colors.surfaceContainer)
            .border(PipSpacing.hairline, if (selected == true) colors.primary else colors.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = PipSpacing.m, end = PipSpacing.s, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipSpacing.s),
    ) {
        if (selected != null) Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(Modifier.weight(1f).padding(end = PipSpacing.s), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                text != null -> Text(text, style = PipType.thought, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                thought.status == ThoughtStatus.CAUGHT || thought.status == ThoughtStatus.TRANSCRIBING -> Shimmer()
                else -> Text(
                    stringResource(if (thought.status == ThoughtStatus.FAILED) R.string.thought_failed else R.string.thought_no_words),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Text(
                if (thought.status == ThoughtStatus.CAUGHT || thought.status == ThoughtStatus.TRANSCRIBING) {
                    stringResource(R.string.thought_transcribing) + " · " + meta
                } else {
                    meta
                },
                style = PipType.meta,
                color = colors.onSurfaceVariant,
            )
        }
        if (text == null && selected == null) {
            IconButton(onClick = onPlay) {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.thought_play), tint = colors.onSurfaceVariant)
            }
        }
    }
}

/** A soft bar of light sweeping across while the thought is being written down. */
@Composable
private fun Shimmer() {
    val colors = MaterialTheme.colorScheme
    val shift by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PipMotion.shimmerMs, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    0f to colors.outlineVariant,
                    shift to colors.primaryContainer,
                    1f to colors.outlineVariant,
                )
            )
    )
}

/** The seed in a soft Sprout circle: catches a thought (the capture overlay then takes over). */
@Composable
private fun SeedButton(onClick: () -> Unit) {
    val label = stringResource(R.string.seed_catch)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(PipSpacing.seedButton).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) { Seed(size = PipSpacing.seedIcon) }
    }
}

@Composable
private fun MicPermissionCard(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(PipSpacing.hairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(PipSpacing.m),
        verticalArrangement = Arrangement.spacedBy(PipSpacing.s),
    ) {
        Text(
            stringResource(if (permanentlyDenied) R.string.perm_mic_denied_forever else R.string.perm_mic_needed),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.perm_open_settings)) }
        } else {
            Button(onClick = onRequest) { Text(stringResource(R.string.perm_mic_grant)) }
        }
    }
}

/** Consecutive thoughts from the same calendar day, newest day first (the list is already newest first). */
private fun groupByDay(thoughts: List<Thought>): List<Pair<LocalDate, List<Thought>>> {
    val zone = ZoneId.systemDefault()
    return thoughts.groupBy { it.createdAt.atZone(zone).toLocalDate() }.toList()
}

/** "Today", "Yesterday", then "Mon, 17 Nov" (with the year once it's not this year). */
@Composable
private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> {
            val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            var flags = DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
            if (day.year != today.year) flags = flags or DateUtils.FORMAT_SHOW_YEAR
            DateUtils.formatDateTime(LocalContext.current, millis, flags)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PipTheme {
        HomeScreen(
            state = HomeUiState(
                settings = PipSettings.Values(false, 0.15f, 0L, false),
                engineStatus = EngineStatus.Off,
                thoughts = listOf(
                    Thought(UUID.randomUUID(), Instant.now(), "thoughts/a.wav", 4_200),
                    Thought(
                        UUID.randomUUID(), Instant.now().minusSeconds(3600), "thoughts/b.wav", 7_000,
                        transcript = "Idea for the app: let people pin their favourite thoughts to the top.",
                        status = ThoughtStatus.READY,
                    ),
                    Thought(
                        UUID.randomUUID(), Instant.now().minusSeconds(90_000), "thoughts/c.wav", 5_000,
                        transcript = "Book recommendation from Ali: Thinking, Fast and Slow.", status = ThoughtStatus.READY,
                    ),
                ),
            ),
            permissions = PermissionState(micGranted = true, micPermanentlyDenied = false, notificationsGranted = true),
            onToggleListening = {}, onCatch = {}, onOpenSettings = {}, onThoughtClick = {}, onPlay = {},
            onDeleteThoughts = {}, onRequestMic = {}, onOpenAppSettings = {},
        )
    }
}
