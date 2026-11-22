package one.mindly.pip.quick

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import one.mindly.pip.MainActivity
import one.mindly.pip.appGraph
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.service.ListeningService

/**
 * An invisible screen that the quick-settings tile and the widget open for a split second.
 *
 * Why not start the service directly from the tile or widget? Android only lets a foreground
 * service use the microphone if it was started while the app was in the foreground. This
 * activity is that moment: it starts the service, waits until the engine has picked up the
 * request, then closes. It is allowed to show over the lock screen (showWhenLocked in the
 * manifest), so the tile works without unlocking the phone.
 */
class QuickActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Invisible, and taps go straight through to whatever is underneath while we wait.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // The home screen explains and asks for the mic.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        val graph = appGraph
        // Each action starts something, then says what engine status means "it got the message".
        val done: (EngineStatus) -> Boolean = when (intent.action) {
            ACTION_CATCH -> {
                ListeningService.catchNow(this)
                ::isCapturing
            }
            ACTION_TOGGLE_LISTENING -> {
                val enable = !graph.settings.values.value.listeningEnabled
                graph.settings.setListeningEnabled(enable)
                if (enable) {
                    ListeningService.start(this)
                    ::isRunning
                } else {
                    ListeningService.stop(this)
                    ::always
                }
            }
            else -> ::always
        }

        lifecycleScope.launch {
            val ok = withTimeoutOrNull(WAIT_MS) { graph.engineStatus.first(done) }
            if (ok == null) Log.w(PIP_LOG_TAG, "[Quick] ${intent.action}: engine didn't respond in time")
            finish()
        }
    }

    private fun isCapturing(status: EngineStatus) = status == EngineStatus.Capturing
    private fun isRunning(status: EngineStatus) = status != EngineStatus.Off && status != EngineStatus.Starting
    @Suppress("UNUSED_PARAMETER")
    private fun always(status: EngineStatus) = true

    companion object {
        const val ACTION_CATCH = "one.mindly.pip.quick.CATCH"
        const val ACTION_TOGGLE_LISTENING = "one.mindly.pip.quick.TOGGLE_LISTENING"

        /** Long enough for the service to start and open the mic on a slow phone. */
        private const val WAIT_MS = 3_000L

        fun intent(context: Context, action: String): Intent =
            Intent(context, QuickActionActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
