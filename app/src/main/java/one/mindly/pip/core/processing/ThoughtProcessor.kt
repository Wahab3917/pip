package one.mindly.pip.core.processing

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import one.mindly.pip.core.PIP_LOG_TAG
import java.util.UUID

/**
 * The "intelligence later" seam: capture hands each new thought's id to this and moves on,
 * so saving never waits for the slow part.
 */
fun interface ThoughtProcessor {
    fun enqueue(thoughtId: UUID)
}

/**
 * Transcribes in the background with WorkManager, which keeps the job queued across app
 * restarts and reboots.
 *
 * There is only ever one transcription job: it works through every thought still waiting
 * (status CAUGHT), oldest first, so it doesn't matter which id asked for it. A request while
 * the job is running is appended after it, so a thought caught mid-run is picked up too.
 * One job at a time also means only one copy of the big model is ever in memory.
 */
class WorkManagerProcessor(private val context: Context) : ThoughtProcessor {
    override fun enqueue(thoughtId: UUID) {
        Log.i(PIP_LOG_TAG, "[Processing] $thoughtId queued for transcription")
        start()
    }

    /** Also called at app start, to finish anything left over (e.g. after a crash). */
    fun start() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<TranscriptionWorker>().build(),
        )
    }

    private companion object {
        const val UNIQUE_NAME = "transcribe"
    }
}
