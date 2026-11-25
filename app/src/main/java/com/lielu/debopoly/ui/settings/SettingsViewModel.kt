package com.lielu.debopoly.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.debopoly.data.settings.AppSettings
import com.lielu.debopoly.data.settings.SettingsRepository
import com.lielu.debopoly.download.DownloadStore
import com.lielu.githubupdater.UpdateInfo
import com.lielu.githubupdater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val downloadStore: DownloadStore,
    private val updateManager: UpdateManager,
    val updatesEnabled: Boolean,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _downloads = MutableStateFlow<List<File>>(emptyList())
    val downloads: StateFlow<List<File>> = _downloads

    val updateState = updateManager.state

    fun refreshDownloads() {
        viewModelScope.launch { _downloads.value = withContext(Dispatchers.IO) { downloadStore.listDownloads() } }
    }

    fun deleteDownload(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { file.delete() }
            refreshDownloads()
        }
    }

    fun setSecureScreen(enabled: Boolean) = launch { settingsRepository.setSecureScreen(enabled) }

    fun setLockTimeout(minutes: Int) = launch { settingsRepository.setLockTimeoutMinutes(minutes) }

    fun setChromeUserAgent(enabled: Boolean) = launch { settingsRepository.setChromeUserAgent(enabled) }

    fun setAlgorithmicDarkening(enabled: Boolean) = launch { settingsRepository.setAlgorithmicDarkening(enabled) }

    fun setDesktopMode(enabled: Boolean) = launch { settingsRepository.setDesktopMode(enabled) }

    fun onCheckUpdateRequested() {
        viewModelScope.launch { runCatching { updateManager.checkForUpdate(force = true) } }
    }

    fun onInstallUpdateRequested(update: UpdateInfo) {
        viewModelScope.launch {
            runCatching {
                val apk = updateManager.downloadUpdate(update)
                install(apk)
            }
        }
    }

    fun onInstallDownloadedRequested(apk: File) {
        runCatching { install(apk) }
    }

    private fun install(apk: File) {
        if (updateManager.canInstallPackages()) {
            updateManager.installUpdate(apk)
        } else {
            updateManager.openInstallPermissionSettings()
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
