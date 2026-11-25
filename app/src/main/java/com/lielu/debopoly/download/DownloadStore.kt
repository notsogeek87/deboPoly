package com.lielu.debopoly.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/** Événement à afficher à l'utilisateur (snackbar). */
sealed interface DownloadEvent {
    data class Started(
        val name: String,
    ) : DownloadEvent

    data class Completed(
        val file: File,
        val mimeType: String,
    ) : DownloadEvent

    data class Failed(
        val name: String,
    ) : DownloadEvent
}

/**
 * Où vivent les fichiers échangés avec Lexis Poly : documents téléchargés et photos prises pour un envoi.
 *
 * Tout est dans le stockage PROPRE à l'app (`Android/data/<app>/files/Download`, cache) : inaccessible aux autres
 * apps depuis Android 11, supprimé à la désinstallation, exclu de toute sauvegarde, effacé par « Se déconnecter
 * et effacer les données ». Les fichiers s'ouvrent dans une autre app via [FileProvider], au cas par cas.
 */
class DownloadStore(
    context: Context,
    private val applicationScope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    val authority = "${appContext.packageName}.fileprovider"

    /** `null` si le stockage externe propre à l'app est indisponible (rare : carte retirée, stockage plein). */
    val downloadsDir: File?
        get() = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }

    private val cameraDir: File get() = File(appContext.cacheDir, "camera").apply { mkdirs() }

    private var httpsDownloads = SupervisorJob(applicationScope.coroutineContext.job)

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events

    fun emit(event: DownloadEvent) {
        _events.tryEmit(event)
    }

    /** Fichier de destination libre pour [name] (déjà nettoyé par [FileNames.sanitize]). */
    fun newDownloadFile(name: String): File? {
        val dir = downloadsDir ?: return null
        return File(dir, FileNames.unique(name) { File(dir, it).exists() })
    }

    fun downloadHttps(
        url: String,
        name: String,
        mime: String,
        userAgent: String,
        referer: String?,
    ) {
        applicationScope.launch(httpsDownloads + Dispatchers.Main.immediate) {
            emit(DownloadEvent.Started(name))
            var partial: File? = null
            try {
                val directory = downloadsDir ?: throw IOException("Stockage indisponible")
                // Les fichiers partiels restent hors de la liste des documents.
                val staging = File(directory, ".pending").apply { mkdirs() }
                val temporary = File.createTempFile("download-", ".part", staging)
                partial = temporary
                withContext(Dispatchers.IO) {
                    downloadHttpsFile(url, userAgent, referer, temporary, CookieManager.getInstance()::getCookie)
                }
                val file = newDownloadFile(name) ?: throw IOException("Stockage indisponible")
                if (temporary.length() == 0L || !temporary.renameTo(file)) {
                    throw IOException("Téléchargement incomplet")
                }
                emit(DownloadEvent.Completed(file, mime))
            } catch (_: IOException) {
                emit(DownloadEvent.Failed(name))
            } finally {
                partial?.delete()
            }
        }
    }

    fun listDownloads(): List<File> =
        downloadsDir
            ?.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun newCameraFile(): File = File(cameraDir, "photo-${System.currentTimeMillis()}.jpg")

    /** Photos de plus d'un jour : déjà envoyées (ou abandonnées). */
    fun purgeStaleCameraFiles() {
        val limit = System.currentTimeMillis() - 24 * 3600_000L
        cameraDir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    /** Supprime documents téléchargés et photos. */
    fun deleteAll() {
        httpsDownloads.cancel()
        httpsDownloads = SupervisorJob(applicationScope.coroutineContext.job)
        downloadsDir?.listFiles()?.forEach { it.deleteRecursively() }
        cameraDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(appContext, authority, file)

    companion object {
        fun mimeTypeFor(file: File): String =
            MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
                ?: "application/octet-stream"

        fun extensionFor(mimeType: String?): String? =
            mimeType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    }
}
