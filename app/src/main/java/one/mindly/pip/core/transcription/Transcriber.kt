package one.mindly.pip.core.transcription

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import one.mindly.pip.core.audio.toFloatSamples
import java.io.Closeable

/**
 * Turns a recording into text, fully offline, with the model named in [SpeechModel].
 *
 * Speech-to-text models work best on pieces of up to ~30 s, but a note can be 2 minutes long.
 * So the note is first cut at its pauses with the same silero VAD the capture uses, and each
 * piece is transcribed on its own; the pieces' text is joined back together.
 *
 * Loading the model takes a moment and a fair bit of memory: create one Transcriber for a
 * batch of notes and close it afterwards (TranscriptionWorker does this).
 */
class Transcriber(private val assets: AssetManager) : Closeable {

    private val recognizer = OfflineRecognizer(
        assets,
        OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            // Up to 4 cores (the A32 has 8). More threads than cores makes it much slower, not faster.
            modelConfig = SpeechModel.config(numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)),
        ),
    )

    /** Returns the raw text (TranscriptCleaner tidies it); "" if there was no speech. */
    fun transcribe(pcm: ShortArray, sampleRate: Int): String {
        require(sampleRate == SAMPLE_RATE) { "Expected $SAMPLE_RATE Hz audio, got $sampleRate" }
        val samples = pcm.toFloatSamples()
        return speechPieces(samples).joinToString(" ") { decode(it) }.trim()
    }

    private fun decode(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    /**
     * Cuts [samples] into pieces of at most [MAX_PIECE_S] at pauses in the speech. Each piece
     * keeps [PAD_S] of audio on both sides, so the first and last sounds of a word aren't cut off.
     */
    private fun speechPieces(samples: FloatArray): List<FloatArray> {
        val vad = Vad(
            assets,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "vad/silero_vad.onnx",
                    threshold = 0.5f,
                    minSpeechDuration = 0.25f,
                    minSilenceDuration = 0.5f,
                    windowSize = VAD_WINDOW,
                    maxSpeechDuration = MAX_PIECE_S,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
            ),
        )
        val ranges = mutableListOf<IntRange>()
        try {
            fun collect() {
                while (!vad.empty()) {
                    val segment = vad.front()
                    val start = segment.start.toInt()
                    ranges += start until start + segment.samples.size
                    vad.pop()
                }
            }
            var offset = 0
            while (offset + VAD_WINDOW <= samples.size) {
                vad.acceptWaveform(samples.copyOfRange(offset, offset + VAD_WINDOW))
                offset += VAD_WINDOW
                collect()
            }
            vad.flush()
            collect()
        } finally {
            vad.release()
        }

        // The capture already heard speech, so if the VAD finds none now, transcribe it all anyway.
        if (ranges.isEmpty()) {
            val maxPiece = (MAX_PIECE_S * SAMPLE_RATE).toInt()
            return (samples.indices step maxPiece).map { samples.copyOfRange(it, minOf(it + maxPiece, samples.size)) }
        }
        val pad = (PAD_S * SAMPLE_RATE).toInt()
        return ranges.map { samples.copyOfRange(maxOf(0, it.first - pad), minOf(samples.size, it.last + 1 + pad)) }
    }

    override fun close() = recognizer.release()

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val VAD_WINDOW = 512
        /** Longest piece handed to the model; comfortably under Whisper's 30 s window. */
        const val MAX_PIECE_S = 20f
        const val PAD_S = 0.25f
    }
}
