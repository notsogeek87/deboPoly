package com.lielu.lexispoly.web

/**
 * Transforme le user-agent par défaut de la WebView en celui de Chrome mobile : certains sites refusent les
 * WebView, qu'ils reconnaissent au marqueur `; wv` et au jeton `Version/4.0`.
 *
 * Avant : `Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko)
 * Version/4.0 Chrome/131.0.6778.39 Mobile Safari/537.36`
 * Après : `Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)
 * Chrome/131.0.6778.39 Mobile Safari/537.36`
 */
object UserAgents {
    private val WV_MARKER = Regex(""";\s*wv\)""")
    private val BUILD_TOKEN = Regex("""\s+Build/[^;)]*""")
    private val VERSION_TOKEN = Regex("""\s+Version/\d+(\.\d+)*""")

    fun chromeLike(webViewUserAgent: String): String =
        webViewUserAgent
            .replace(WV_MARKER, ")")
            .replace(BUILD_TOKEN, "")
            .replace(VERSION_TOKEN, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    /** User-agent de Chrome pour ordinateur (Linux), même version de moteur que la WebView. */
    fun desktop(webViewUserAgent: String): String {
        val version = Regex("""Chrome/([\d.]+)""").find(webViewUserAgent)?.groupValues?.get(1) ?: "131.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }

    /** Largeur de page imposée en version ordinateur. */
    const val DESKTOP_WIDTH = 1280

    /**
     * Remplace la balise viewport (souvent `width=device-width`, qui force la mise en page mobile) par une
     * largeur d'ordinateur. Rejoué au chargement du DOM et à la fin du chargement, la balise étant dans le HTML.
     */
    val DESKTOP_VIEWPORT_SCRIPT: String =
        "(function(){function f(){var h=document.head||document.documentElement;if(!h)return;" +
            "var m=document.querySelector('meta[name=viewport]');" +
            "if(!m){m=document.createElement('meta');m.name='viewport';h.appendChild(m);}" +
            "var c='width=$DESKTOP_WIDTH';if(m.content!==c)m.content=c;}" +
            "f();document.addEventListener('DOMContentLoaded',f);window.addEventListener('load',f);})();"
}
