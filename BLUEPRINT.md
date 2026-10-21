# BLUEPRINT TEKNIS — YouTube GO : Next Android Native (Kotlin)

> Aplikasi Android **native 100% Kotlin** dari konsep web app YouTube GO
> (live: https://youtube-go-adityas-projects-2ad3da13.vercel.app):
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
| Network | Retrofit + OkHttp | 2.11.0 / 4.12.0 | Cache HTTP 10 MB, interceptor, timeout tegas |
| Parsing | Kotlinx Serialization | 1.7.3 | Cepat, `ignoreUnknownKeys` tahan perubahan API |
| Navigasi | Navigation Compose | 2.8.3 | Satu Activity, rute bertipe |
| Build | AGP / Gradle | 8.7.2 / 8.10.2, Java 17, SDK 34 (min 24) | Sama dengan standar proyek pemilik |

Seluruh versi dikunci di `app/build.gradle.kts` (tanpa version catalog terpisah agar proyek tetap ramping; migrasi ke `libs.versions.toml` adalah langkah opsional tahap 2).

## 2. Kontrak API — Memakai Dokumen Invidious API yang Diberikan

**API yang dipakai adalah persis dokumen `Invidious-API` dari pemilik proyek**, bukan API lain:

- Semua endpoint JSON di bawah `https://<instance>/api/v1`, respons JSON, galat `{"error":"..."}` + HTTP 4xx/5xx.
- Endpoint terpasang di `data/remote/InvidiousApi.kt`:

| Grup | Endpoint (dokumen) | Dipakai untuk |
|---|---|---|
| Feed | `GET /trending?type=&region=ID`, `GET /popular` | Beranda + chip kategori |
| Video | `GET /videos/:id?local=true&region=ID` | Detail, `formatStreams[]`, `adaptiveFormats[]`, `dashUrl`, `hlsUrl`, `captions[]`, `recommendedVideos[]` |
| Video | `GET /comments/:id?sort_by=top`, `GET /captions/:id` | Komentar & subtitle |
| Cari | `GET /search?q=&page=&type=video&duration=&region=ID`, `GET /search/suggestions?q=` | Pencarian, Shorts (`duration=short`), saran (debounce 350 ms) |
| Channel | `GET /channels/:ucid`, `GET /channels/:ucid/latest` | Halaman channel, umpan langganan lokal |
| Lainnya | `GET /playlists/:plid`, `GET /stats` | Detail playlist, cek kesehatan instance |
| Streaming | `/api/manifest/dash/id/:id` (di luar /api/v1) | Jalur adaptif DASH tahap 2 (field `dashUrl` sudah dipetakan) |

- Endpoint `/auth/*` **sengaja tidak dipasang**: sama seperti web app, aplikasi memakai **akun lokal** (langganan/riwayat/suka disimpan di perangkat). API publik tidak menyediakan login pengguna akhir yang aman untuk ditanam di aplikasi.
- **Keputusan base URL (penting, terverifikasi di web app):** `ApiConstants.BASE_URL` menunjuk proksi Vercel YouTube GO yang sudah live (`https://youtube-go-adityas-projects-2ad3da13.vercel.app/`), **bukan** instance `https://invidious.f5.si` langsung. Alasannya teknis dan terbukti: `/api/v1` memang dikecualikan anti-bot Anubis, tetapi `/vi/*` (thumbnail) dan `/videoplayback` (stream) **dipagari Anubis** — membalas halaman tantangan proof-of-work, bukan gambar/video. Coil dan ExoPlayer tidak bisa memecahkan PoW. Proksi Vercel itu memecahkan PoW dalam satu soket yang sama, meneruskan `Range` (206 + Content-Range, seek terbukti bekerja), dan meng-cache gambar asli 24 jam. Semua URL mentah dari respons API (`formatStreams`, `videoThumbnails`, `authorThumbnails`, `captions`) ditulis ulang ke proksi oleh `data/mapper/UrlRewriter.kt` — setara helper `prox()` di web.
- Batasan jujur jalur ini (sudah diketahui dari web app): kualitas gabungan saat ini 360p; tiap permintaan Range memecahkan PoW ulang (±2–5 detik ke byte pertama) dan fungsi Vercel berumur maks 60 detik. Jalur naik kelasnya adalah DASH adaptif dari `dashUrl` (tahap 2), bukan mengganti API.

## 3. Struktur Proyek (Clean Architecture)
