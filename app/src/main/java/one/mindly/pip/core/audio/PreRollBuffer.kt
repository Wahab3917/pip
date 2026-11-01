package one.mindly.pip.core.audio

/**
 * Remembers the last few chunks of audio. The wake word is detected a moment AFTER you
 * finish saying it, so if you say "Hey Pip buy milk" in one breath, "buy" may already be
 * spoken by the time capture starts. Prepending this short tail means it's never cut off.
 */
class PreRollBuffer(private val maxChunks: Int) {
    private val chunks = ArrayDeque<ShortArray>()

    fun add(chunk: ShortArray) {
        chunks.addLast(chunk)
        while (chunks.size > maxChunks) chunks.removeFirst()
    }

    /** Returns everything buffered (oldest first) and empties the buffer. */
    fun drain(): ShortArray {
        val all = ShortArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(all, offset)
            offset += chunk.size
        }
        chunks.clear()
        return all
    }

    fun clear() = chunks.clear()
}
