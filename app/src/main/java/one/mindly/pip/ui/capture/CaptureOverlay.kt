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
import one.mindly.pip.ui.theme.PipType

/**
 * Shown over everything while Pip is catching a thought and the app is open, whatever started
 * it (seed button, tile, widget or "Hey Pip"). "Pip — Design Guidelines" → Capture overlay:
 * Paper background, the seed breathing with your voice, "Listening…", a thin live waveform,
 * and "Tap to finish" (anywhere on the screen, or back) to end the note early and keep it.
 */
@Composable
fun CaptureOverlay(level: Float, onFinish: () -> Unit) {
    BackHandler(onBack = onFinish)
    val colors = MaterialTheme.colorScheme
    val breath by animateFloatAsState(1f + level * PipMotion.seedBreath, tween(PipMotion.SHORT_MS), label = "breath")

    // The last few seconds of levels, newest on the right.
    val history = remember { mutableStateListOf<Float>().apply { repeat(WAVE_POINTS) { add(0f) } } }
    LaunchedEffect(level) {
        history.removeAt(0)
        history.add(level)
    }

    val finish = stringResource(R.string.capture_tap_to_finish)
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
        Text(
            finish,
            style = PipType.meta,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = PipSpacing.xl),
        )
    }
}

/** 100 ms per level → 4 s of history. */
private const val WAVE_POINTS = 40
