package one.mindly.pip

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.WavFile
import one.mindly.pip.core.storage.ThoughtStatus
import one.mindly.pip.core.transcription.TranscriptCleaner
import one.mindly.pip.core.transcription.Transcriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Offline transcription on a real device. The reference note (androidTest/assets/note_reference.wav)
 * is a synthetic voice with background noise saying "Remember to email Sarah about the budget
 * before Friday." (made by training/compare_asr.py --make-test).
 * Run on a device or emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class TranscriptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val reference = WavFile.decode(instrumentation.context.assets.open("note_reference.wav").readBytes())

    @Test
    fun transcriberHearsTheNote() {
        val started = SystemClock.elapsedRealtime()
        Transcriber(app.assets).use { transcriber ->
            val loaded = SystemClock.elapsedRealtime()
            val text = TranscriptCleaner.clean(transcriber.transcribe(reference.pcm, reference.sampleRate))
            val first = SystemClock.elapsedRealtime()
            transcriber.transcribe(reference.pcm, reference.sampleRate)
            val second = SystemClock.elapsedRealtime()
            Log.i(
                PIP_LOG_TAG,
                "[Test] ${reference.pcm.size / 16} ms of audio: model loaded in ${loaded - started} ms, " +
                    "transcribed in ${first - loaded} ms (again: ${second - first} ms): $text",
            )
            assertKeyWords(text)
        }
    }

    /** The whole path: save like a capture does → background job → transcript in the database. */
    @Test
    fun savedThoughtGetsTranscribed() = runBlocking {
        val repository = app.appGraph.repository
        val thought = repository.saveCaught(reference.pcm, reference.sampleRate)
        try {
            // Generous: the first run loads the model, and emulators can be very slow.
            val done = withTimeout(300_000) {
                repository.observe(thought.id).filterNotNull()
                    .first { it.status == ThoughtStatus.READY || it.status == ThoughtStatus.FAILED }
            }
            assertEquals(ThoughtStatus.READY, done.status)
            assertKeyWords(done.transcript.orEmpty())
        } finally {
            // Leave no test note behind in the app's real list.
            app.appGraph.database.openHelper.writableDatabase
                .execSQL("DELETE FROM thoughts WHERE id = ?", arrayOf(thought.id.toString()))
            File(app.filesDir, thought.audioPath).delete()
        }
    }

    private fun assertKeyWords(text: String) {
        val lower = text.lowercase()
        for (word in listOf("email", "sarah", "budget", "friday")) {
            assertTrue("\"$word\" missing from: $text", word in lower)
        }
    }
}
