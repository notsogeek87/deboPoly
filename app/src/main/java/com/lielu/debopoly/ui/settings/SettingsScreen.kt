package com.lielu.debopoly.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lielu.debopoly.BuildConfig
import com.lielu.debopoly.R
import com.lielu.debopoly.config.AppConfig
import com.lielu.debopoly.download.FileOpener
import com.lielu.debopoly.ui.update.updateErrorMessage
import com.lielu.githubupdater.UpdateInfo
import com.lielu.githubupdater.UpdateState
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    onWipeConfirmed: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmWipe by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshDownloads() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Retour")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            SectionTitle("Sécurité")
            SwitchRow(
                title = "Bloquer les captures d'écran",
                subtitle = "Empêche captures et enregistrements, et masque l'aperçu dans les apps récentes.",
                checked = settings.secureScreen,
                onCheckedChange = viewModel::setSecureScreen,
            )
            Text(
                "Redemander l'empreinte ou le code après",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            AppConfig.LOCK_TIMEOUT_CHOICES.forEach { minutes ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.lockTimeoutMinutes == minutes,
                            role = Role.RadioButton,
                            onClick = { viewModel.setLockTimeout(minutes) },
                        ).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.lockTimeoutMinutes == minutes, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(timeoutLabel(minutes))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionTitle("Affichage du site")
            SwitchRow(
                title = "Agenda en liste sur téléphone",
                subtitle =
                    "En portrait, l'agenda s'affiche en liste des rendez-vous par jour, à partir d'aujourd'hui, " +
                        "sous le menu du site. « Filtres » rend la page normale ; en paysage, la vue habituelle revient.",
                checked = settings.agendaMobile,
                onCheckedChange = viewModel::setAgendaMobile,
            )
            SwitchRow(
                title = "Version ordinateur",
                subtitle =
                    "Affiche le site comme sur un ordinateur (pages larges, zoom au pincement). Utile pour les " +
                        "écrans prévus pour ordinateur, comme l'agenda. Aussi dans le menu ⋮ de l'écran principal.",
                checked = settings.desktopMode,
                onCheckedChange = viewModel::setDesktopMode,
            )
            SwitchRow(
                title = "Se présenter comme Chrome",
                subtitle =
                    "Envoie le user-agent de Chrome mobile, sans le marqueur « wv » des WebView. À désactiver " +
                        "seulement si le site s'affiche mal.",
                checked = settings.chromeUserAgent,
                onCheckedChange = viewModel::setChromeUserAgent,
            )
            SwitchRow(
                title = "Assombrir les pages sans thème sombre",
                subtitle =
                    "En mode sombre, le site suit le thème du système s'il le prévoit. Cette option assombrit " +
                        "aussi les pages qui ne le prévoient pas (rendu parfois imparfait).",
                checked = settings.algorithmicDarkening,
                onCheckedChange = viewModel::setAlgorithmicDarkening,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionTitle("Documents téléchargés")
            Text(
                "Conservés dans l'espace privé de l'app, jamais sauvegardés dans le cloud.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (downloads.isEmpty()) {
                Text("Aucun document.", style = MaterialTheme.typography.bodyMedium)
            }
            downloads.forEach { file ->
                DownloadRow(
                    file = file,
                    onOpen = {
                        if (!FileOpener.open(context, viewModel.downloadStore, file)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Aucune application ne sait ouvrir ce fichier",
                                )
                            }
                        }
                    },
                    onDelete = { viewModel.deleteDownload(file) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionTitle("Compte")
            Text(
                "Vide les cookies (session), le cache, le stockage du site, l'historique et les documents " +
                    "téléchargés. Il faudra se reconnecter.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(
                onClick = { confirmWipe = true },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) { Text("Se déconnecter et effacer les données") }

            if (viewModel.updatesEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                UpdateSection(
                    state = updateState,
                    appName = appName,
                    onCheck = viewModel::onCheckUpdateRequested,
                    onInstall = viewModel::onInstallUpdateRequested,
                    onInstallDownloaded = viewModel::onInstallDownloadedRequested,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text(
                "$appName ${BuildConfig.VERSION_NAME} — application indépendante, non éditée par LexisNexis. " +
                    "Elle affiche l'interface web officielle de Lexis Poly.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Se déconnecter ?") },
            text = {
                Text(
                    "Toutes les données de Lexis Poly enregistrées sur cet appareil seront effacées, y compris " +
                        "les documents téléchargés. Cette action est irréversible.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    onWipeConfirmed()
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DownloadRow(
    file: File,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                Formatter.formatShortFileSize(context, file.length()) + " · " +
                    DateFormat
                        .getDateTimeInstance(
                            DateFormat.SHORT,
                            DateFormat.SHORT,
                        ).format(Date(file.lastModified())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) { Text("Supprimer") }
    }
}

private fun timeoutLabel(minutes: Int): String =
    when (minutes) {
        0 -> "Immédiatement (à chaque retour dans l'app)"
        1 -> "1 minute"
        else -> "$minutes minutes"
    }

/** Section « Mise à jour » : dessine l'état exposé par la bibliothèque lielugit-updater. */
@Composable
private fun UpdateSection(
    state: UpdateState,
    appName: String,
    onCheck: () -> Unit,
    onInstall: (UpdateInfo) -> Unit,
    onInstallDownloaded: (File) -> Unit,
) {
    Text("Mise à jour", style = MaterialTheme.typography.titleMedium)
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading
    val status =
        when (state) {
            UpdateState.Idle -> "Vérifie si une nouvelle version est publiée sur GitHub."
            UpdateState.Checking -> "Recherche de mise à jour…"
            UpdateState.UpToDate -> "Vous utilisez la dernière version."
            is UpdateState.UpdateAvailable -> "Version ${state.update.versionName} disponible."
            is UpdateState.Downloading -> "Téléchargement… ${state.progress.percentage?.let { "$it %" }.orEmpty()}"
            is UpdateState.Downloaded -> "Mise à jour téléchargée, prête à installer."
            UpdateState.Installing -> "Installation en cours…"
            is UpdateState.Error -> updateErrorMessage(state.error, appName)
        }
    Text(
        status,
        style = MaterialTheme.typography.labelSmall,
        color =
            if (state is UpdateState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    if (state is UpdateState.Downloading) {
        LinearProgressIndicator(
            progress = { (state.progress.percentage ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is UpdateState.UpdateAvailable ->
                Button(onClick = { onInstall(state.update) }) { Text("Télécharger et installer") }
            is UpdateState.Downloaded ->
                Button(onClick = { onInstallDownloaded(state.file) }) { Text("Installer") }
            else -> Unit
        }
        if (state is UpdateState.UpdateAvailable || state is UpdateState.Downloaded) Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onCheck, enabled = !busy) { Text("Rechercher une mise à jour") }
    }
}
