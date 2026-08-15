package io.github.brandonscollins.yarn.work

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamingTest {
    @Test
    fun `index is zero-padded and extension comes from the part path`() {
        assertEquals(
            "01 - Chapter One.m4b",
            downloadDisplayName(1, "Chapter One", "/library/parts/12/34/file.m4b"),
        )
    }

    @Test
    fun `missing or bogus extension defaults to mp3`() {
        assertEquals("07 - Intro.mp3", downloadDisplayName(7, "Intro", "/library/parts/12/34"))
        // A dot earlier in the path must not smuggle slashes into the extension.
        assertEquals("07 - Intro.mp3", downloadDisplayName(7, "Intro", "/lib.rary/parts/file"))
    }

    @Test
    fun `path-hostile characters are replaced, never honoured`() {
        assertEquals("Who_ Me_", sanitizeForFileName("""Who? Me:"""))
        assertEquals("a_b_c", sanitizeForFileName("""a/b\c"""))
    }

    @Test
    fun `blank title still yields a usable name`() {
        assertEquals("Untitled", sanitizeForFileName("   "))
    }
}
