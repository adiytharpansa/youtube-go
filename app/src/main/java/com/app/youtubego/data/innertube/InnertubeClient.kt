package com.app.youtubego.data.innertube

import com.app.youtubego.domain.model.CaptionTrack
import com.app.youtubego.domain.model.StreamOption
import com.app.youtubego.domain.model.VideoSummary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Pengirim HTTP POST minimal; implementasi OkHttp ada di [com.app.youtubego.data.newpipe.OkHttpNewPipeDownloader.postJson]. */
fun interface HttpPoster { fun post(url: String, headers: Map<String, String>, body: String): String }

/** Hasil ringkas dari satu panggilan `/youtubei/v1/player`. Tanpa storyboard/bab (diisi NewPipe di latar belakang). */
data class OwnPlayback(
    val summary: VideoSummary,
    val description: String,
    val combined: List<StreamOption>,
    val videoOnly: List<StreamOption>,
    val audioOnly: List<StreamOption>,
    val captions: List<CaptionTrack> = emptyList(),
)

/**
 * Klien Innertube buatan sendiri: SATU permintaan POST kecil (klien ANDROID_VR) langsung menghasilkan URL stream.
 *
 * Kenapa lebih cepat dari NewPipeExtractor: NewPipe mengunduh halaman tonton + memanggil player + (bila perlu)
 * mengunduh & menjalankan JavaScript pemutar YouTube untuk dekode tanda tangan / parameter anti-throttle.
 * Klien ANDROID_VR mengembalikan URL langsung (tanpa cipher, tanpa JS), sehingga cukup satu permintaan.
 *
 * Rapuh dengan sengaja ditangani: YouTube bisa mengubah/menutup klien ini kapan saja. Karena itu semua pemanggil
 * HARUS menyiapkan cadangan (NewPipeExtractor); lihat DeviceStreamSource. Fungsi ini melempar exception bila apa pun janggal.
 */
class InnertubeClient(private val http: HttpPoster) {

    fun fetch(videoId: String): OwnPlayback = parse(videoId, http.post(ENDPOINT, headers(), requestBody(videoId)))

    private fun headers(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "User-Agent" to USER_AGENT,
        "X-YouTube-Client-Name" to CLIENT_ID,
        "X-YouTube-Client-Version" to CLIENT_VERSION,
        "Origin" to "https://www.youtube.com",
    )

    internal fun requestBody(videoId: String): String = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", "ANDROID_VR")
                put("clientVersion", CLIENT_VERSION)
                put("deviceMake", "Oculus")
                put("deviceModel", "Quest 3")
                put("androidSdkVersion", 32)
                put("osName", "Android")
                put("osVersion", "12L")
                put("userAgent", USER_AGENT)
                put("hl", "id")
                put("gl", "ID")
                put("timeZone", "UTC")
                put("utcOffsetMinutes", 0)
            }
        }
        put("videoId", videoId)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
        putJsonObject("playbackContext") { putJsonObject("contentPlaybackContext") { put("html5Preference", "HTML5_PREF_WANTS") } }
    }.toString()

    internal fun parse(videoId: String, raw: String): OwnPlayback {
        val root = Json.parseToJsonElement(raw).jsonObject
        val playability = root["playabilityStatus"] as? JsonObject
        val status = playability?.get("status")?.jsonPrimitive?.contentOrNull
        if (status != "OK") {
            val reason = playability?.get("reason")?.jsonPrimitive?.contentOrNull.orEmpty()
            throw IllegalStateException("innertube: playability=$status $reason".trim())
        }
        val details = root["videoDetails"] as? JsonObject ?: throw IllegalStateException("innertube: videoDetails kosong")
        if (details["isLive"]?.jsonPrimitive?.booleanOrNull == true || details["isUpcoming"]?.jsonPrimitive?.booleanOrNull == true)
            throw IllegalStateException("innertube: siaran langsung ditangani NewPipe")
        val sd = root["streamingData"] as? JsonObject ?: throw IllegalStateException("innertube: streamingData kosong")

        val muxed = objects(sd["formats"]).mapNotNull { toOption(it) }.filter { it.first.startsWith("video/") }
        val adaptive = objects(sd["adaptiveFormats"]).filter { usable(it) }.mapNotNull { toOption(it) }

        val combined = muxed.sortedBy { it.third }.map { it.second }
        val videoOnly = adaptive.filter { it.first.startsWith("video/") }.sortedBy { it.third }.map { it.second }
        val audioOnly = adaptive.filter { it.first.startsWith("audio/") }
            .sortedBy { it.fourth }.map { it.second.copy(qualityLabel = "${it.fourth}") }
        if (combined.isEmpty() && (videoOnly.isEmpty() || audioOnly.isEmpty()))
            throw IllegalStateException("innertube: tidak ada stream dengan URL langsung (kemungkinan perlu PO token / cipher)")

        val thumb = ((details["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray)
            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
        val summary = VideoSummary(
            id = videoId,
            title = details["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = details["author"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorId = details["channelId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            authorAvatarUrl = "",
            thumbnailUrl = thumb,
            lengthSeconds = details["lengthSeconds"]?.jsonPrimitive?.longOrNull ?: 0L,
            viewCount = details["viewCount"]?.jsonPrimitive?.longOrNull ?: 0L,
            published = 0L,
            isLive = false,
        )
        return OwnPlayback(summary, details["shortDescription"]?.jsonPrimitive?.contentOrNull.orEmpty(), combined, videoOnly, audioOnly, captionsOf(root))
    }

    /** Takarir (VTT) dari daftar trek; terbaik-upaya, takarir otomatis (asr) diurutkan terakhir. Gagal = tanpa takarir. */
    private fun captionsOf(root: JsonObject): List<CaptionTrack> = runCatching {
        val tracks = (((root["captions"] as? JsonObject)?.get("playerCaptionsTracklistRenderer") as? JsonObject)?.get("captionTracks") as? JsonArray)
            ?.mapNotNull { it as? JsonObject }.orEmpty()
        tracks.sortedBy { if (it["kind"]?.jsonPrimitive?.contentOrNull == "asr") 1 else 0 }.take(10).mapNotNull { t ->
            val base = t["baseUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lang = t["languageCode"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val name = (t["name"] as? JsonObject)?.let { n ->
                n["simpleText"]?.jsonPrimitive?.contentOrNull
                    ?: ((n["runs"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            CaptionTrack(name.ifBlank { lang }, lang, base.replace(Regex("[&?]fmt=[^&]*"), "") + "&fmt=vtt")
        }
    }.getOrDefault(emptyList())

    private fun objects(e: kotlinx.serialization.json.JsonElement?): List<JsonObject> =
        (e as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /** Buang format yang tak layak: dubbing non-default, DRC, atau OTF (potongan khusus). */
    private fun usable(o: JsonObject): Boolean {
        if (o["isDrc"]?.jsonPrimitive?.booleanOrNull == true) return false
        if ((o["audioTrack"] as? JsonObject)?.get("audioIsDefault")?.jsonPrimitive?.booleanOrNull == false) return false
        if (o["type"]?.jsonPrimitive?.contentOrNull == "FORMAT_STREAM_TYPE_OTF") return false
        return true
    }

    private data class Quad(val first: String, val second: StreamOption, val third: Int, val fourth: Int)

    /** (mime dasar, opsi, tinggi, bitrate) atau null bila tak ada URL langsung. */
    private fun toOption(o: JsonObject): Quad? {
        val url = o["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val full = o["mimeType"]?.jsonPrimitive?.contentOrNull ?: return null
        val mime = full.substringBefore(';').trim()
        val codec = full.substringAfter("codecs=\"", "").substringBefore("\"").substringBefore(",").trim()
        val height = o["height"]?.jsonPrimitive?.intOrNull ?: 0
        val label = o["qualityLabel"]?.jsonPrimitive?.contentOrNull ?: if (height > 0) "${height}p" else ""
        val bitrate = o["averageBitrate"]?.jsonPrimitive?.intOrNull ?: o["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        return Quad(mime, StreamOption(url, label, mime, codec), height, bitrate)
    }

    companion object {
        const val ENDPOINT = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val CLIENT_VERSION = "1.60.19"
        const val CLIENT_ID = "28"
        const val USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    }
}
