package com.lielu.lexispoly

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lielu.lexispoly.config.AppConfig
import com.lielu.lexispoly.download.DownloadController
import com.lielu.lexispoly.download.DownloadEvent
import com.lielu.lexispoly.download.FileOpener
import com.lielu.lexispoly.security.BiometricGate
import com.lielu.lexispoly.security.GateResult
import com.lielu.lexispoly.ui.main.LockUi
import com.lielu.lexispoly.ui.main.MainScreen
import com.lielu.lexispoly.ui.settings.SettingsActivity
import com.lielu.lexispoly.ui.theme.AppTheme
import com.lielu.lexispoly.ui.update.AppUpdateViewModel
import com.lielu.lexispoly.ui.update.UpdatePrompt
import com.lielu.lexispoly.web.BrowserController
import com.lielu.lexispoly.web.BrowserState
import com.lielu.lexispoly.web.UrlPolicy
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Écran unique : la WebView Lexis Poly, sous un écran de verrouillage biométrique.
 *
 * Rotation, pliage/dépliage (Galaxy Z Fold), redimensionnement en multi-fenêtre et changement clair/sombre sont
 * déclarés dans `android:configChanges` (manifest) : l'activité n'est PAS recréée, la page n'est pas rechargée
 * et un formulaire en cours de saisie est conservé. En cas de mort du processus, l'historique est restauré.
 */
class MainActivity :
    FragmentActivity(),
    BrowserController.Host {
    private lateinit var container: AppContainer
    private lateinit var gate: BiometricGate
    private var browser: BrowserController? = null
    private val browserState = BrowserState()
    private val snackbarHostState = SnackbarHostState()

    private var lockUi by mutableStateOf<LockUi>(LockUi.Locked())
    private var authInProgress = false

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraFile: File? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onFileChooserResult)

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == SettingsActivity.RESULT_WIPE) wipeData()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant tout affichage : par défaut, pas de capture ni d'aperçu (le réglage peut ensuite le lever).
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = (application as LieluApplication).container
        gate = BiometricGate(this)

        val policy = UrlPolicy(AppConfig.inAppDomains)
        if (AppConfig.BASE_URL.startsWith("https://") && policy.isInApp(AppConfig.BASE_URL)) {
            browser =
                BrowserController(
                    activity = this,
                    homeUrl = AppConfig.BASE_URL,
                    policy = policy,
                    downloads = DownloadController(this, container.downloadStore, policy.originRules()),
                    state = browserState,
                    host = this,
                ).also {
                    it.applySettings(container.settings.value)
                    it.start(savedInstanceState)
                }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.collect { settings ->
                    applySecureFlag(settings.secureScreen)
                    browser?.applySettings(settings)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.appLock.locked.collect { locked ->
                    if (locked) {
                        if (lockUi == LockUi.Unlocked) lockUi = LockUi.Locked()
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) requestUnlock()
                    } else {
                        lockUi = LockUi.Unlocked
                    }
                    // Une vue invisible ne reçoit ni toucher ni lecture d'écran (accessibilité).
                    browser?.view?.visibility = if (locked) View.INVISIBLE else View.VISIBLE
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.downloadStore.events.collect(::showDownloadEvent)
            }
        }

        // Retour : historique de la WebView d'abord, puis sortie de l'app.
        onBackPressedDispatcher.addCallback(this) {
            val b = browser
            if (b != null && b.canGoBack()) b.goBack() else finish()
        }

        setContent {
            AppTheme {
                val settings by container.settings.collectAsState()
                MainScreen(
                    browserView = browser?.view,
                    state = browserState,
                    lock = lockUi,
                    snackbarHostState = snackbarHostState,
                    onUnlock = ::requestUnlock,
                    onOpenSecuritySettings = ::openSecuritySettings,
                    onReload = { browser?.reload() },
                    onHome = { browser?.goHome() },
                    onSettings = { settingsLauncher.launch(Intent(this, SettingsActivity::class.java)) },
                    desktopMode = settings.desktopMode,
                    onToggleDesktopMode = {
                        container.applicationScope.launch {
                            container.settingsRepository.setDesktopMode(!container.settings.value.desktopMode)
                        }
                    },
                )
                if (lockUi == LockUi.Unlocked) {
                    val updateViewModel: AppUpdateViewModel =
                        viewModel(
                            factory =
                                viewModelFactory {
                                    initializer {
                                        AppUpdateViewModel(container.updateManager, container.updatesEnabled)
                                    }
                                },
                        )
                    UpdatePrompt(updateViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        browser?.onResume()
        if (container.appLock.locked.value) requestUnlock()
    }

    override fun onPause() {
        browser?.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        browser?.saveState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rien à recharger : Compose relit la configuration (thème clair/sombre, taille) tout seul.
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        browser?.destroy()
        browser = null
        super.onDestroy()
    }

    // --- Verrouillage ---------------------------------------------------------------------------------------

    private fun requestUnlock() {
        if (authInProgress || !container.appLock.locked.value) return
        authInProgress = true
        // La saisie du code de l'appareil est une autre activité sur Android 9 et moins : pas un « départ ».
        container.appLock.onOwnActivityLaunched()
        gate.authenticate { result ->
            authInProgress = false
            when (result) {
                GateResult.Success -> container.appLock.unlock()
                is GateResult.Failed -> lockUi = LockUi.Locked(result.message)
                GateResult.NoDeviceSecurity -> lockUi = LockUi.NoDeviceSecurity
            }
        }
    }

    private fun openSecuritySettings() {
        try {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun applySecureFlag(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        browser?.setSecure(secure)
    }

    // --- BrowserController.Host ----------------------------------------------------------------------------

    override fun secureScreen(): Boolean = container.settings.value.secureScreen

    override fun onRendererGone() {
        browser?.destroy()
        browser = null
        recreate()
    }

    override fun showFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        pendingCameraFile = null

        val mimeTypes = acceptedMimeTypes(params.acceptTypes)
        val content =
            Intent(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeTypes.singleOrNull() ?: "*/*")
        if (mimeTypes.size > 1) content.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val camera = cameraIntent(mimeTypes)
        val intent =
            if (params.isCaptureEnabled && camera != null) {
                camera
            } else {
                Intent.createChooser(content, "Joindre un document").apply {
                    if (camera != null) putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                }
            }
        return try {
            container.appLock.onOwnActivityLaunched()
            fileChooserLauncher.launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            pendingFileCallback = null
            pendingCameraFile?.delete()
            pendingCameraFile = null
            false
        }
    }

    /** Attribut `accept` (types MIME et extensions `.pdf`) → types MIME ; liste vide = tout type. */
    private fun acceptedMimeTypes(acceptTypes: Array<String>?): List<String> =
        acceptTypes
            .orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .mapNotNull { type ->
                if (type.startsWith(".")) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.removePrefix("."))
                } else {
                    type
                }
            }.distinct()
            .takeUnless { it.contains("*/*") }
            .orEmpty()

    /** Appareil photo proposé si la page accepte des images (ou tout type) et qu'une caméra existe. */
    private fun cameraIntent(mimeTypes: List<String>): Intent? {
        val wantsImages = mimeTypes.isEmpty() || mimeTypes.any { it.startsWith("image/") }
        if (!wantsImages || !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
        val file = container.downloadStore.newCameraFile()
        val uri = container.downloadStore.uriFor(file)
        val intent =
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", uri)
        val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (apps.isEmpty()) return null
        // Proposée via le sélecteur système : l'autorisation d'écrire la photo est accordée explicitement.
        apps.forEach {
            grantUriPermission(
                it.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        pendingCameraFile = file
        return intent
    }

    private fun onFileChooserResult(result: ActivityResult) {
        val callback = pendingFileCallback ?: return
        pendingFileCallback = null
        val cameraFile = pendingCameraFile
        pendingCameraFile = null
        cameraFile?.let {
            revokeUriPermission(
                container.downloadStore.uriFor(it),
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }

        if (result.resultCode != RESULT_OK) {
            cameraFile?.delete()
            callback.onReceiveValue(null)
            return
        }
        val uris = mutableListOf<Uri>()
        result.data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }
        if (uris.isEmpty()) result.data?.data?.let(uris::add)
        if (cameraFile != null && cameraFile.length() > 0) {
            val cameraUri = container.downloadStore.uriFor(cameraFile)
            if (uris.isEmpty() || uris == listOf(cameraUri)) {
                uris.clear()
                uris += cameraUri
            } else {
                cameraFile.delete()
            }
        } else {
            cameraFile?.delete()
        }
        callback.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    // --- Téléchargements ------------------------------------------------------------------------------------

    private suspend fun showDownloadEvent(event: DownloadEvent) {
        when (event) {
            is DownloadEvent.Started ->
                snackbarHostState.showSnackbar("Téléchargement de ${event.name}…", duration = SnackbarDuration.Short)
            is DownloadEvent.Failed ->
                snackbarHostState.showSnackbar("Échec du téléchargement de ${event.name}")
            is DownloadEvent.Completed -> {
                val result =
                    snackbarHostState.showSnackbar(
                        "${event.file.name} téléchargé",
                        actionLabel = "Ouvrir",
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    if (!FileOpener.open(this, container.downloadStore, event.file, event.mimeType)) {
                        snackbarHostState.showSnackbar("Aucune application installée ne sait ouvrir ce fichier")
                    }
                }
            }
        }
    }

    // --- Déconnexion ---------------------------------------------------------------------------------------

    private fun wipeData() {
        container.downloadStore.deleteAll()
        val b = browser
        if (b == null) {
            lifecycleScope.launch { snackbarHostState.showSnackbar("Données effacées") }
            return
        }
        b.wipe { lifecycleScope.launch { snackbarHostState.showSnackbar("Déconnecté : données effacées") } }
    }
}
