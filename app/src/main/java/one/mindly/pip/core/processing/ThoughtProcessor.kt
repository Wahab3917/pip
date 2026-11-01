package one.mindly.pip.core.processing

import android.util.Log
import one.mindly.pip.core.PIP_LOG_TAG
import java.util.UUID

/**
 * The "intelligence later" seam: capture hands each new thought's id to this and moves on.
 * Phase 2 replaces [LogOnlyProcessor] with a WorkManager job (transcribe → categorise → reminder).
 */
fun interface ThoughtProcessor {
    fun enqueue(thoughtId: UUID)
}

/** Phase 1 stand-in: just records that processing would happen now. */
class LogOnlyProcessor : ThoughtProcessor {
    override fun enqueue(thoughtId: UUID) {
        Log.i(PIP_LOG_TAG, "[Processing] $thoughtId queued (transcription arrives in Phase 2)")
    }
}
