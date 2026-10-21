package com.app.youtubego.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec

/**
 * Header permintaan untuk URL googlevideo. URL terikat ke klien YouTube yang membuatnya (param c=ANDROID / IOS /
 * VISIONOS / WEB ...) dan server menolak (403/5xx) bila User-Agent tidak cocok dengan klien itu.
 * Dipakai SEMUA jalur yang mengambil stream: pemutar utama, Shorts, dan unduhan.
 */
object ClientHeaders {

    private const val WEB_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val uaCache = HashMap<String, String?>()

    /** UA resmi klien dari NewPipeExtractor (lewat refleksi supaya build tak patah bila nama berubah antar-versi). */
    private fun extractorUserAgent(method: String): String? = synchronized(uaCache) {
        uaCache.getOrPut(method) {
            runCatching {
                val c = Class.forName("org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper")
                val loc = Class.forName("org.schabi.newpipe.extractor.localization.Localization")
                c.getMethod(method, loc).invoke(null, null) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    /** User-Agent yang cocok dengan nilai param `c=` pada URL stream (null = klien web / tak diketahui). */
    fun userAgentFor(client: String?): String = when (client) {
        "ANDROID" -> extractorUserAgent("getAndroidUserAgent")
        "IOS" -> extractorUserAgent("getIosUserAgent")
        "VISIONOS" -> extractorUserAgent("getVisionOsUserAgent")
        "ANDROID_VR" -> com.app.youtubego.data.innertube.InnertubeClient.USER_AGENT
        else -> null
    } ?: WEB_UA

    /** true bila klien web: googlevideo mengharuskan Origin/Referer youtube.com untuk URL jenis ini. */
    private fun needsWebOrigin(host: String, client: String?): Boolean =
        host.endsWith("googlevideo.com") && (client == null || client.startsWith("WEB") || client.startsWith("MWEB"))

    /** Header untuk satu URL stream (dipakai OkHttp pada unduhan). */
    fun forUrl(url: String): Map<String, String> {
        val uri = Uri.parse(url)
        val client = uri.getQueryParameter("c")
        val headers = HashMap<String, String>()
        headers["User-Agent"] = userAgentFor(client)
        if (needsWebOrigin(uri.host.orEmpty(), client)) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        }
        return headers
    }

    /** Terapkan header di atas ke DataSpec Media3 (dipakai ResolvingDataSource). */
    @UnstableApi
    fun apply(spec: DataSpec): DataSpec {
        val headers = HashMap(spec.httpRequestHeaders)
        headers.putAll(forUrl(spec.uri.toString()))
        return spec.buildUpon().setHttpRequestHeaders(headers).build()
    }
}
