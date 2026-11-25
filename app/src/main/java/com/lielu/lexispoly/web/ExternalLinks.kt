package com.lielu.lexispoly.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import java.net.URISyntaxException

/** Ouverture des liens qui sortent de l'app. */
object ExternalLinks {
    /** Navigateur (ou app associée au lien) : jamais cette app, qui ne déclare aucun lien web. */
    fun openInBrowser(
        context: Context,
        url: String,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        start(context, intent)
    }

    /** `mailto:`, `tel:`, `sms:`, `geo:`. */
    fun openSchemeUrl(
        context: Context,
        url: String,
    ) {
        start(context, Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    /**
     * Lien `intent:` : relu sans composant ni sélecteur explicites (un site ne doit pas pouvoir lancer une
     * activité interne d'une autre app), limité aux activités BROWSABLE ; à défaut, `browser_fallback_url` HTTPS.
     */
    fun openIntentUrl(
        context: Context,
        url: String,
    ) {
        val intent =
            try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                return
            }
        val fallback = intent.getStringExtra("browser_fallback_url")
        intent.component = null
        intent.selector = null
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.flags = 0
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (fallback != null && fallback.startsWith("https://", ignoreCase = true)) {
                openInBrowser(context, fallback)
            }
        } catch (e: SecurityException) {
            // Activité non exportée : ignoré.
        }
    }

    private fun start(
        context: Context,
        intent: Intent,
    ) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aucune application pour ouvrir ce lien", Toast.LENGTH_SHORT).show()
        }
    }
}
