package com.lielu.debopoly.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {
    @Test
    fun `content-disposition RFC 5987 prioritaire`() {
        assertEquals(
            "Conclusions récap.pdf",
            FileNames.fromContentDisposition(
                "attachment; filename=\"Conclusions recap.pdf\"; filename*=UTF-8''Conclusions%20r%C3%A9cap.pdf",
            ),
        )
    }

    @Test
    fun `content-disposition simple, avec ou sans guillemets`() {
        assertEquals("acte.docx", FileNames.fromContentDisposition("attachment; filename=\"acte.docx\""))
        assertEquals("acte.docx", FileNames.fromContentDisposition("inline; filename=acte.docx"))
        assertNull(FileNames.fromContentDisposition("attachment"))
        assertNull(FileNames.fromContentDisposition(null))
    }

    @Test
    fun `nom depuis l url`() {
        assertEquals(
            "note de frais.pdf",
            FileNames.fromUrl("https://poly.lexisnexis.fr/docs/note%20de%20frais.pdf?x=1"),
        )
        assertNull(FileNames.fromUrl("https://poly.lexisnexis.fr/download"))
    }

    @Test
    fun `sanitize neutralise chemins et caracteres interdits`() {
        assertEquals("passwd", FileNames.sanitize("../../etc/passwd", null, "document"))
        assertEquals("a_b_.pdf", FileNames.sanitize("a:b?.pdf", "pdf", "document"))
        assertEquals("document.pdf", FileNames.sanitize("  ..  ", "pdf", "document"))
        assertEquals("rapport.pdf", FileNames.sanitize("rapport", "pdf", "document"))
        val long = FileNames.sanitize("x".repeat(300) + ".pdf", null, "document")
        assertEquals(120, long.length)
        assertEquals(".pdf", long.takeLast(4))
    }

    @Test
    fun `unique numerote les doublons`() {
        val existing = setOf("a.pdf", "a (1).pdf")
        assertEquals("a (2).pdf", FileNames.unique("a.pdf") { it in existing })
        assertEquals("b.pdf", FileNames.unique("b.pdf") { it in existing })
    }
}
