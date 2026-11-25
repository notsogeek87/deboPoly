package com.lielu.lexispoly.web

import java.net.URI
import java.util.Locale

/** Ce que l'app fait d'une URL demandée par la page (lien, redirection, `window.open`). */
sealed interface UrlDecision {
    /** HTTPS sur un domaine autorisé : chargé dans la WebView. */
    data object InApp : UrlDecision

    /** HTTP sur un domaine autorisé : rechargé en HTTPS (jamais de contenu en clair dans l'app). */
    data class UpgradeToHttps(
        val httpsUrl: String,
    ) : UrlDecision

    /** Lien web vers un autre domaine : navigateur externe. */
    data object ExternalBrowser : UrlDecision

    /** `mailto:`, `tel:`, `sms:`, `geo:` : app du système correspondante. */
    data object ExternalApp : UrlDecision

    /** `intent:` (liens Android) : traité à part, sans jamais cibler un composant explicite. */
    data object AndroidIntent : UrlDecision

    /** `blob:` / `data:` en navigation de premier niveau : laissé à la WebView (téléchargements). */
    data object LocalContent : UrlDecision

    /** `javascript:`, `file:`, `content:`, schéma inconnu, URL illisible : ignoré. */
    data object Blocked : UrlDecision
}

/**
 * Liste blanche de domaines : un hôte est autorisé s'il EST l'un des domaines ou s'il en est un sous-domaine
 * (`poly.lexisnexis.fr` pour `lexisnexis.fr`) — jamais par simple suffixe (`evil-lexisnexis.fr` est refusé).
 */
class UrlPolicy(
    domains: List<String>,
) {
    private val domains = domains.map { it.trim().trimStart('.').lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

    fun isAllowedHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.trimEnd('.').lowercase(Locale.ROOT)
        return domains.any { h == it || h.endsWith(".$it") }
    }

    /** `true` pour une URL HTTPS dont l'hôte est autorisé. */
    fun isInApp(url: String): Boolean = decide(url) == UrlDecision.InApp

    fun decide(url: String): UrlDecision {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return when (scheme) {
            "https", "http" -> decideWeb(url, scheme)
            "mailto", "tel", "sms", "smsto", "geo" -> UrlDecision.ExternalApp
            "intent" -> UrlDecision.AndroidIntent
            "blob", "data" -> UrlDecision.LocalContent
            else -> UrlDecision.Blocked
        }
    }

    private fun decideWeb(
        url: String,
        scheme: String,
    ): UrlDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return UrlDecision.Blocked
        val host = uri.host ?: return UrlDecision.Blocked
        // Identifiants dans l'URL (https://user@host) : trompe la lecture de l'hôte, refusé.
        if (uri.rawUserInfo != null) return UrlDecision.Blocked
        if (!isAllowedHost(host)) return UrlDecision.ExternalBrowser
        return if (scheme == "https") {
            UrlDecision.InApp
        } else {
            UrlDecision.UpgradeToHttps("https" + url.substring(scheme.length))
        }
    }

    /**
     * Règles d'origine pour `WebViewCompat.addWebMessageListener` / `addDocumentStartJavaScript` :
     * le domaine et ses sous-domaines, en HTTPS uniquement.
     */
    fun originRules(): Set<String> = domains.flatMap { listOf("https://$it", "https://*.$it") }.toSet()
}
