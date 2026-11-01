package one.mindly.pip.core.capture

/**
 * The rules for one capture, as a small state machine with no Android or model code,
 * so it can be unit-tested with fake "speech / no speech" input.
 *
 * Feed it each chunk plus whether the VAD heard speech in it; it answers:
 * - [Outcome.Continue]: keep recording.
 * - [Outcome.Missed]: no speech began within [CaptureConfig.noSpeechTimeoutMs]; save nothing.
 * - [Outcome.Caught]: speech happened and then [CaptureConfig.endSilenceMs] of silence
 *   (or the hard cap) was reached; here is the full recording.
 */
class CaptureSession(
    private val config: CaptureConfig = CaptureConfig(),
    preRoll: ShortArray = ShortArray(0),
) {
    sealed interface Outcome {
        data object Continue : Outcome
        data object Missed : Outcome
        class Caught(val pcm: ShortArray) : Outcome
    }

    private val chunks = mutableListOf(preRoll)
    private var samplesSinceStart = 0L
    private var samplesSinceSpeech = 0L
    private var speechStarted = false

    fun onChunk(pcm: ShortArray, isSpeech: Boolean): Outcome {
        chunks += pcm
        samplesSinceStart += pcm.size

        if (isSpeech) {
            speechStarted = true
            samplesSinceSpeech = 0
        } else {
            samplesSinceSpeech += pcm.size
        }

        return when {
            !speechStarted && samplesSinceStart.ms >= config.noSpeechTimeoutMs -> Outcome.Missed
            speechStarted && samplesSinceSpeech.ms >= config.endSilenceMs -> caught()
            samplesSinceStart.ms >= config.maxDurationMs -> caught()
            else -> Outcome.Continue
        }
    }

    /**
     * Capture was interrupted (mic error, service stopped). If any speech was heard we keep it,
     * because a partial thought beats a lost one.
     */
    fun finishEarly(): Outcome = if (speechStarted) caught() else Outcome.Missed

    private fun caught(): Outcome.Caught {
        val all = ShortArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(all, offset)
            offset += chunk.size
        }
        return Outcome.Caught(all)
    }

    private val Long.ms: Long get() = this * 1000 / config.sampleRate
}

data class CaptureConfig(
    val sampleRate: Int = 16_000,
    /** Silence after speech that ends the note. */
    val endSilenceMs: Long = 2_000,
    /** If no speech starts within this time after the wake word, play "missed". */
    val noSpeechTimeoutMs: Long = 4_000,
    /** Hard cap for a single note. */
    val maxDurationMs: Long = 120_000,
)
