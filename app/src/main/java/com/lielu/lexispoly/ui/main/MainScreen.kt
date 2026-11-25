package com.lielu.lexispoly.ui.main

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lielu.lexispoly.R
import com.lielu.lexispoly.web.BrowserState
import com.lielu.lexispoly.web.PageError

/** État du verrouillage tel que l'écran le dessine. */
sealed interface LockUi {
    data object Unlocked : LockUi

    data class Locked(
        val message: String? = null,
    ) : LockUi

    /** Aucun verrouillage d'écran sur l'appareil : l'app refuse d'afficher les données. */
    data object NoDeviceSecurity : LockUi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    browserView: View?,
    state: BrowserState,
    lock: LockUi,
    snackbarHostState: SnackbarHostState,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onReload: () -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    desktopMode: Boolean,
    onToggleDesktopMode: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Lexis Poly", style = MaterialTheme.typography.titleMedium) },
                    expandedHeight = 48.dp,
                    actions = {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = onReload, enabled = browserView != null) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Actualiser")
                        }
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(R.drawable.ic_more), contentDescription = "Plus d'options")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Page d'accueil") },
                                enabled = browserView != null,
                                onClick = {
                                    menu = false
                                    onHome()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Version ordinateur") },
                                enabled = browserView != null,
                                trailingIcon = { Checkbox(checked = desktopMode, onCheckedChange = null) },
                                onClick = {
                                    menu = false
                                    onToggleDesktopMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Réglages") },
                                onClick = {
                                    menu = false
                                    onSettings()
                                },
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .fillMaxSize(),
            ) {
                if (browserView == null) {
                    MissingConfiguration()
                } else {
                    AndroidView(
                        factory = {
                            (browserView.parent as? ViewGroup)?.removeView(browserView)
                            browserView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (state.loading && state.progress < 100) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    state.error?.let { ErrorPanel(it, onRetry = onReload) }
                }
            }
        }
        if (lock != LockUi.Unlocked) LockPanel(lock, onUnlock, onOpenSecuritySettings)
    }
}

@Composable
private fun CenteredPanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Rien ne doit traverser le panneau jusqu'à la WebView dessous.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }.safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun PanelText(
    title: String,
    body: String,
) {
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ErrorPanel(
    error: PageError,
    onRetry: () -> Unit,
) {
    CenteredPanel {
        Icon(
            painterResource(R.drawable.ic_cloud_off),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        when (error) {
            PageError.Offline ->
                PanelText(
                    "Hors ligne",
                    "Lexis Poly n'est pas joignable. Vérifiez la connexion Internet ; la page se rechargera " +
                        "d'elle-même au retour du réseau.",
                )
            is PageError.Unreachable ->
                PanelText("Page indisponible", "La page n'a pas pu être chargée. ${error.detail}".trim())
            PageError.Insecure ->
                PanelText(
                    "Connexion non sécurisée",
                    "Le certificat du serveur n'a pas pu être vérifié : la connexion a été interrompue pour " +
                        "protéger vos données. Réessayez sur un autre réseau (Wi-Fi public, proxy d'entreprise…).",
                )
            is PageError.LeftApp ->
                PanelText(
                    "Ouvert dans le navigateur",
                    "La page a redirigé vers « ${error.host} », qui n'est pas dans la liste des domaines " +
                        "autorisés dans l'app. S'il s'agit de la connexion (SSO) de votre cabinet, ce domaine " +
                        "doit être ajouté à la configuration (README, « Liste blanche »).",
                )
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}

@Composable
private fun MissingConfiguration() {
    CenteredPanel {
        PanelText(
            "Configuration incomplète",
            "L'adresse de connexion Lexis Poly n'est pas renseignée (ou n'est pas en HTTPS sur un domaine " +
                "autorisé). Renseignez BASE_URL dans config/AppConfig.kt puis reconstruisez l'app.",
        )
    }
}

@Composable
private fun LockPanel(
    lock: LockUi,
    onUnlock: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
) {
    CenteredPanel {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        when (lock) {
            is LockUi.Locked -> {
                PanelText(
                    "Lexis Poly est verrouillé",
                    lock.message ?: "Déverrouillez avec votre empreinte, votre visage ou le code de l'appareil.",
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onUnlock) { Text("Déverrouiller") }
            }
            LockUi.NoDeviceSecurity -> {
                PanelText(
                    "Verrouillage de l'appareil requis",
                    "Les données de Lexis Poly sont couvertes par le secret professionnel. Configurez un code, " +
                        "un schéma, un mot de passe ou une empreinte sur cet appareil pour continuer.",
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onOpenSecuritySettings) { Text("Ouvrir les réglages de sécurité") }
                OutlinedButton(onClick = onUnlock) { Text("Réessayer") }
            }
            LockUi.Unlocked -> Unit
        }
    }
}
