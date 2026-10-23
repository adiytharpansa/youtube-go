package com.app.playtubelite.data.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * Downloader untuk NewPipeExtractor di atas OkHttp.
 * Menjalankan Request persis apa adanya — semua header (termasuk User-Agent klien)
 * berasal dari extractor, bukan dari aplikasi.
 */
class OkHttpNewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    /** Buka koneksi TCP+TLS(+HTTP/2) ke host YouTube lebih awal; permintaan ekstraksi pertama tidak lagi membayar handshake. */
    fun warmUp() {
        for (u in listOf("https://www.youtube.com/generate_204", "https://youtubei.googleapis.com/generate_204")) {
            runCatching { client.newCall(okhttp3.Request.Builder().url(u).head().build()).execute().close() }
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (name, values) -> values.forEach { builder.addHeader(name, it) } }
        val data = request.dataToSend()
        when (request.httpMethod().uppercase()) {
            "HEAD" -> builder.head()
            "POST" -> builder.post((data ?: ByteArray(0)).toRequestBody())
            "PUT" -> builder.put((data ?: ByteArray(0)).toRequestBody())
            "DELETE" -> if (data != null) builder.delete(data.toRequestBody()) else builder.delete()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string() ?: "",
                resp.request.url.toString(),
            )
        }
    }
}
