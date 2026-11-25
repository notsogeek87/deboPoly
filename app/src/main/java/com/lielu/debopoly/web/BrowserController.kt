package com.lielu.debopoly.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import android.widget.FrameLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.lielu.debopoly.data.settings.AppSettings
import com.lielu.debopoly.download.DownloadController
import java.net.URI

/**
 * Possède la WebView et applique toutes les règles de l'app : liste blanche, HTTPS seul, téléchargements,
 * fenêtres secondaires (connexion SSO en popup), erreurs, effacement des données.
 *
 * Volontairement : aucun `addJavascriptInterface`, aucun accès `file://` ni `content://`, aucun journal d'URL
 * ni de contenu.
 */
@SuppressLint("SetJavaScriptEnabled")
class BrowserController(
    private val activity: Activity,
    private val homeUrl: String,
    private val policy: UrlPolicy,
    private val downloads: DownloadController,
    val state: BrowserState,
    private val host: Host,
) {
    /** Ce que l'activité fournit : sélecteur de fichiers, recréation après plantage du moteur, réglages. */
    interface Host {
        fun showFileChooser(
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams,
        ): Boolean

        fun onRendererGone()

        fun secureScreen(): Boolean
    }

    val webView = LexisWebView(activity)
    val view: WebSwipeRefreshLayout = WebSwipeRefreshLayout(activity)

    private val defaultUserAgent: String
    private val defaultUaMetadata: UserAgentMetadata?
    private var appliedSettings: AppSettings? = null
    private var clearHistoryOnNextFinish = false
    private var popup: Dialog? = null
    private var desktopScript: ScriptHandler? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        WebView.setWebContentsDebuggingEnabled(false)
        configure(webView)
        defaultUserAgent = webView.settings.userAgentString
        defaultUaMetadata =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                WebSettingsCompat.getUserAgentMetadata(webView.settings)
            } else {
                null
            }
        webView.webViewClient = MainClient()
        webView.webChromeClient = ChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.onDownloadStart(webView, url, userAgent, contentDisposition, mimeType)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Les connexions SSO posent souvent leurs cookies depuis un autre domaine (iframe, redirection).
            setAcceptThirdPartyCookies(webView, true)
        }
        downloads.attach(webView)

        view.webView = webView
        view.addView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view.setOnRefreshListener { reload() }
    }

    /** Réglages communs à la WebView principale et aux fenêtres secondaires. */
    private fun configure(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(wv.settings, true)
        }
        appliedSettings?.let { applyUserSettings(wv, it) }
    }

    /** Applique les réglages de l'utilisateur ; recharge la page si le user-agent a changé. */
    fun applySettings(settings: AppSettings) {
        val previous = appliedSettings
        appliedSettings = settings
        applyUserSettings(webView, settings)
        updateDesktopScript(settings.desktopMode)
        val uaChanged =
            previous != null &&
                (previous.chromeUserAgent != settings.chromeUserAgent || previous.desktopMode != settings.desktopMode)
        if (uaChanged && webView.url != null) webView.reload()
    }

    private fun applyUserSettings(
        wv: WebView,
        settings: AppSettings,
    ) {
        wv.settings.userAgentString =
            when {
                settings.desktopMode -> UserAgents.desktop(defaultUserAgent)
                settings.chromeUserAgent -> UserAgents.chromeLike(defaultUserAgent)
                else -> null
            }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val base = defaultUaMetadata
            if (base != null) {
                // Client Hints (Sec-CH-UA) cohérents avec le user-agent : sans la marque « Android WebView »
                // comme Chrome, et « non mobile » en version ordinateur.
                val metadata =
                    if (settings.chromeUserAgent || settings.desktopMode) {
                        UserAgentMetadata
                            .Builder(base)
                            .setBrandVersionList(base.brandVersionList.filterNot { it.brand.contains("WebView", true) })
                            .setMobile(!settings.desktopMode && base.isMobile)
                            .build()
                    } else {
                        base
                    }
                WebSettingsCompat.setUserAgentMetadata(wv.settings, metadata)
            }
        }
        // Thème sombre : avec targetSdk ≥ 33, la WebView expose `prefers-color-scheme` d'après le thème de l'app
        // (qui suit le système). L'assombrissement automatique des sites SANS thème sombre reste optionnel.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, settings.algorithmicDarkening)
        }
    }

    private fun updateDesktopScript(enabled: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (enabled && desktopScript == null) {
            desktopScript =
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    UserAgents.DESKTOP_VIEWPORT_SCRIPT,
                    policy.originRules(),
                )
        } else if (!enabled) {
            desktopScript?.remove()
            desktopScript = null
        }
    }

    /** Premier affichage : état restauré (mort du processus) s'il existe, sinon la page de connexion. */
    fun start(savedState: Bundle?) {
        val restored = savedState?.getBundle(KEY_WEBVIEW)?.let { webView.restoreState(it) }
        if (restored == null) webView.loadUrl(homeUrl)
        watchNetwork()
    }

    fun saveState(outState: Bundle) {
        val bundle = Bundle()
        webView.saveState(bundle)
        outState.putBundle(KEY_WEBVIEW, bundle)
    }

    fun reload() {
        state.error = null
        val current = webView.url
        if (current == null || current == "about:blank" || !policy.isInApp(current)) {
            webView.loadUrl(homeUrl)
        } else {
            webView.reload()
        }
    }

    fun goHome() {
        state.error = null
        webView.loadUrl(homeUrl)
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        state.error = null
        webView.goBack()
    }

    fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
    }

    fun onResume() {
        webView.onResume()
    }

    /**
     * « Se déconnecter et effacer les données » : cookies, cache HTTP, stockage web (localStorage, IndexedDB,
     * Cache Storage, service workers), formulaires, identifiants HTTP, historique. Puis retour à la connexion.
     */
    fun wipe(onDone: () -> Unit) {
        closePopup()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        webView.clearCache(true)
        webView.clearFormData()
        webView.clearSslPreferences()
        WebViewDatabase.getInstance(activity).clearHttpAuthUsernamePassword()
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            // clearHistory() garde l'entrée courante : on l'appelle une fois la page de connexion chargée.
            clearHistoryOnNextFinish = true
            webView.clearHistory()
            state.error = null
            webView.loadUrl(homeUrl)
            onDone()
        }
    }

    fun destroy() {
        networkCallback?.let { cb ->
            runCatching { activity.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        closePopup()
        downloads.detach()
        (view.parent as? ViewGroup)?.removeView(view)
        webView.stopLoading()
        webView.destroy()
    }

    /** Hors ligne → retour du réseau : nouvel essai automatique. */
    private fun watchNetwork() {
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        val cb =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activity.runOnUiThread { if (state.error == PageError.Offline) reload() }
                }
            }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun isOnline(): Boolean {
        val cm = activity.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Décision commune aux deux WebView pour une navigation. Renvoie `true` si la navigation est prise en
     * charge ici (donc annulée dans la WebView).
     */
    private fun handleNavigation(
        wv: WebView,
        url: String,
        isMainFrame: Boolean,
    ): Boolean =
        when (val decision = policy.decide(url)) {
            UrlDecision.InApp -> false
            is UrlDecision.UpgradeToHttps -> {
                if (isMainFrame) wv.loadUrl(decision.httpsUrl)
                true
            }
            UrlDecision.ExternalBrowser -> {
                // Un cadre intégré (aperçu, lecteur) peut venir d'un autre domaine ; seule la page principale
                // est tenue à la liste blanche.
                if (isMainFrame) ExternalLinks.openInBrowser(activity, url)
                isMainFrame
            }
            UrlDecision.ExternalApp -> {
                ExternalLinks.openSchemeUrl(activity, url)
                true
            }
            UrlDecision.AndroidIntent -> {
                ExternalLinks.openIntentUrl(activity, url)
                true
            }
            UrlDecision.LocalContent -> false
            UrlDecision.Blocked -> true
        }

    private inner class MainClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = handleNavigation(view, request.url.toString(), request.isForMainFrame)

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            state.loading = true
            // Une redirection serveur vers un domaine hors liste ne passe pas toujours par
            // shouldOverrideUrlLoading : dernier filet.
            val decision = policy.decide(url)
            if (url != "about:blank" && decision != UrlDecision.InApp && decision != UrlDecision.LocalContent) {
                view.stopLoading()
                handleNavigation(view, url, isMainFrame = true)
                // Rien d'affiché derrière (ex. redirection SSO dès le chargement) : expliquer plutôt qu'une page vide.
                if (!view.canGoBack() && decision == UrlDecision.ExternalBrowser) {
                    state.loading = false
                    state.error = PageError.LeftApp(runCatching { URI(url).host }.getOrNull().orEmpty())
                }
            }
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            state.loading = false
            this@BrowserController.view.isRefreshing = false
            if (clearHistoryOnNextFinish && url != "about:blank") {
                clearHistoryOnNextFinish = false
                view.clearHistory()
            }
            CookieManager.getInstance().flush()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            this@BrowserController.view.isRefreshing = false
            state.loading = false
            state.error =
                if (!isOnline() || error.errorCode in OFFLINE_ERRORS) {
                    PageError.Offline
                } else {
                    PageError.Unreachable(error.description?.toString().orEmpty())
                }
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError,
        ) {
            // Jamais proceed() : un certificat invalide = connexion abandonnée.
            handler.cancel()
            val current = view.url
            val sameHost =
                current == null ||
                    runCatching { URI(error.url).host == URI(current).host }.getOrDefault(true)
            if (sameHost) {
                this@BrowserController.view.isRefreshing = false
                state.loading = false
                state.error = PageError.Insecure
            }
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            // La WebView est inutilisable : l'activité en recrée une (sans planter l'app).
            host.onRendererGone()
            return true
        }
    }

    private inner class ChromeClient : WebChromeClient() {
        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            state.progress = newProgress
            if (newProgress >= 100) state.loading = false
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = host.showFileChooser(filePathCallback, fileChooserParams)

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean = openPopup(resultMsg)

        override fun onPermissionRequest(request: PermissionRequest) {
            // Caméra / micro / MIDI demandés par une page : refusés (aucune permission déclarée).
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, false, false)
        }
    }

    /**
     * `window.open` / `target=_blank` : une fenêtre plein écran au-dessus de la page, nécessaire aux connexions
     * SSO en popup (la page d'origine attend un message de la fenêtre qu'elle a ouverte). Mêmes règles que la
     * WebView principale ; une destination hors liste blanche part dans le navigateur et ferme la fenêtre.
     */
    private fun openPopup(resultMsg: Message): Boolean {
        closePopup()
        val popupView = WebView(activity)
        configure(popupView)
        CookieManager.getInstance().setAcceptThirdPartyCookies(popupView, true)
        popupView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloads.onDownloadStart(popupView, url, userAgent, contentDisposition, mimeType)
        }
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
        popupView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val handled = handleNavigation(view, request.url.toString(), request.isForMainFrame)
                    if (handled && request.isForMainFrame && view.url.isNullOrEmpty()) closePopup()
                    return handled
                }

                override fun onPageStarted(
                    view: WebView,
                    url: String,
                    favicon: Bitmap?,
                ) {
                    val decision = policy.decide(url)
                    if (url != "about:blank" && decision != UrlDecision.InApp && decision != UrlDecision.LocalContent) {
                        view.stopLoading()
                        handleNavigation(view, url, isMainFrame = true)
                        closePopup()
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel()
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    closePopup()
                    return true
                }
            }
        popupView.webChromeClient =
            object : WebChromeClient() {
                override fun onCloseWindow(window: WebView) {
                    closePopup()
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean = host.showFileChooser(filePathCallback, fileChooserParams)

                override fun onPermissionRequest(request: PermissionRequest) {
                    request.deny()
                }
            }
        val frame =
            FrameLayout(activity).apply {
                fitsSystemWindows = true
                addView(
                    popupView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        dialog.setContentView(frame)
        if (host.secureScreen()) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (popupView.canGoBack()) popupView.goBack() else closePopup()
                true
            } else {
                keyCode == KeyEvent.KEYCODE_BACK
            }
        }
        dialog.setOnDismissListener {
            if (popup === dialog) popup = null
            popupView.stopLoading()
            popupView.destroy()
        }
        popup = dialog
        dialog.show()

        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = popupView
        resultMsg.sendToTarget()
        return true
    }

    private fun closePopup() {
        popup?.let { if (it.isShowing) it.dismiss() }
        popup = null
    }

    /** Réapplique FLAG_SECURE à une fenêtre secondaire ouverte. */
    fun setSecure(secure: Boolean) {
        popup?.window?.let {
            if (secure) {
                it.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    companion object {
        private const val KEY_WEBVIEW = "lexis_webview"

        private val OFFLINE_ERRORS =
            setOf(
                WebViewClient.ERROR_HOST_LOOKUP,
                WebViewClient.ERROR_CONNECT,
                WebViewClient.ERROR_TIMEOUT,
                WebViewClient.ERROR_IO,
                WebViewClient.ERROR_PROXY_AUTHENTICATION,
            )
    }
}
