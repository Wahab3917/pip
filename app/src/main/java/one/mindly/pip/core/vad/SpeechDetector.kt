package one.mindly.pip.core.vad

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import one.mindly.pip.core.audio.Microphone
import java.io.Closeable

/**
 * Answers one question per audio chunk: "is someone speaking right now?"
 * Uses the silero VAD (voice activity detection) model through sherpa-onnx.
 *
 * The "stop after 2 s of silence" rule lives in CaptureSession, not here. This class only
 * reports speech/no-speech, with a short 0.25 s smoothing so tiny gaps between words
 * don't count as silence.
 */
class SpeechDetector(assets: AssetManager) : Closeable {

    private val vad = Vad(
        assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "vad/silero_vad.onnx",
                threshold = 0.5f,          // Speech probability needed to count as speech.
                minSpeechDuration = 0.25f, // Ignore blips shorter than this.
                minSilenceDuration = 0.25f,
                windowSize = 512,          // What silero expects at 16 kHz.
                maxSpeechDuration = 150f,  // Longer than our 2-minute cap, so it never splits a note.
            ),
            sampleRate = Microphone.SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    fun isSpeech(samples: FloatArray): Boolean {
        vad.acceptWaveform(samples)
        val speaking = vad.isSpeechDetected()
        // The VAD also collects finished speech segments; we don't use them, so drop them.
        while (!vad.empty()) vad.pop()
        return speaking
    }

    fun reset() = vad.reset()

    override fun close() = vad.release()
}
