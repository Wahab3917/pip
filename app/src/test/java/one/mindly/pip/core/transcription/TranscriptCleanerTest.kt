package one.mindly.pip.core.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCleanerTest {
    private fun clean(text: String) = TranscriptCleaner.clean(text)

    @Test fun `removes the wake word left at the start`() {
        assertEquals("Buy milk on the way home.", clean("Pip. buy milk on the way home."))
        assertEquals("Buy milk.", clean("Hey Pip, buy milk."))
        assertEquals("Buy milk.", clean(" hey pip   buy milk."))
    }

    @Test fun `keeps pip when it is part of the note`() {
        assertEquals("Pipelines are slow today.", clean("Pipelines are slow today."))
        assertEquals("Pip's vet called.", clean("Pip's vet called."))
    }

    @Test fun `removes non-speech tags and tidies spacing`() {
        assertEquals("Idea for the talk.", clean("[BLANK_AUDIO] idea for  the talk. (wind blowing)"))
    }

    @Test fun `nothing but noise becomes empty`() {
        assertEquals("", clean("[BLANK_AUDIO]"))
        assertEquals("", clean("Pip."))
    }
}
