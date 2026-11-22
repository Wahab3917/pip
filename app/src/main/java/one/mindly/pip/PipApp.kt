package one.mindly.pip

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

        // Keep the widget's "Hey Pip: on/off" label in step with the setting, whoever changed it.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            graph.settings.values.map { it.listeningEnabled }.distinctUntilChanged().collect {
                PipWidget.updateAll(this@PipApp)
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
