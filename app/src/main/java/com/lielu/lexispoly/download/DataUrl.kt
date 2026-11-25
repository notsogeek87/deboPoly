package com.lielu.lexispoly.download

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Contenu d'une URL `data:` (RFC 2397). */
class DataUrl(
    val mimeType: String,
    val bytes: ByteArray,
) {
    companion object {
        /** `null` si [url] n'est pas une URL `data:` valide. */
        fun parse(url: String): DataUrl? {
            if (!url.regionMatches(0, "data:", 0, 5, ignoreCase = true)) return null
            val comma = url.indexOf(',')
            if (comma < 0) return null
            val meta = url.substring(5, comma)
            val payload = url.substring(comma + 1)
            val parts = meta.split(';')
            val isBase64 = parts.drop(1).any { it.equals("base64", ignoreCase = true) }
            val mime = parts.first().trim().ifEmpty { "text/plain" }
            val bytes =
                runCatching {
                    // « + » est un caractère base64 : seul le pourcent-encodage est décodé, jamais « + » → espace.
                    // ISO-8859-1 : chaque %XX redevient exactement un octet (contenu binaire préservé).
                    val latin1 = StandardCharsets.ISO_8859_1
                    val decoded = URLDecoder.decode(payload.replace("+", "%2B"), latin1.name())
                    if (isBase64) Base64.getMimeDecoder().decode(decoded) else decoded.toByteArray(latin1)
                }.getOrNull() ?: return null
            return DataUrl(mime, bytes)
        }
    }
}
