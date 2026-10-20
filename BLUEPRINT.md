# BLUEPRINT TEKNIS — YouTube GO Android Native (Kotlin)

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

```
com.app.youtubego
├── YoutubeGoApp.kt            (@HiltAndroidApp)
├── MainActivity.kt            (satu Activity, tema gelap dipaksa default)
├── di/
│   ├── NetworkModule.kt       (Json, OkHttp+cache, Retrofit, dispatcher)
│   └── RepositoryModule.kt    (@Binds VideoRepository)
├── data/
│   ├── remote/
│   │   ├── InvidiousApi.kt    (Retrofit interface 1:1 dokumen API)
│   │   ├── ApiConstants.kt    (BASE_URL proksi, instance, region ID, hl id)
│   │   └── dto/VideoDtos.kt   (semua DTO @Serializable)
│   ├── mapper/                (VideoMapper: DTO -> domain, pilih thumbnail terbaik; UrlRewriter)
│   └── repository/VideoRepositoryImpl.kt  (withContext(IO) + runCatching -> Result)
├── domain/
│   ├── model/Models.kt        (VideoSummary, VideoDetail, StreamOption, CaptionTrack, ...)
│   ├── repository/VideoRepository.kt      (interface + HomeCategory)
│   └── usecase/UseCases.kt    (GetTrending, SearchVideos, GetVideoDetail -> Flow<UiState>)
└── presentation/
    ├── common/                (UiState, VideoCard, shimmer, ErrorBox, formatter)
    ├── home/                  (HomeScreen LazyColumn + chip, HomeViewModel)
    ├── search/                (SearchScreen + saran debounce, SearchViewModel)
    ├── player/                (PlayerScreen Media3 + kontrol kustom, PlayerViewModel)
    ├── navigation/Nav.kt      (home / search / player/{videoId})
    └── theme/                 (Theme.kt token warna web, TypeAndShape.kt)
```

Aturan arah dependensi: `presentation -> domain <- data`. Presentation tidak pernah menyentuh DTO/Retrofit; data tidak pernah menyentuh Compose.

## 4. UI State — Sealed Interface

`presentation/common/UiState.kt` (dipakai semua layar, `when` wajib exhaustive):

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

Pesan error **selalu ramah** (diterjemahkan di `Throwable.toFriendlyMessage()` di use case): tidak ada koneksi / server bermasalah / video tidak tersedia — tidak pernah menampilkan pengecualian mentah ke pengguna.

## 5. Home Screen — LazyColumn Performa Tinggi + Shimmer

`presentation/home/HomeScreen.kt` + `HomeViewModel.kt`:

- `LazyColumn` dengan **`key = videoId` stabil** dan `contentType = "video"` — kunci scroll halus dan reuse komposisi pada daftar panjang.
- Chip kategori (Semua/Musik/Game/Mix/Berita/Belajar) memetakan persis perilaku web: Musik & Game ke `/trending?type=`, Mix/Berita/Belajar ke `/search`, Semua ke `/trending` dengan cadangan `/popular`.
- Saat `Loading`: 5 `VideoCardShimmer()` — shimmer brush linear bergerak **tanpa library tambahan** (`Modifier.shimmer()` di `common/Components.kt`), murah di GPU.
- Kartu video (`VideoCard`): thumbnail 16:9 sudut 14dp, lencana durasi / **LIVE** (live = `liveNow || lengthSeconds == 0`, aturan yang sama dengan perbaikan web), avatar Coil, judul maks 2 baris elipsis.
- Coil: `crossfade(true)` + memory & disk cache `ENABLED` — thumbnail berulang tidak mengunduh ulang (hemat data).
- ViewModel mengekspos satu `StateFlow<HomeUiState>`; pergantian kategori membatalkan job lama (`job?.cancel()`) agar respons basi tidak menimpa kategori baru.

## 6. Player — Media3 ExoPlayer di Compose, Kontrol Kustom, Fullscreen

`presentation/player/PlayerScreen.kt` + `PlayerViewModel.kt`:

- ExoPlayer dibuat **sekali per videoId** dengan `remember(videoId)`, dilepas di `DisposableEffect` (`player.release()`), dan di-`pause()` otomatis saat `ON_PAUSE` (lifecycle-aware).
- Integrasi lewat `AndroidView` + `PlayerView(useController = false)`; **kontrolnya Compose kustom** agar identik desain: tombol kembali, play/pause tengah 64dp, slider posisi + label waktu, tombol fullscreen.
- **Fullscreen**: state Compose mengunci `requestedOrientation = SENSOR_LANDSCAPE` di Activity dan menyembunyikan daftar di bawahnya; keluar fullscreen mengembalikan orientasi bebas.
- **Hemat data sejak desain**: `DefaultTrackSelector` dibatasi `setMaxVideoSize(1280, 720)` + `setMaxAudioBitrate(128_000)`, dan media yang disiapkan pertama adalah `formatStreams` gabungan **kualitas terendah** (pola web 360p), cadangan `hlsUrl`. DASH adaptif (`dashUrl` + `media3-exoplayer-dash`, sudah ada dependensinya) adalah saklar tahap 2 untuk kualitas lebih tinggi saat pengguna memilih.
- Posisi dipolling tiap 500 ms ke state Compose (cukup untuk slider, jauh lebih murah daripada listener per-frame).
- Di bawah pemutar: judul, jumlah tonton, deskripsi (maks 4 baris), lalu rekomendasi dari `recommendedVideos[]` sebagai `LazyColumn` yang sama dengan Beranda. Autoplay rekomendasi pertama adalah opsi pengaturan (pola web), bukan bawaan menyala.

## 7. ViewModel & UseCase — Contoh Alur Data Lengkap

```
HomeScreen --collectAsState--> HomeViewModel.state: StateFlow<HomeUiState>
HomeViewModel --invoke--> GetTrendingUseCase(category): Flow<UiState<List<VideoSummary>>>
UseCase --repo.trending--> VideoRepository (domain interface)
VideoRepositoryImpl --api.trending/search/popular--> InvidiousApi (Retrofit)
DTO --VideoMapper--> VideoSummary (URL sudah ditulis ulang UrlRewriter)
Galat jaringan/HTTP --runCatching + toFriendlyMessage--> UiState.Error
```

`SearchViewModel` menambahkan: debounce saran 350 ms (sama seperti web), pembatalan job pencarian lama, dan filter Shorts (`duration=short`). `PlayerViewModel` membaca `videoId` dari `SavedStateHandle` — selamat dari process death.

## 8. Tema — Material 3 Dark, Token Persis Web

`presentation/theme/Theme.kt`: skema gelap dibangun dari token CSS web app —
background `#0A0B0D`, surface `#101114`, surface-variant/chip `#1C1E23`, outline `#23252B`,
teks `#F3F4F6`, teks redup `#9AA0AB`, aksen primer `#FF2E4D`, aksen sekunder `#FF6A3D`.
`MainActivity` memanggil `YouTubeGOTheme(darkTheme = true)` — **gelap adalah default yang dipaksa**, bukan mengikuti sistem (skema terang tersedia sebagai opsi pengaturan tahap 2). Tipografi & bentuk di `TypeAndShape.kt` (sudut kartu 14dp, chip 10dp, pil 20dp — sama dengan web).

## 9. Strategi Hemat Data (ciri khas "GO")

1. Stream gabungan kualitas rendah lebih dulu; batas TrackSelector 720p/128 kbps.
2. Cache HTTP OkHttp 10 MB + cache Coil (memori & disk) untuk JSON/thumbnail berulang.
3. Thumbnail dipilih satu kualitas terbaik yang tersedia saja (`high > sd > maxres > default`, bukan `medium` buram) dan dimuat `Crop` seukuran kartu.
4. Tanpa autoplay video di Beranda (hanya gambar diam); Shorts memuat detail hanya saat slide aktif (pola IntersectionObserver web diterjemahkan ke pager tahap 2).
5. Semua panggilan berbatas waktu tegas (connect 15 dtk, read 25 dtk) — tidak ada panggilan tanpa batas waktu.

## 10. Yang Belum Ada — Peta Jalan Tahap 2 (jujur, belum diklaim selesai)

- Shorts: pager vertikal (`VerticalPager`) memakai `/search?duration=short` — use case & parameternya sudah siap.
- DASH adaptif dari `dashUrl` + pemilih kualitas manual di pemutar.
- Akun lokal persisten: langganan/riwayat/Tonton Nanti/Disukai dengan DataStore/Room (di web: `localStorage yg_*`) + layar Subscription/Anda/Notifikasi seperti web.
- Unduhan resmi Media3 `DownloadManager` dari `formatStreams` (di web: unduh berkas asli).
- Subtitle: trek `captions[]` dipasang sebagai `MediaItem.SubtitleConfiguration` (DTO & mapper sudah menyediakan URL-nya).
- PiP + putar latar via `media3-session` (dependensi sudah terpasang).
- Komentar: endpoint & DTO sudah ada (`comments()` di repository), layar komentar belum dibuat.
- Gradle Wrapper belum dibuat di VM ini (tidak ada Gradle/SDK lokal) — workflow Actions memakai Gradle 8.10.2 dari `setup-gradle`; wrapper bisa dibuat sekali dari Android Studio/CI bila diinginkan.
- **Kode kerangka ini belum dikompilasi** saat blueprint ditulis (tidak ada toolchain Android di lingkungan agen). Verifikasi pertamanya adalah menjalankan workflow **Build Debug APK** (`workflow_dispatch`) di repo ini; perbaikan kompilasi mengikuti hasil run itu.

## 11. Build & Rilis

- Debug: GitHub Actions → **Build Debug APK** → Run workflow → artefak `youtube-go-debug` (diunduh dari HP, tanpa PC).
- Rilis: hanya atas perintah pemilik proyek; keystore tidak pernah disimpan di repo.
