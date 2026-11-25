package com.lielu.lexispoly.data.settings

import com.lielu.lexispoly.config.AppConfig
import kotlinx.coroutines.flow.Flow

data class AppSettings(
    /** FLAG_SECURE : pas de capture d'écran, aperçu masqué dans les apps récentes. */
    val secureScreen: Boolean = true,
    /** Délai d'inactivité (minutes) avant de redemander l'authentification ; 0 = à chaque retour. */
    val lockTimeoutMinutes: Int = AppConfig.DEFAULT_LOCK_TIMEOUT_MINUTES,
    /** `true` : user-agent de Chrome mobile (sans « ; wv »). `false` : user-agent WebView d'origine. */
    val chromeUserAgent: Boolean = true,
    /** Version ordinateur : user-agent de bureau et page large (1280 px), zoomable. */
    val desktopMode: Boolean = false,
    /** Assombrissement automatique des pages qui n'ont pas de thème sombre (mode sombre du système). */
    val algorithmicDarkening: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setSecureScreen(enabled: Boolean)

    suspend fun setLockTimeoutMinutes(minutes: Int)

    suspend fun setChromeUserAgent(enabled: Boolean)

    suspend fun setAlgorithmicDarkening(enabled: Boolean)

    suspend fun setDesktopMode(enabled: Boolean)
}
