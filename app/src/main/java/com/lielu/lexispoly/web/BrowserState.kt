package com.lielu.lexispoly.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Erreur de chargement de la page principale, affichée par-dessus la WebView avec un bouton Réessayer. */
sealed interface PageError {
    /** Pas de réseau, ou serveur injoignable. */
    data object Offline : PageError

    /** Le réseau répond mais la page n'a pas pu être chargée. */
    data class Unreachable(
        val detail: String,
    ) : PageError

    /**
     * La page a redirigé vers un domaine hors liste blanche, ouvert dans le navigateur. Typiquement un
     * fournisseur SSO à ajouter à `AppConfig.SSO_DOMAINS`.
     */
    data class LeftApp(
        val host: String,
    ) : PageError

    /** Certificat refusé (la connexion est annulée, jamais poursuivie). */
    data object Insecure : PageError
}

/** État observable par Compose : progression, erreur. */
class BrowserState {
    var progress by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<PageError?>(null)
}
