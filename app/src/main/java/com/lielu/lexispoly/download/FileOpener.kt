package com.lielu.lexispoly.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.io.File

/** Ouvre un document téléchargé (PDF, DOCX…) dans l'app choisie par l'utilisateur, via FileProvider. */
object FileOpener {
    /** `false` si aucune app installée ne sait ouvrir ce type de fichier. */
    fun open(
        context: Context,
        store: DownloadStore,
        file: File,
        mimeType: String = DownloadStore.mimeTypeFor(file),
    ): Boolean {
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(store.uriFor(file), mimeType)
                // Lecture seule, accordée à la seule app qui ouvre le fichier.
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
