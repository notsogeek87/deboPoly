package com.lielu.debopoly

import android.content.Context
import android.os.SystemClock
import com.lielu.debopoly.data.settings.AppSettings
import com.lielu.debopoly.data.settings.SettingsRepository
import com.lielu.debopoly.data.settings.SettingsRepositoryImpl
import com.lielu.debopoly.data.settings.settingsDataStore
import com.lielu.debopoly.download.DownloadStore
import com.lielu.debopoly.security.AppLock
import com.lielu.githubupdater.UpdateConfig
import com.lielu.githubupdater.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Conteneur de dépendances écrit à la main (convention du modèle lieluinit) : l'app est petite et mono-module.
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext.settingsDataStore)

    /**
     * Réglages courants, lisibles sans attendre. Tant que le DataStore n'a pas répondu, ce sont les valeurs par
     * défaut, qui sont les plus protectrices (FLAG_SECURE actif, verrouillage à 5 min).
     */
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(applicationScope, SharingStarted.Eagerly, AppSettings())

    val appLock =
        AppLock(
            timeoutMillis = { settings.value.lockTimeoutMinutes * 60_000L },
            clock = SystemClock::elapsedRealtime,
        )

    /** Documents téléchargés depuis Lexis Poly : stockage privé de l'app, jamais sauvegardé ni partagé. */
    val downloadStore = DownloadStore(appContext)

    /** Mise à jour de l'app depuis les releases GitHub de CE dépôt (bibliothèque lielugit-updater du modèle). */
    val updateManager =
        UpdateManager(
            appContext,
            UpdateConfig(
                githubOwner = BuildConfig.GITHUB_REPOSITORY.substringBefore('/'),
                githubRepository = BuildConfig.GITHUB_REPOSITORY.substringAfter('/'),
                checkIntervalHours = 1,
            ),
        )

    /** Seul l'APK release `production` peut être remplacé par l'APK de la release `main` (même applicationId). */
    val updatesEnabled: Boolean = !BuildConfig.DEBUG && BuildConfig.FLAVOR == "production"
}
