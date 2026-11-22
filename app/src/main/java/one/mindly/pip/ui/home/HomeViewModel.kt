package one.mindly.pip.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    private val player = ThoughtPlayer()

    val uiState: StateFlow<HomeUiState> = combine(
        graph.settings.values,
        graph.engineStatus,
        graph.repository.thoughts,
        ::HomeUiState,
    ).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(graph.settings.values.value, graph.engineStatus.value, emptyList()),
    )

    /** The note sheet's player (shared, so closing a note can stop it). */
    val playerState: StateFlow<ThoughtPlayer.State> = player.state

    /** Voice level while catching, for the capture overlay. */
    val captureLevel: StateFlow<Float> = graph.captureLevel

    /** The seed button. Call only once RECORD_AUDIO is granted; we're in the foreground, so the mic is allowed. */
    fun catchNow() = ListeningService.catchNow(getApplication())

    /** "Tap to finish" in the capture overlay. */
    fun finishCatch() = ListeningService.finishCatch(getApplication())

    /** Call only once RECORD_AUDIO is granted (MainActivity takes care of that). */
    fun setListening(enabled: Boolean) {
        graph.settings.setListeningEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) ListeningService.start(context) else ListeningService.stop(context)
    }

    fun setSensitivity(value: Float) = graph.settings.setSensitivity(value)

    fun setPauseInBatterySaver(enabled: Boolean) = graph.settings.setPauseInBatterySaver(enabled)

    fun thought(id: UUID): Flow<Thought?> = graph.repository.observe(id)

    fun retryTranscription(id: UUID) {
        viewModelScope.launch { graph.repository.retryTranscription(id) }
    }

    fun editTranscript(id: UUID, text: String) {
        viewModelScope.launch { graph.repository.editTranscript(id, text) }
    }

    fun delete(thought: Thought) {
        if (player.state.value.id == thought.id) player.stop()
        viewModelScope.launch { graph.repository.delete(thought) }
    }

    fun deleteAll(thoughts: Collection<Thought>) {
        if (thoughts.any { it.id == player.state.value.id }) player.stop()
        viewModelScope.launch { graph.repository.deleteAll(thoughts) }
    }

    fun togglePlayback(thought: Thought) =
        player.toggle(thought.id, graph.repository.audioFile(thought))

    fun seek(thought: Thought, ms: Int) =
        player.seekTo(thought.id, graph.repository.audioFile(thought), ms)

    fun playbackPositionMs(): Int = player.positionMs()

    fun stopPlayback() = player.stop()

    override fun onCleared() = player.stop()
}
