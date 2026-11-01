package one.mindly.pip.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import one.mindly.pip.appGraph
import one.mindly.pip.core.audio.ThoughtPlayer
import one.mindly.pip.core.capture.EngineStatus
import one.mindly.pip.core.settings.PipSettings
import one.mindly.pip.core.storage.Thought
import one.mindly.pip.service.ListeningService
import java.util.UUID

/** Everything the home screen shows, as one immutable snapshot. */
data class HomeUiState(
    val settings: PipSettings.Values,
    val engineStatus: EngineStatus,
    val thoughts: List<Thought>,
    val playingId: UUID?,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    private val player = ThoughtPlayer()

    val uiState: StateFlow<HomeUiState> = combine(
        graph.settings.values,
        graph.engineStatus,
        graph.repository.thoughts,
        player.playing,
    ) { settings, status, thoughts, playing ->
        HomeUiState(settings, status, thoughts, playing)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(graph.settings.values.value, graph.engineStatus.value, emptyList(), null),
    )

    /** Call only once RECORD_AUDIO is granted (MainActivity takes care of that). */
    fun setListening(enabled: Boolean) {
        graph.settings.setListeningEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) ListeningService.start(context) else ListeningService.stop(context)
    }

    fun setSensitivity(value: Float) = graph.settings.setSensitivity(value)

    fun setPauseInBatterySaver(enabled: Boolean) = graph.settings.setPauseInBatterySaver(enabled)

    fun togglePlayback(thought: Thought) =
        player.toggle(thought.id, graph.repository.audioFile(thought))

    override fun onCleared() = player.stop()
}
