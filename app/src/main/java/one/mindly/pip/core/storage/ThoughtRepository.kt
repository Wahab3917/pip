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

    fun observe(id: UUID): Flow<Thought?> = dao.observe(id)

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

    /** The user corrected the transcript. Also how a FAILED note gets text by hand. */
    suspend fun editTranscript(id: UUID, text: String) = dao.setTranscript(id, text.trim())

    /**
     * Deletes a thought for good: the row first (so it leaves the list at once), then the audio.
     * If the background job is transcribing it right now, its later update simply finds no row.
     */
    suspend fun delete(thought: Thought) = withContext(Dispatchers.IO) {
        dao.delete(thought.id)
        val file = audioFile(thought)
        if (!file.delete() && file.exists()) Log.w(PIP_LOG_TAG, "[Storage] couldn't delete ${file.name}")
        Log.i(PIP_LOG_TAG, "[Storage] deleted thought ${thought.id}")
    }

    /** Bulk delete from the list's selection mode; same rules as [delete], one thought at a time. */
    suspend fun deleteAll(thoughts: Collection<Thought>) = thoughts.forEach { delete(it) }

    /** "Try again" for a failed transcript: put it back in the queue. */
    suspend fun retryTranscription(id: UUID) {
        dao.setStatus(id, ThoughtStatus.CAUGHT)
        processor.enqueue(id)
    }

    // --- Used by TranscriptionWorker ---------------------------------------------------------

    suspend fun requeueInterrupted() = dao.requeueInterrupted()

    /** Claims the oldest untranscribed thought (marks it TRANSCRIBING), or null when none are left. */
    suspend fun claimNextToTranscribe(): Thought? =
        dao.nextToTranscribe()?.also { dao.setStatus(it.id, ThoughtStatus.TRANSCRIBING) }

    suspend fun saveTranscript(id: UUID, transcript: String) = dao.setTranscript(id, transcript)

    suspend fun markFailed(id: UUID) = dao.setStatus(id, ThoughtStatus.FAILED)

    private companion object {
        const val AUDIO_DIR = "thoughts"
    }
}
