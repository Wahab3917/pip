package one.mindly.pip

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.quick.QuickActionActivity
import one.mindly.pip.service.ListeningService
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the widget and tile do, without tapping them: open QuickActionActivity with an action
 * and check the service reacts. Puts "Hey Pip" back the way it found it.
 */
@RunWith(AndroidJUnit4::class)
class QuickActionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val graph = app.appGraph

    @Before
    fun grantMic() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun toggleTurnsHeyPipOnAndOff() = runBlocking<Unit> {
        val before = graph.settings.values.value.listeningEnabled
        try {
            graph.settings.setListeningEnabled(false)
            ListeningService.stop(app)
            withTimeout(10_000) { graph.engineStatus.first { it == EngineStatus.Off } }

            launch(QuickActionActivity.ACTION_TOGGLE_LISTENING)
            assertEquals(true, graph.settings.values.value.listeningEnabled)
            withTimeout(30_000) { graph.engineStatus.first { it != EngineStatus.Off && it != EngineStatus.Starting } }

            launch(QuickActionActivity.ACTION_TOGGLE_LISTENING)
            assertEquals(false, graph.settings.values.value.listeningEnabled)
            withTimeout(10_000) { graph.engineStatus.first { it == EngineStatus.Off } }
        } finally {
            graph.settings.setListeningEnabled(before)
            if (before) ListeningService.start(app)
        }
    }

    @Test
    fun catchStartsACaptureWithHeyPipOff() = runBlocking<Unit> {
        val before = graph.settings.values.value.listeningEnabled
        try {
            graph.settings.setListeningEnabled(false)
            ListeningService.stop(app)
            withTimeout(10_000) { graph.engineStatus.first { it == EngineStatus.Off } }

            launch(QuickActionActivity.ACTION_CATCH)
            withTimeout(30_000) { graph.engineStatus.first { it == EngineStatus.Capturing } }
            // Nobody speaks, so it gives up after a few seconds and the service stops by itself.
            withTimeout(60_000) { graph.engineStatus.first { it == EngineStatus.Off } }
        } finally {
            graph.settings.setListeningEnabled(before)
            if (before) ListeningService.start(app)
        }
    }

    private fun launch(action: String) {
        ActivityScenario.launch<QuickActionActivity>(QuickActionActivity.intent(app, action)).close()
    }
}
