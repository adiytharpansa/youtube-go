# BLUEPRINT TEKNIS — Playtube LITE Android Native (Kotlin)

> Aplikasi Android **native 100% Kotlin** Playtube LITE:
> minimalis, cepat, hemat data, **Dark Theme default penuh**.
> Dokumen ini adalah blueprint resmi proyek. Kode implementasi intinya sudah ditulis
> sebagai kerangka proyek nyata di repo ini — setiap bagian di bawah menunjuk berkasnya.

---

## 1. Tech Stack & Dependencies

| Lapisan | Pilihan | Versi | Alasan |
|---|---|---|---|
| Bahasa | Kotlin | 2.0.21 | 100% Kotlin, coroutine-native |
| UI | Jetpack Compose + Material 3 | BOM 2024.10.01 | Deklaratif, dark scheme bawaan, performa daftar tinggi |
| Arsitektur | MVVM + Clean Architecture | — | data / domain / presentation, arah dependensi ke dalam |
| Async | Coroutines + Flow / StateFlow | 1.9.0 | Lifecycle-aware, backpressure aman |
| DI | Hilt (Dagger) | 2.52 + KSP | Standar industri, compile-time safe |
| Player | AndroidX Media3 (ExoPlayer + DASH + UI + Session) | 1.5.0 | Putar optimal, hemat resource, siap PiP & latar |
| Gambar | Coil Compose | 2.7.0 | Memory + disk cache, crossfade, hemat data |
| Network | OkHttp + NewPipeExtractor | 4.12.0 / v0.26.5 | Downloader extractor, unduhan, data stream |
| Parsing | Kotlinx Serialization | 1.7.3 | Cepat, `ignoreUnknownKeys` tahan perubahan API |
| Navigasi | Navigation Compose | 2.8.3 | Satu Activity, rute bertipe |
| Build | AGP / Gradle | 8.7.2 / 8.10.2, Java 17, SDK 34 (min 24) | Sama dengan standar proyek pemilik |

Seluruh versi dikunci di `app/build.gradle.kts` (tanpa version catalog terpisah agar proyek tetap ramping; migrasi ke `libs.versions.toml` adalah langkah opsional tahap 2).

## 2. Sumber Data — Murni NewPipeExtractor + YouTube

Aplikasi TIDAK memakai server perantara apa pun.
Semua data diambil langsung dari YouTube di perangkat lewat **NewPipeExtractor** (`com.github.teamnewpipe:NewPipeExtractor`):

| Fitur | Sumber |
|---|---|
| Beranda, pencarian, saran | NewPipeExtractor -> YouTube (`NewPipeVideoRepository`) |
| Detail video, komentar, channel, playlist | NewPipeExtractor -> YouTube |
| Stream putar (gabungan, video-saja, audio-saja, DASH/HLS), storyboard, bab | NewPipeExtractor (`DeviceStreamSource`) |
| Unduhan dan Shorts | URL stream dari NewPipeExtractor, header per-klien lewat `ClientHeaders` |

- HTTP dijalankan `OkHttpNewPipeDownloader` (OkHttp) yang menjalankan request extractor apa adanya.
- Akun bersifat **lokal**: langganan, riwayat, suka, dan playlist disimpan di perangkat.
- Permintaan berangkat dari IP pengguna, jadi dinding anti-bot yang memblokir server tidak berlaku.

## 3. Struktur Proyek (Clean Architecture)
