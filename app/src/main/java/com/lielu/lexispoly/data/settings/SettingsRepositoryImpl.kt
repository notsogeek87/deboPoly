package com.lielu.lexispoly.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data.map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                secureScreen = prefs[Keys.SECURE_SCREEN] ?: defaults.secureScreen,
                lockTimeoutMinutes = (prefs[Keys.LOCK_TIMEOUT] ?: defaults.lockTimeoutMinutes).coerceIn(0, 24 * 60),
                chromeUserAgent = prefs[Keys.CHROME_UA] ?: defaults.chromeUserAgent,
                algorithmicDarkening = prefs[Keys.ALGO_DARK] ?: defaults.algorithmicDarkening,
                desktopMode = prefs[Keys.DESKTOP] ?: defaults.desktopMode,
                agendaMobile = prefs[Keys.AGENDA_MOBILE] ?: defaults.agendaMobile,
            )
        }

    override suspend fun setSecureScreen(enabled: Boolean) {
        dataStore.edit { it[Keys.SECURE_SCREEN] = enabled }
    }

    override suspend fun setLockTimeoutMinutes(minutes: Int) {
        dataStore.edit { it[Keys.LOCK_TIMEOUT] = minutes.coerceIn(0, 24 * 60) }
    }

    override suspend fun setChromeUserAgent(enabled: Boolean) {
        dataStore.edit { it[Keys.CHROME_UA] = enabled }
    }

    override suspend fun setAlgorithmicDarkening(enabled: Boolean) {
        dataStore.edit { it[Keys.ALGO_DARK] = enabled }
    }

    override suspend fun setDesktopMode(enabled: Boolean) {
        dataStore.edit { it[Keys.DESKTOP] = enabled }
    }

    override suspend fun setAgendaMobile(enabled: Boolean) {
        dataStore.edit { it[Keys.AGENDA_MOBILE] = enabled }
    }

    private object Keys {
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val LOCK_TIMEOUT = intPreferencesKey("lock_timeout_minutes")
        val CHROME_UA = booleanPreferencesKey("chrome_user_agent")
        val ALGO_DARK = booleanPreferencesKey("algorithmic_darkening")
        val DESKTOP = booleanPreferencesKey("desktop_mode")
        val AGENDA_MOBILE = booleanPreferencesKey("agenda_mobile")
    }
}
