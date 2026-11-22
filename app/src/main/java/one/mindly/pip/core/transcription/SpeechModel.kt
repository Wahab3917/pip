package one.mindly.pip.core.transcription

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig

/**
 * The speech-to-text model the app ships, and the only file that names its files.
 *
 * Whisper base.en (OpenAI, MIT licence), int8, as packaged by sherpa-onnx: English only,
 * ~160 MB. Chosen with training/compare_asr.py on 36 synthetic notes (7 voices, clean / noisy /
 * echoey): fewest wrong words of the candidates (16% overall, 8% clean, and most of those were
 * spelling: "10" for "ten", "favorite" for "favourite"). Whisper tiny.en was faster but worse,
 * Moonshine tiny much worse in echoey rooms.
 *
 * The files are too big for git: `tools/fetch_speech_model.sh` downloads them into
 * app/src/main/assets/asr/ before building.
 */
internal object SpeechModel {
    private const val DIR = "asr"

    fun config(numThreads: Int) = OfflineModelConfig(
        whisper = OfflineWhisperModelConfig(
            encoder = "$DIR/base.en-encoder.int8.onnx",
            decoder = "$DIR/base.en-decoder.int8.onnx",
            language = "en",
            task = "transcribe",
        ),
        tokens = "$DIR/base.en-tokens.txt",
        modelType = "whisper",
        numThreads = numThreads,
    )
}
