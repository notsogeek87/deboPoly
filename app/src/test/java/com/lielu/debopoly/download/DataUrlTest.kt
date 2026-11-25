package com.lielu.debopoly.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataUrlTest {
    @Test
    fun base64() {
        // Contient « + » et « / » : ne doit pas être pris pour un espace.
        val d = DataUrl.parse("data:application/pdf;base64,+/8A")!!
        assertEquals("application/pdf", d.mimeType)
        assertArrayEquals(byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0x00), d.bytes)
    }

    @Test
    fun `texte pourcent-encode`() {
        val d = DataUrl.parse("data:text/csv;charset=utf-8,a%2Cb%0A%C3%A9")!!
        assertEquals("text/csv", d.mimeType)
        assertEquals("a,b\né", String(d.bytes, Charsets.UTF_8))
    }

    @Test
    fun `type par defaut et invalides`() {
        assertEquals("text/plain", DataUrl.parse("data:,x")!!.mimeType)
        assertNull(DataUrl.parse("data:text/plain"))
        assertNull(DataUrl.parse("https://x"))
    }
}
