package com.lielu.debopoly.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

private const val MAX_REDIRECTS = 5
private const val TIMEOUT_MILLIS = 30_000

internal suspend fun downloadHttpsFile(
    url: String,
    userAgent: String,
    referer: String?,
    destination: File,
    cookiesFor: (String) -> String?,
    connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) {
    var target = parseHttps(url)
    val refererOrigin = referer?.let { runCatching { parseHttps(it).origin() }.getOrNull() }
    var sendCookies = true
    val coroutine = currentCoroutineContext()
    repeat(MAX_REDIRECTS + 1) { hop ->
        coroutine.ensureActive()
        val connection = connect(target)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("User-Agent", userAgent)
            // Le Referer d'une autre origine n'est pas nécessaire au téléchargement.
            if (refererOrigin == target.origin()) connection.setRequestProperty("Referer", "$refererOrigin/")
            if (sendCookies) {
                cookiesFor(target.toASCIIString())?.let { connection.setRequestProperty("Cookie", it) }
            }

            coroutine.ensureActive()
            when (val status = connection.responseCode) {
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    if (hop == MAX_REDIRECTS || location.isNullOrBlank()) throw IOException("Redirection invalide")
                    val next = parseHttps(location, target)
                    sendCookies = sendCookies && next.origin() == target.origin()
                    target = next
                }
                HttpURLConnection.HTTP_OK -> {
                    coroutine.ensureActive()
                    connection.inputStream.use { input ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                coroutine.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    return
                }
                else -> throw IOException("Téléchargement HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun parseHttps(
    value: String,
    base: URI? = null,
): URI {
    val uri =
        try {
            val parsed = URI(value)
            if (base != null && value.startsWith("?")) {
                URI(base.toASCIIString().substringBefore('#').substringBefore('?') + value)
            } else {
                base?.resolve(parsed) ?: parsed
            }
        } catch (invalid: URISyntaxException) {
            throw IOException("URL de téléchargement invalide", invalid)
        }
    if (!uri.scheme.equals("https", ignoreCase = true) ||
        uri.host == null ||
        uri.rawUserInfo != null ||
        (uri.port != -1 && uri.port !in 1..65535)
    ) {
        throw IOException("Origine de téléchargement invalide")
    }
    return uri
}

private fun URI.origin(): String {
    val normalizedHost = host.lowercase(Locale.ROOT)
    return if (port == -1 || port == 443) "https://$normalizedHost" else "https://$normalizedHost:$port"
}
