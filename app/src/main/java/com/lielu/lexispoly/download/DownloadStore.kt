package com.lielu.lexispoly.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
) {
    private val appContext = context.applicationContext

    val authority = "${appContext.packageName}.fileprovider"

    /** `null` si le stockage externe propre à l'app est indisponible (rare : carte retirée, stockage plein). */
    val downloadsDir: File?
        get() = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.apply { mkdirs() }

    private val cameraDir: File get() = File(appContext.cacheDir, "camera").apply { mkdirs() }

    /** Téléchargements DownloadManager en cours : identifiant → fichier de destination et type MIME. */
    val pending = ConcurrentHashMap<Long, Pair<File, String>>()

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
