package one.mindly.pip.core.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/** Plays one recording at a time. [playing] holds the id of the thought playing, or null. */
class ThoughtPlayer {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<UUID?>(null)
    val playing: StateFlow<UUID?> = _playing

    fun toggle(id: UUID, file: File) {
        val wasPlaying = _playing.value == id
        stop()
        if (wasPlaying) return
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { stop() }
            prepare() // Local, small files: preparing synchronously is fine.
            start()
        }
        _playing.value = id
    }

    fun stop() {
        player?.release()
        player = null
        _playing.value = null
    }
}
