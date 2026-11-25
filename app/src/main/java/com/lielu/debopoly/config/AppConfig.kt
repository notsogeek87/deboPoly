package com.lielu.debopoly.config

/**
 * Configuration de l'app : c'est le seul fichier à modifier pour pointer vers l'instance Lexis Poly du cabinet.
 * Voir README, « Configuration ».
 */
object AppConfig {
    /**
     * Page Lexis Poly chargée au lancement (elle redirige vers la connexion `authcore.lexispoly.fr` si besoin).
     * Doit commencer par `https://` et appartenir à [ALLOWED_DOMAINS] ; sinon l'app affiche un écran
     * « Configuration incomplète ».
     */
    const val BASE_URL = "https://www.lexispoly.fr/lexispoly/"

    /**
     * Domaines (et tous leurs sous-domaines) ouverts DANS l'app. Tout autre lien part dans le navigateur.
     * `lexispoly.fr` couvre `www.lexispoly.fr` (l'application) et `authcore.lexispoly.fr` (la connexion).
     */
    val ALLOWED_DOMAINS: List<String> =
        listOf(
            "lexispoly.fr",
            "lexisnexis.fr",
            "lexisnexis.com",
        )

    /**
     * Fournisseurs d'identité à garder dans l'app si la connexion du cabinet passe par un SSO externe (sinon
     * la page de connexion s'ouvrirait dans le navigateur et la session n'arriverait jamais dans l'app).
     * Vide par défaut : n'ajouter que celui réellement utilisé, par exemple :
     *   "login.microsoftonline.com", "login.live.com"   (Microsoft Entra ID / Microsoft 365)
     *   "accounts.google.com"                           (Google Workspace)
     *   "okta.com"                                      (Okta)
     */
    val SSO_DOMAINS: List<String> = emptyList()

    /** Délai par défaut avant de redemander l'empreinte / le code au retour dans l'app. */
    const val DEFAULT_LOCK_TIMEOUT_MINUTES = 5

    /** Choix proposés dans les réglages (0 = à chaque retour dans l'app). */
    val LOCK_TIMEOUT_CHOICES: List<Int> = listOf(0, 1, 5, 15, 30)

    /** Tous les domaines autorisés dans la WebView. */
    val inAppDomains: List<String> get() = ALLOWED_DOMAINS + SSO_DOMAINS
}
