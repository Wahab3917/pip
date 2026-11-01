package one.mindly.pip.core

/**
 * Every Pip log line uses this one tag, with a [Component] prefix in the message,
 * so `adb logcat -s Pip` shows the whole story in order.
 */
const val PIP_LOG_TAG = "Pip"
