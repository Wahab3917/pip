package one.mindly.pip.core.capture

/** What the listening engine is doing right now. Shown in the notification and on the home screen. */
sealed interface EngineStatus {
    data object Off : EngineStatus
    data object Starting : EngineStatus
    data object Listening : EngineStatus
    data object Capturing : EngineStatus
    data class Paused(val reason: PauseReason, val untilMs: Long? = null) : EngineStatus
    data object MicUnavailable : EngineStatus
}

enum class PauseReason { USER, CALL, MIC_BUSY, BATTERY_SAVER }
