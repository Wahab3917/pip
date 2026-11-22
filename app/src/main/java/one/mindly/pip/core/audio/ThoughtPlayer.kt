package one.mindly.pip.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/**
 * Plays one recording at a time, with pause and seek.
 * [state] says which thought is loaded and whether it's playing; the position changes too
 * often for a flow, so the screen asks for [positionMs] while it's playing.
 */
class ThoughtPlayer {
    data class State(val id: UUID? = null, val isPlaying: Boolean = false, val durationMs: Int = 0)

    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Play [id]; if it's already loaded, pause or resume it instead. */
    fun toggle(id: UUID, file: File) {
        val current = player
        if (current != null && _state.value.id == id) {
            if (current.isPlaying) current.pause() else current.start()
            _state.value = _state.value.copy(isPlaying = current.isPlaying)
            return
        }
        load(id, file).start()
        _state.value = _state.value.copy(isPlaying = true)
    }

    /** Jump to [ms] in [id], loading it (paused) if another recording was loaded. */
    fun seekTo(id: UUID, file: File, ms: Int) {
        val target = if (_state.value.id == id) player ?: load(id, file) else load(id, file)
        target.seekTo(ms)
    }

    fun positionMs(): Int = player?.currentPosition ?: 0

    fun stop() {
        player?.release()
        player = null
        _state.value = State()
    }

    private fun load(id: UUID, file: File): MediaPlayer {
        stop()
        return MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                it.seekTo(0)
                _state.value = _state.value.copy(isPlaying = false)
            }
            prepare() // Local, small files: preparing synchronously is fine.
            player = this
            _state.value = State(id = id, isPlaying = false, durationMs = duration)
        }
    }
}
