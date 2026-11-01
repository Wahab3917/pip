package one.mindly.pip.core.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.WavFile
import one.mindly.pip.core.processing.ThoughtProcessor
import java.io.File
import java.time.Instant
import java.util.UUID

/** The one place that creates and reads thoughts. Screens and the service go through here. */
class ThoughtRepository(
    private val dao: ThoughtDao,
    private val filesDir: File,
    private val processor: ThoughtProcessor,
) {
    val thoughts: Flow<List<Thought>> = dao.observeAll()

    /**
     * Saves a freshly captured recording. The order is the "never lose a thought" rule:
     * 1. write the audio to disk, 2. insert the row, 3. queue the slow, redoable processing.
     */
    suspend fun saveCaught(pcm: ShortArray, sampleRate: Int): Thought = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID()
        val audioPath = "$AUDIO_DIR/$id.wav"
        WavFile.write(File(filesDir, audioPath), pcm, sampleRate)

        val thought = Thought(
            id = id,
            createdAt = Instant.now(),
            audioPath = audioPath,
            durationMs = pcm.size * 1000L / sampleRate,
        )
        dao.insert(thought)
        Log.i(PIP_LOG_TAG, "[Storage] saved thought $id (${thought.durationMs} ms)")

        processor.enqueue(id)
        thought
    }

    fun audioFile(thought: Thought): File = File(filesDir, thought.audioPath)

    private companion object {
        const val AUDIO_DIR = "thoughts"
    }
}
