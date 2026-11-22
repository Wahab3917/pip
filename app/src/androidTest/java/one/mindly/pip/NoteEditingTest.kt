package one.mindly.pip

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import one.mindly.pip.core.storage.ThoughtStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Editing a transcript and deleting thoughts, on the app's real database (it cleans up after itself). */
@RunWith(AndroidJUnit4::class)
class NoteEditingTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = app.appGraph.repository

    @Test
    fun editThenDelete() = runBlocking<Unit> {
        // One second of silence: transcription finishes quickly with no words.
        val thought = repository.saveCaught(ShortArray(16_000), 16_000)
        val audio = File(app.filesDir, thought.audioPath)
        try {
            withTimeout(300_000) {
                repository.observe(thought.id).filterNotNull().first { it.status == ThoughtStatus.READY || it.status == ThoughtStatus.FAILED }
            }

            repository.editTranscript(thought.id, "  Buy oat milk.  ")
            val edited = repository.observe(thought.id).filterNotNull().first()
            assertEquals("Buy oat milk.", edited.transcript)
            assertEquals(ThoughtStatus.READY, edited.status)

            repository.delete(edited)
            assertNull(repository.observe(thought.id).first())
            assertFalse(audio.exists())
        } finally {
            repository.delete(thought) // No-op if the test got that far.
        }
    }

    @Test
    fun deleteSeveralAtOnce() = runBlocking<Unit> {
        val thoughts = List(3) { repository.saveCaught(ShortArray(16_000), 16_000) }
        try {
            repository.deleteAll(thoughts)
            for (thought in thoughts) {
                assertNull(repository.observe(thought.id).first())
                assertFalse(File(app.filesDir, thought.audioPath).exists())
            }
        } finally {
            repository.deleteAll(thoughts)
        }
    }
}
