package com.app.playtubelite
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.app.playtubelite.data.newpipe.DeviceStreamSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class PlaytubeApp : Application(), ImageLoaderFactory {
    @Inject lateinit var deviceSource: DeviceStreamSource

    override fun onCreate() {
        super.onCreate()
        // Panaskan NewPipe + koneksi TLS ke YouTube di latar belakang: video pertama yang diketuk tidak menunggu handshake/inisialisasi.
        // Memuat berkas preferensi dimulai di thread latar SEKARANG; saat Hilt membuat store (di MainActivity) data sudah siap,
        // tidak lagi memblokir thread utama saat startup.
        listOf("user_library", "interest_profile", "search_history", "downloads").forEach { getSharedPreferences(it, MODE_PRIVATE) }
        deviceSource.warmUp()
    }

    /**
     * Pemuat gambar global agar thumbnail tampil cepat:
     * - cache memori 25% RAM + cache disk 256 MB (thumbnail yang pernah dilihat langsung muncul),
     * - klien HTTP khusus gambar dengan 16 permintaan paralel per host (bawaan OkHttp hanya 5, jadi gambar antre),
     * - abaikan header cache server (i.ytimg.com aman di-cache), RGB565 untuk menghemat memori, crossfade singkat.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 16 })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(128L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .crossfade(120)
            .build()
    }
}
