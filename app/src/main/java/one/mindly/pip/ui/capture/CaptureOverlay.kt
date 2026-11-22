package one.mindly.pip.ui.capture

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import one.mindly.pip.R
import one.mindly.pip.ui.components.Seed
import one.mindly.pip.ui.theme.PipMotion
import one.mindly.pip.ui.theme.PipSpacing

/**
 * Shown over everything while Pip is catching a thought and the app is open, whatever started
 * it (seed button, tile, widget or "Hey Pip"). "Pip — Design Guidelines" → Capture overlay:
 * Paper background, the seed breathing with your voice, "Listening…" and a thin live waveform.
 *
 * ✕ and ✓ sit together at the bottom, in reach of the thumb. ✓ (or a tap anywhere, or back) ends
 * the note now and keeps it; ✕ throws it away.
 */
@Composable
fun CaptureOverlay(level: Float, onFinish: () -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onFinish)
    val colors = MaterialTheme.colorScheme
    val breath by animateFloatAsState(1f + level * PipMotion.seedBreath, tween(PipMotion.SHORT_MS), label = "breath")

    // The last few seconds of levels, newest on the right.
    val history = remember { mutableStateListOf<Float>().apply { repeat(WAVE_POINTS) { add(0f) } } }
    LaunchedEffect(level) {
        history.removeAt(0)
        history.add(level)
    }

    val finish = stringResource(R.string.capture_done)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinish)
            .semantics { contentDescription = finish },
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PipSpacing.l),
        ) {
            Seed(size = PipSpacing.overlaySeed, modifier = Modifier.scale(breath))
            Text(stringResource(R.string.capture_listening), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Canvas(Modifier.fillMaxWidth(0.6f).height(PipSpacing.overlayWave)) {
                val step = size.width / (WAVE_POINTS - 1)
                val mid = size.height / 2
                history.forEachIndexed { i, v ->
                    val half = (v * mid).coerceAtLeast(1.5f)
                    val x = i * step
                    drawLine(colors.primary, Offset(x, mid - half), Offset(x, mid + half), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = PipSpacing.l)
                .padding(bottom = PipSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(PipSpacing.overlayButtonGap),
        ) {
            OutlinedIconButton(
                onClick = onCancel,
                border = BorderStroke(PipSpacing.hairline, colors.outline),
                modifier = Modifier.size(PipSpacing.overlayButton),
            ) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.capture_cancel), modifier = Modifier.size(PipSpacing.overlayIcon))
            }
            FilledIconButton(onClick = onFinish, modifier = Modifier.size(PipSpacing.overlayButton)) {
                Icon(painterResource(R.drawable.ic_check), finish, modifier = Modifier.size(PipSpacing.overlayIcon))
            }
        }
    }
}

/** 100 ms per level → 4 s of history. */
private const val WAVE_POINTS = 40
