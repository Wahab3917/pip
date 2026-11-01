package one.mindly.pip

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.processing.LogOnlyProcessor
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.storage.PipDatabase
import one.mindly.pip.core.storage.ThoughtRepository

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
    }
}

class AppGraph(context: Context) {
    val settings = PipSettings(context)
    val database = PipDatabase.create(context)
    val repository = ThoughtRepository(database.thoughtDao(), context.filesDir, LogOnlyProcessor())

    /** Written by ListeningService, read by the UI. Off whenever the service isn't running. */
    val engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Off)
}

val Context.appGraph: AppGraph get() = (applicationContext as PipApp).graph
