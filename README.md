# YouTube GO — Android Native (Kotlin)

Aplikasi Android native dari konsep web app **YouTube GO**: minimalis, cepat, hemat data, Dark Theme default.
Arsitektur: **MVVM + Clean Architecture** (data / domain / presentation), Hilt, Media3 ExoPlayer, Coil, Retrofit + Kotlinx Serialization, Coroutines & Flow.

**API:** Invidious API sesuai dokumen yang diberikan pemilik proyek — semua endpoint JSON di bawah `/api/v1`
melalui proksi Vercel YouTube GO yang sudah live (thumbnail & stream wajib lewat proksi karena dipagari anti-bot Anubis).
Rincian lengkap ada di [BLUEPRINT.md](BLUEPRINT.md).

## Build debug (di GitHub Actions, tanpa PC)
Actions → **Build Debug APK** → Run workflow → unduh artefak `youtube-go-debug`.
