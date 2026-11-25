package com.lielu.debopoly.download

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Nom de fichier d'un téléchargement : Content-Disposition, sinon URL, sinon nom générique + extension. */
object FileNames {
    private const val MAX_LENGTH = 120

    /**
     * Lit `filename*=UTF-8''…` (RFC 5987, prioritaire) puis `filename="…"`. `null` si absent.
     * `URLUtil.guessFileName` d'Android ignore `filename*` et renvoie souvent `downloadfile.bin`.
     */
    fun fromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            val charset = m.groupValues[1].ifBlank { "UTF-8" }
            val decoded =
                runCatching { URLDecoder.decode(m.groupValues[2].trim().replace("+", "%2B"), charset) }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        Regex("""filename\s*=\s*"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            return m.groupValues[1].replace(Regex("""\\(.)"""), "$1").takeIf { it.isNotBlank() }
        }
        Regex("""filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let { m ->
            return m.groupValues[1].trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Dernier segment du chemin de l'URL, s'il ressemble à un nom de fichier (avec extension). */
    fun fromUrl(url: String): String? {
        val path = runCatching { URI(url).rawPath }.getOrNull() ?: return null
        val last = path.substringAfterLast('/')
        if (last.isBlank() || !last.contains('.')) return null
        return runCatching { URLDecoder.decode(last.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }.getOrNull()
    }

    /**
     * Rend un nom sûr pour le système de fichiers : pas de chemin (`../`), pas de caractères interdits,
     * longueur bornée en gardant l'extension, extension ajoutée depuis le type MIME si elle manque.
     */
    fun sanitize(
        raw: String?,
        extensionForMime: String?,
        fallbackBase: String,
    ): String {
        var name =
            (raw ?: "")
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("""[\u0000-\u001f\u007f<>:"|?*]"""), "_")
                .trim()
                .trim('.', ' ')
        if (name.isEmpty()) name = fallbackBase
        val ext = extensionForMime?.lowercase(Locale.ROOT)?.trimStart('.')
        if (!name.contains('.') && !ext.isNullOrBlank()) name = "$name.$ext"
        if (name.length > MAX_LENGTH) {
            val dot = name.lastIndexOf('.')
            val suffix = if (dot > 0 && name.length - dot <= 10) name.substring(dot) else ""
            name = name.take(MAX_LENGTH - suffix.length) + suffix
        }
        return name
    }

    /** `rapport.pdf` → `rapport (1).pdf`, `rapport (2).pdf`… jusqu'à ce que [exists] renvoie `false`. */
    fun unique(
        name: String,
        exists: (String) -> Boolean,
    ): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }
}
