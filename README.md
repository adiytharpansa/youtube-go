# YouTube GO — Android Native (Kotlin)

Aplikasi Android native dari konsep web app **YouTube GO**: minimalis, cepat, hemat data, Dark Theme default.
Arsitektur: **MVVM + Clean Architecture** (data / domain / presentation), Hilt, Media3 ExoPlayer, Coil, Retrofit + Kotlinx Serialization, Coroutines & Flow.

**API:** Invidious API sesuai dokumen yang diberikan pemilik proyek — semua endpoint JSON di bawah `/api/v1`
melalui proksi Vercel YouTube GO yang sudah live (thumbnail & stream wajib lewat proksi karena dipagari anti-bot Anubis).
Rincian lengkap ada di [BLUEPRINT.md](BLUEPRINT.md).

## Build debug (di GitHub Actions, tanpa PC)
Actions → **Build Debug APK** → Run workflow → unduh artefak `youtube-go-debug`.

## Halaman & fitur (v1.43)
- **Beranda**: gabungan beberapa sumber per kategori + scroll tak terbatas (halaman lanjutan, deduplikasi, footer "Coba lagi"/"Sudah semua"). Tidak ada video yang dibuang dari feed "Untuk Anda" (yang sudah ditonton dipindah ke belakang).
- **Shorts**: pemutar vertikal (geser atas/bawah), suka/simpan/ikuti/bagikan, halaman tak terbatas.
- **Langganan**: umpan video terbaru dari channel yang diikuti + deret avatar; **Channel**: banner, tombol ikuti, video terbaru.
- **Anda**: Riwayat, Tonton nanti, Video disukai, Unduhan (offline, bisa dilanjutkan), Playlist (buat/ubah nama/hapus), Pengaturan.
- Menu titik tiga di semua kartu video: Tonton nanti, Simpan ke playlist, Unduh, Bagikan.
