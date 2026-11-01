package one.mindly.pip.core.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import one.mindly.pip.core.wakeword.WakeWordTuning

/**
 * Pip's few settings, persisted in SharedPreferences and mirrored into a StateFlow so the
 * service and the UI see every change immediately. (One process, one instance, see PipApp.)
 */
class PipSettings(context: Context) {

    data class Values(
        val listeningEnabled: Boolean,
        val sensitivity: Float,
        /** Wall-clock time (epoch ms) until which the user paused listening; 0 = not paused. */
        val pausedUntilMs: Long,
        val pauseInBatterySaver: Boolean,
    )

    private val prefs = context.getSharedPreferences("pip_settings", Context.MODE_PRIVATE)
    private val _values = MutableStateFlow(read())
    val values: StateFlow<Values> = _values

    fun setListeningEnabled(enabled: Boolean) = save { putBoolean(KEY_LISTENING, enabled) }
    fun setSensitivity(value: Float) = save { putFloat(KEY_SENSITIVITY, value.coerceIn(0f, 1f)) }
    fun setPauseInBatterySaver(enabled: Boolean) = save { putBoolean(KEY_BATTERY_SAVER, enabled) }
    fun pauseFor(durationMs: Long) =
        save { putLong(KEY_PAUSED_UNTIL, System.currentTimeMillis() + durationMs) }
    fun resume() = save { putLong(KEY_PAUSED_UNTIL, 0L) }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _values.value = read()
    }

    private fun read() = Values(
        listeningEnabled = prefs.getBoolean(KEY_LISTENING, false),
        sensitivity = prefs.getFloat(KEY_SENSITIVITY, WakeWordTuning.DEFAULT_SENSITIVITY),
        pausedUntilMs = prefs.getLong(KEY_PAUSED_UNTIL, 0L),
        pauseInBatterySaver = prefs.getBoolean(KEY_BATTERY_SAVER, false),
    )

    private companion object {
        const val KEY_LISTENING = "listening_enabled"
        // "_v2": the slider now maps to a different threshold range, so old saved positions are ignored.
        const val KEY_SENSITIVITY = "sensitivity_v2"
        const val KEY_PAUSED_UNTIL = "paused_until_ms"
        const val KEY_BATTERY_SAVER = "pause_in_battery_saver"
    }
}
