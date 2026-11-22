package one.mindly.pip

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.processing.WorkManagerProcessor
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.storage.PipDatabase
import one.mindly.pip.core.storage.ThoughtRepository
import one.mindly.pip.quick.PipWidget

/**
 * Creates the app-wide singletons once, by hand ("manual dependency injection").
 * For an app this size that's clearer than a DI framework: everything is visible right here.
 */
class PipApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.processor.start() // Transcribe anything still waiting from before.

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        // Keep the widget in step with what it shows (Hey Pip on/off, dark mode, "catching"), whoever changed it.
        scope.launch {
            combine(
                graph.settings.values.map { it.listeningEnabled to it.darkMode }.distinctUntilChanged(),
                graph.engineStatus.map { it == EngineStatus.Capturing }.distinctUntilChanged(),
            ) { settings, catching -> settings to catching }.collect {
                PipWidget.updateAll(this@PipApp)
            }
        }
        // Pip's dark-mode setting (light by default) also drives the window background and system
        // bars, not just Compose: on Android 12+ tell the system which mode this app is in.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiMode = getSystemService(UiModeManager::class.java)
            scope.launch {
                graph.settings.values.map { it.darkMode }.distinctUntilChanged().collect { dark ->
                    uiMode.setApplicationNightMode(if (dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO)
                }
            }
        }
    }
}

class AppGraph(context: Context) {
    val settings = PipSettings(context)
    val database = PipDatabase.create(context)
    val processor = WorkManagerProcessor(context)
    val repository = ThoughtRepository(database.thoughtDao(), context.filesDir, processor)

    /** Written by ListeningService, read by the UI. Off whenever the service isn't running. */
    val engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Off)

    /** Voice level 0..1 while a thought is being caught; drives the capture overlay. */
    val captureLevel = MutableStateFlow(0f)
}

val Context.appGraph: AppGraph get() = (applicationContext as PipApp).graph
