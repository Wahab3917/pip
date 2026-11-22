package one.mindly.pip.core.transcription

/**
 * Tidies the raw speech-to-text output into the note the user sees. Pure text rules,
 * no Android, so they're unit-tested (TranscriptCleanerTest).
 *
 * - Drops the tail of the wake word: a "Hey Pip" recording keeps 0.5 s from just before the
 *   wake word fired (see PreRollBuffer), so the text can start with "Pip." or "Hey Pip,".
 * - Drops the tags models write for non-speech, like "[BLANK_AUDIO]" or "(wind blowing)".
 * - Collapses whitespace and starts the note with a capital letter.
 */
object TranscriptCleaner {

    private val nonSpeechTags = Regex("""\[[^\]]*]|\([^)]*\)""")
    private val leadingWakeWord = Regex("""^(hey[\s,.!?-]+)?pip(?=[\s,.!?-]|$)[\s,.!?-]*""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")

    fun clean(raw: String): String {
        var text = raw.replace(nonSpeechTags, " ").replace(whitespace, " ").trim()
        text = text.replaceFirst(leadingWakeWord, "").trim()
        return text.replaceFirstChar { it.uppercaseChar() }
    }
}
