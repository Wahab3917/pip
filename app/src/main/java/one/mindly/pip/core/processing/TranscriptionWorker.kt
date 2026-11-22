package one.mindly.pip.core.processing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import one.mindly.pip.appGraph
import one.mindly.pip.core.PIP_LOG_TAG
import one.mindly.pip.core.audio.WavFile
import one.mindly.pip.core.transcription.TranscriptCleaner
import one.mindly.pip.core.transcription.Transcriber

/**
 * Transcribes every thought that is still waiting, oldest first (see WorkManagerProcessor).
 *
 * The audio file is never touched: if anything goes wrong the thought is marked FAILED and
 * the user can try again from the note screen. The recording itself is always safe.
 */
class TranscriptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val repository = applicationContext.appGraph.repository
        // Only one of these jobs runs at a time, so a TRANSCRIBING row here was cut off by a crash.
        repository.requeueInterrupted()

        var transcriber: Transcriber? = null
        try {
            while (true) {
                val thought = repository.claimNextToTranscribe() ?: break
                try {
                    val started = SystemClock.elapsedRealtime()
                    val model = transcriber ?: Transcriber(applicationContext.assets).also { transcriber = it }
                    val audio = WavFile.read(repository.audioFile(thought))
                    val text = TranscriptCleaner.clean(model.transcribe(audio.pcm, audio.sampleRate))
                    repository.saveTranscript(thought.id, text)
                    Log.i(
                        PIP_LOG_TAG,
                        "[Transcribe] ${thought.id}: ${thought.durationMs} ms of audio in " +
                            "${SystemClock.elapsedRealtime() - started} ms, ${text.length} chars",
                    )
                } catch (e: Exception) {
                    Log.e(PIP_LOG_TAG, "[Transcribe] ${thought.id} failed", e)
                    repository.markFailed(thought.id)
                }
            }
        } finally {
            transcriber?.close()
        }
        Result.success()
    }
}
