package com.lielu.debopoly.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

class HttpsDownloadTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun cookiesStayOnInitialOriginAndRefererContainsOnlyOrigin() {
        val targets = mutableListOf<URI>()
        val cookies = mutableListOf<String>()
        val first = "https://docs.example.test/start?initial=1"
        val sameOrigin = "https://docs.example.test/start?next=2"
        val connections =
            listOf(
                Response(first, 302, "?next=2"),
                Response(sameOrigin, 307, "https://docs.example.test:8443/file"),
                Response("https://docs.example.test:8443/file", 308, "https://docs.example.test/result"),
                Response("https://docs.example.test/result", 200),
            )
        val destination = folder.newFile()

        runBlocking {
            downloadHttpsFile(
                first,
                "test-agent",
                "https://docs.example.test/private?secret=1",
                destination,
                { url ->
                    cookies.add(url)
                    "session=test"
                },
            ) { target ->
                targets.add(target)
                connections[targets.lastIndex]
            }
        }

        assertEquals(connections.map { it.url.toURI() }, targets)
        assertEquals(listOf(first, sameOrigin), cookies)
        assertEquals(
            listOf("session=test", "session=test", null, null),
            connections.map { it.getRequestProperty("Cookie") },
        )
        assertEquals(
            listOf("https://docs.example.test/", "https://docs.example.test/", null, "https://docs.example.test/"),
            connections.map { it.getRequestProperty("Referer") },
        )
        connections.forEach { assertFalse(it.instanceFollowRedirects) }
        assertEquals("document", destination.readText())
    }

    @Test
    fun rejectsNonHttpsRedirectBeforeOpeningIt() {
        val initialUrl = "https://docs.example.test/start"
        val connection = Response(initialUrl, 302, "http://docs.example.test/result")
        val destination = folder.newFile()
        val targets = mutableListOf<URI>()

        assertThrows(IOException::class.java) {
            runBlocking {
                downloadHttpsFile(
                    initialUrl,
                    "test-agent",
                    "https://other.example.test/private",
                    destination,
                    { null },
                ) {
                    targets.add(it)
                    connection
                }
            }
        }

        assertEquals(listOf(URI(initialUrl)), targets)
        assertNull(connection.getRequestProperty("Referer"))
        assertEquals(0L, destination.length())
    }

    private class Response(
        url: String,
        private val status: Int,
        private val location: String? = null,
    ) : HttpURLConnection(URI(url).toURL()) {
        override fun getResponseCode(): Int = status

        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null

        override fun getInputStream() = "document".byteInputStream()

        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false
    }
}
