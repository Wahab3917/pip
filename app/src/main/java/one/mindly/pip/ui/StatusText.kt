package one.mindly.pip.ui

import androidx.annotation.StringRes
import one.mindly.pip.R
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.capture.PauseReason

/** The wording for each engine status. Used by both the notification and the home screen. */
@StringRes
fun EngineStatus.statusText(): Int = when (this) {
    EngineStatus.Off -> R.string.status_off
    EngineStatus.Starting -> R.string.status_starting
    EngineStatus.Listening -> R.string.status_listening
    EngineStatus.Capturing -> R.string.status_capturing
    EngineStatus.MicUnavailable -> R.string.status_error
    is EngineStatus.Paused -> when (reason) {
        PauseReason.USER -> R.string.status_paused_user
        PauseReason.CALL -> R.string.status_paused_call
        PauseReason.MIC_BUSY -> R.string.status_paused_mic_busy
        PauseReason.BATTERY_SAVER -> R.string.status_paused_battery_saver
    }
}
