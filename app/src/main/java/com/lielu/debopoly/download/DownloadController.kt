package com.lielu.debopoly.download

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Base64

/**
 * Téléchargements lancés par la page :
 * - `https:` → transfert avec contrôle des redirections et des en-têtes de session ;
 * - `data:` → décodé localement ;
 * - `blob:` → l'URL n'existe que dans la page : un script la relit (fetch) et renvoie le contenu par morceaux
 *   via un canal `WebViewCompat.addWebMessageListener` limité aux domaines autorisés (pas d'
 *   `addJavascriptInterface`). Chaque transfert porte un jeton aléatoire : un message sans jeton connu est ignoré.
 *
 * Destination : stockage propre à l'app ([DownloadStore]).
 */
class DownloadController(
    private val store: DownloadStore,
    private val originRules: Set<String>,
) {
    private val random = SecureRandom()
    private val blobTransfers = HashMap<String, BlobTransfer>()

    private class BlobTransfer(
        val suggestedName: String?,
        val fallbackMime: String,
        var file: File? = null,
        var out: FileOutputStream? = null,
        var mime: String = fallbackMime,
    )

    /** À appeler une fois la WebView créée : installe le canal de retour des téléchargements `blob:`. */
    fun attach(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, originRules) { _, message, _, _, _ ->
                onBridgeMessage(message)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Beaucoup d'applis web révoquent l'URL blob: juste après le clic : on retarde la révocation pour
            // laisser le temps de relire le contenu.
            WebViewCompat.addDocumentStartJavaScript(webView, DELAY_REVOKE_SCRIPT, originRules)
        }
    }

    fun detach() {
        blobTransfers.values.forEach { abort(it) }
        blobTransfers.clear()
    }

    /** Branché sur `WebView.setDownloadListener`. */
    fun onDownloadStart(
        webView: WebView,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        val suggested = FileNames.fromContentDisposition(contentDisposition)
        when {
            url.startsWith("https:", ignoreCase = true) -> {
                val name = buildName(suggested ?: FileNames.fromUrl(url), mimeType)
                store.downloadHttps(
                    url,
                    name,
                    effectiveMime(mimeType, File(name)),
                    userAgent ?: webView.settings.userAgentString,
                    webView.url,
                )
            }
            url.startsWith("data:", ignoreCase = true) -> saveDataUrl(url, suggested)
            url.startsWith("blob:", ignoreCase = true) -> fetchBlob(webView, url, suggested, mimeType)
            // http: (jamais en clair), file:, content: … : refusé.
            else -> store.emit(DownloadEvent.Failed(suggested ?: "document"))
        }
    }

    private fun saveDataUrl(
        url: String,
        suggested: String?,
    ) {
        val data = DataUrl.parse(url)
        val name = buildName(suggested, data?.mimeType)
        val file = if (data != null) store.newDownloadFile(name) else null
        if (data == null || file == null) {
            store.emit(DownloadEvent.Failed(name))
            return
        }
        val written = runCatching { file.writeBytes(data.bytes) }.isSuccess
        if (written) {
            store.emit(DownloadEvent.Completed(file, effectiveMime(data.mimeType, file)))
        } else {
            file.delete()
            store.emit(DownloadEvent.Failed(name))
        }
    }

    private fun fetchBlob(
        webView: WebView,
        url: String,
        suggested: String?,
        mimeType: String?,
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            store.emit(DownloadEvent.Failed(suggested ?: "document"))
            return
        }
        val token = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        blobTransfers[token] = BlobTransfer(suggested, mimeType?.takeIf { it.isNotBlank() } ?: "")
        webView.evaluateJavascript(blobFetchScript(url, token), null)
    }

    private fun onBridgeMessage(message: WebMessageCompat) {
        val json = runCatching { JSONObject(message.data ?: return) }.getOrNull() ?: return
        val token = json.optString("t")
        val transfer = blobTransfers[token] ?: return
        when (json.optString("k")) {
            "start" -> {
                val type = json.optString("type").ifBlank { transfer.fallbackMime }
                transfer.mime = type
                val name = buildName(transfer.suggestedName, type)
                val file = store.newDownloadFile(name)
                if (file == null) {
                    blobTransfers.remove(token)
                    store.emit(DownloadEvent.Failed(name))
                    return
                }
                transfer.file = file
                transfer.out = runCatching { FileOutputStream(file) }.getOrNull()
                store.emit(DownloadEvent.Started(file.name))
            }
            "chunk" -> {
                val out = transfer.out ?: return
                val ok = runCatching { out.write(Base64.getDecoder().decode(json.optString("d"))) }.isSuccess
                if (!ok) fail(token, transfer)
            }
            "end" -> {
                blobTransfers.remove(token)
                val file = transfer.file
                val closed = runCatching { transfer.out?.close() }.isSuccess
                if (file != null && closed && file.length() > 0) {
                    store.emit(DownloadEvent.Completed(file, effectiveMime(transfer.mime, file)))
                } else {
                    abort(transfer)
                    store.emit(DownloadEvent.Failed(file?.name ?: transfer.suggestedName ?: "document"))
                }
            }
            else -> fail(token, transfer)
        }
    }

    private fun fail(
        token: String,
        transfer: BlobTransfer,
    ) {
        blobTransfers.remove(token)
        abort(transfer)
        store.emit(DownloadEvent.Failed(transfer.file?.name ?: transfer.suggestedName ?: "document"))
    }

    private fun abort(transfer: BlobTransfer) {
        runCatching { transfer.out?.close() }
        transfer.file?.delete()
    }

    private fun buildName(
        suggested: String?,
        mimeType: String?,
    ): String = FileNames.sanitize(suggested, DownloadStore.extensionFor(mimeType), "document-${stamp()}")

    private fun stamp(): String =
        java.text
            .SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT)
            .format(java.util.Date())

    /** Le type annoncé par le serveur s'il est précis, sinon celui déduit de l'extension. */
    private fun effectiveMime(
        announced: String?,
        file: File,
    ): String {
        val clean = announced?.substringBefore(';')?.trim()
        return if (clean.isNullOrBlank() || clean == "application/octet-stream" || clean == "binary/octet-stream") {
            DownloadStore.mimeTypeFor(file)
        } else {
            clean
        }
    }

    companion object {
        const val BRIDGE_NAME = "LexisPolyDownloads"

        private const val DELAY_REVOKE_SCRIPT =
            "(function(){if(window.__lpRevokeDelayed)return;window.__lpRevokeDelayed=true;" +
                "var r=URL.revokeObjectURL.bind(URL);" +
                "URL.revokeObjectURL=function(u){setTimeout(function(){r(u);},120000);};})();"

        /** Lit l'URL blob: dans la page et la renvoie par morceaux base64 (768 Kio, multiple de 3). */
        internal fun blobFetchScript(
            url: String,
            token: String,
        ): String {
            val u = JSONObject.quote(url)
            val t = JSONObject.quote(token)
            return """
                (function(u,t){
                  var B=window.$BRIDGE_NAME;
                  if(!B){return;}
                  function send(o){o.t=t;B.postMessage(JSON.stringify(o));}
                  fetch(u).then(function(r){return r.blob();}).then(function(b){
                    var CH=786432,off=0;
                    send({k:'start',type:b.type||''});
                    function next(){
                      if(off>=b.size){send({k:'end'});return;}
                      var fr=new FileReader();
                      fr.onload=function(){var s=fr.result;send({k:'chunk',d:s.substring(s.indexOf(',')+1)});
                        off+=CH;next();};
                      fr.onerror=function(){send({k:'error'});};
                      fr.readAsDataURL(b.slice(off,off+CH));
                    }
                    next();
                  }).catch(function(){send({k:'error'});});
                })($u,$t);
                """.trimIndent()
        }
    }
}
