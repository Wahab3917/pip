package one.mindly.pip.core.wakeword

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.res.AssetManager
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * Listens for "Hey Pip" with Pip's own trained model (see training/README.md).
 *
 * Three small ONNX models run every 80 ms:
 *   audio ──► mel spectrogram ──► speech embedding (96 numbers) ──► classifier ──► probability 0..1
 * The first two are a frozen, pretrained front-end; the classifier was trained on your voice.
 *
 * The streaming rules below MUST match training/pipww/features.py exactly; the instrumented test
 * WakeWordDetectorTest checks this against scores computed in Python:
 *   - audio is handled in chunks of 1280 samples, as int16 values stored in floats (not / 32768);
 *   - the mel model sees the last 480 + 1280 samples (history starts as zeros);
 *   - its 8 new frames, transformed with x / 10 + 2, go into a 76-row mel buffer that starts as 1.0;
 *   - the embedding model turns the last 76 mel rows into one embedding;
 *   - the classifier scores the last 16 embeddings (~1.3 s).
 *
 * Not thread-safe: call it from one coroutine/thread at a time.
 */
class WakeWordDetector(assets: AssetManager, var threshold: Float) : Closeable {

    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(1) // One core is plenty, and kinder to the battery.
        setInterOpNumThreads(1)
    }
    private val melModel = env.createSession(assets.readBytes("wake/melspectrogram.onnx"), options)
    private val embeddingModel = env.createSession(assets.readBytes("wake/embedding_model.onnx"), options)
    private val classifier = env.createSession(assets.readBytes("wake/hey_pip.onnx"), options)

    private val pending = FloatArray(CHUNK)
    private var pendingCount = 0
    private val history = FloatArray(CONTEXT + CHUNK)
    private val mel = FloatArray(MEL_WINDOW * MEL_BINS)
    private val embeddings = ArrayDeque<FloatArray>()

    /** Average time to process one 80 ms chunk, in ms, since the last [reset] (for logs; must stay well under 80). */
    val averageChunkMs: Float get() = if (chunksProcessed == 0) 0f else totalNanos / chunksProcessed / 1e6f
    private var chunksProcessed = 0
    private var totalNanos = 0L

    /** The most recent classifier probability (handy for logs and tests); -1 until available. */
    var lastScore = -1f
        private set

    /** The newest 96-number embedding (a copy), or null before the first chunk. Used by tests. */
    val latestEmbedding: FloatArray? get() = embeddings.lastOrNull()?.copyOf()

    init {
        reset()
    }

    /** Feeds any amount of 16-bit audio. Returns true if "Hey Pip" was heard in it. */
    fun accept(pcm: ShortArray): Boolean {
        var heard = false
        for (sample in pcm) {
            pending[pendingCount++] = sample.toFloat()
            if (pendingCount == CHUNK) {
                pendingCount = 0
                val started = System.nanoTime()
                val score = processChunk()
                totalNanos += System.nanoTime() - started
                chunksProcessed++
                if (score >= threshold) heard = true
            }
        }
        return heard
    }

    /** Forget all recent audio (call after a capture, so the same words can't trigger twice). */
    fun reset() {
        pendingCount = 0
        history.fill(0f)
        mel.fill(1f)
        embeddings.clear()
        lastScore = -1f
    }

    override fun close() {
        classifier.close()
        embeddingModel.close()
        melModel.close()
        options.close()
    }

    /** Runs the three models for one 80 ms chunk. Returns the probability, or -1 while warming up. */
    private fun processChunk(): Float {
        // Slide the history left by one chunk and append the new audio.
        System.arraycopy(history, CHUNK, history, 0, CONTEXT)
        System.arraycopy(pending, 0, history, CONTEXT, CHUNK)

        val frames = run(melModel, history, longArrayOf(1, history.size.toLong()))
        // Slide the mel buffer up by the new frames and append them, transformed.
        val newRows = frames.size / MEL_BINS
        System.arraycopy(mel, newRows * MEL_BINS, mel, 0, (MEL_WINDOW - newRows) * MEL_BINS)
        for (i in frames.indices) mel[(MEL_WINDOW - newRows) * MEL_BINS + i] = frames[i] / 10f + 2f

        embeddings.addLast(run(embeddingModel, mel, longArrayOf(1, MEL_WINDOW.toLong(), MEL_BINS.toLong(), 1)))
        if (embeddings.size > N_EMBEDDINGS) embeddings.removeFirst()
        if (embeddings.size < N_EMBEDDINGS) return -1f

        val window = FloatArray(N_EMBEDDINGS * EMBEDDING_DIM)
        embeddings.forEachIndexed { i, e -> e.copyInto(window, i * EMBEDDING_DIM) }
        lastScore = run(classifier, window, longArrayOf(1, N_EMBEDDINGS.toLong(), EMBEDDING_DIM.toLong()))[0]
        return lastScore
    }

    /** Runs a single-input, single-output model and returns its output as a flat array. */
    private fun run(session: OrtSession, input: FloatArray, shape: LongArray): FloatArray {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val out = (result[0] as OnnxTensor).floatBuffer
                return FloatArray(out.remaining()).also { out.get(it) }
            }
        }
    }

    private fun AssetManager.readBytes(path: String): ByteArray = open(path).use { it.readBytes() }

    private companion object {
        const val CHUNK = 1280
        const val CONTEXT = 480
        const val MEL_WINDOW = 76
        const val MEL_BINS = 32
        const val EMBEDDING_DIM = 96
        const val N_EMBEDDINGS = 16
    }
}
