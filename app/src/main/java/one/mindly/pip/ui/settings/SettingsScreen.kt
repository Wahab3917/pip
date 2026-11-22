package one.mindly.pip.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import one.mindly.pip.R
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.wakeword.WakeWordTuning
import one.mindly.pip.ui.components.Seed
import one.mindly.pip.ui.statusText
import one.mindly.pip.ui.theme.PipSpacing
import one.mindly.pip.ui.theme.PipType

/**
 * Settings: a plain grouped list, no icons beside rows except the seed for the Listening group
 * ("Pip — Design Guidelines" → Settings). Holds what used to be on the home screen's Hey Pip card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PipSettings.Values,
    engineStatus: EngineStatus,
    showNotificationHint: Boolean,
    onListeningChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onPauseInBatterySaverChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.note_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.padding(horizontal = PipSpacing.m, vertical = PipSpacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PipSpacing.s),
            ) {
                Seed(size = 16.dp, solid = true)
                Text(stringResource(R.string.settings_group_listening), style = PipType.dayLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ListItem(
                colors = rowColors,
                headlineContent = { Text(stringResource(R.string.home_listening_label)) },
                supportingContent = {
                    Text(stringResource(engineStatus.statusText()))
                },
                trailingContent = { Switch(checked = settings.listeningEnabled, onCheckedChange = onListeningChange) },
            )
            if (showNotificationHint) {
                Text(
                    stringResource(R.string.perm_notifications_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = PipSpacing.m),
                )
            }
            SensitivitySlider(settings.sensitivity, onSensitivityChange)
            ListItem(
                colors = rowColors,
                headlineContent = { Text(stringResource(R.string.home_battery_saver_label)) },
                trailingContent = { Switch(checked = settings.pauseInBatterySaver, onCheckedChange = onPauseInBatterySaverChange) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/**
 * The slider moves freely while dragging but only saves when released, because every saved
 * change makes the service update the wake-word threshold.
 */
@Composable
private fun SensitivitySlider(saved: Float, onChange: (Float) -> Unit) {
    var dragging by remember(saved) { mutableFloatStateOf(saved) }
    val threshold = WakeWordTuning.thresholdFor(dragging)
    Column(Modifier.padding(horizontal = PipSpacing.m, vertical = PipSpacing.s)) {
        Text(stringResource(R.string.home_sensitivity_label), style = MaterialTheme.typography.bodyLarge)
        Slider(value = dragging, onValueChange = { dragging = it }, onValueChangeFinished = { onChange(dragging) })
        Row {
            Text(stringResource(R.string.home_sensitivity_strict), style = PipType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.home_sensitivity_loose), style = PipType.meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.home_sensitivity_detail, threshold),
            style = PipType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
