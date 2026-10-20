package com.app.youtubego.data.remote
/**
 * Kontrak API: dokumen "Invidious-API" yang dikirim user.
 * Semua endpoint JSON ada di bawah <instance>/api/v1, galat = {"error":"..."} + HTTP 4xx/5xx.
 *
 * KEPUTUSAN PRODUKSI (terverifikasi di web app):
 * - BASE_URL menunjuk proksi Vercel YouTube GO yang SUDAH LIVE, bukan instance langsung.
 *   Alasan: /api/v1 memang dikecualikan anti-bot Anubis, TETAPI /vi (thumbnail) dan
 *   /videoplayback (stream) DIPAGARI Anubis (halaman tantangan PoW, bukan gambar/video).
 *   Proksi itu memecahkan PoW di satu soket yang sama lalu meneruskan Range (206) -
 *   Coil & ExoPlayer tidak bisa memecahkan PoW sendiri.
 * - INSTANCE_URL hanya dipakai UrlRewriter untuk mengenali & menulis ulang URL mentah
 *   dari respons API (formatStreams, videoThumbnails, authorThumbnails, captions).
 */
object ApiConstants {
    const val BASE_URL = "https://youtube-go-adityas-projects-2ad3da13.vercel.app/"
    const val INSTANCE_URL = "https://invidious.f5.si"
    const val REGION = "ID"
    const val HL = "id"
}
