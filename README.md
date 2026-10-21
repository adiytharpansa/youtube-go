# YouTube GO — Android Native (Kotlin)

Aplikasi Android native dari konsep web app **YouTube GO**: minimalis, cepat, hemat data, Dark Theme default.
Arsitektur: **MVVM + Clean Architecture** (data / domain / presentation), Hilt, Media3 ExoPlayer, Coil, Retrofit + Kotlinx Serialization, Coroutines & Flow.

**API:** Invidious API sesuai dokumen yang diberikan pemilik proyek — semua endpoint JSON di bawah `/api/v1`
melalui proksi Vercel YouTube GO yang sudah live (thumbnail & stream wajib lewat proksi karena dipagari anti-bot Anubis).
Rincian lengkap ada di [BLUEPRINT.md](BLUEPRINT.md).

## Baru di v1.47 - lanjut menonton, antrean, hemat baterai
- **Lanjut menonton**: posisi tiap video disimpan permanen (tiap 10 dtk, saat jeda, saat keluar) dan dilanjutkan saat dibuka lagi; video yang hampir/sudah selesai mulai dari awal.
- **Antrean**: menu titik tiga di semua kartu video punya "Tambahkan ke antrean". Antrean tampil di halaman pemutar, diputar lebih dulu daripada rekomendasi (putar otomatis, tombol Berikutnya, notifikasi), bisa dihapus per item.
- Mini player: geser naik = buka pemutar penuh.
- Layar tidak lagi dipaksa menyala saat video dijeda.

## Baru di v1.46 - pemutar makin setara YouTube
- Cubit dua jari di pemutar: lebarkan = isi layar, rapatkan = sesuai layar.
- Geser turun di pemutar (portrait) = kecilkan ke mini player; mini player bisa digeser kiri/kanan untuk menutup.
- Ketuk judul bab di kontrol pemutar membuka daftar Bab; penanda waktu di deskripsi (mis. 1:23) bisa diketuk untuk melompat.
- Perbaikan: komentar yang menampilkan `&nbsp;` literal (HTML ter-escape dua kali) kini bersih.

## Baru di v1.45 - 100% di perangkat (tanpa server perantara)

- Semua data & putar kini diekstrak langsung di HP dengan NewPipeExtractor: beranda, pencarian,
  saran, detail, komentar, channel, dan stream. Tumpukan Invidious (instance publik) + proksi
  Vercel DIBUANG dari aplikasi - terukur tidak bisa diandalkan (stream gabungan kosong, manifes
  DASH cangkang kosong), dan permintaan dari IP pengguna tidak menabrak dinding pusat data.
- Pemutar: failover antar sumber - satu kali ambil URL segar, lalu pindah ke sumber berikutnya;
  pesan galat menampilkan kode HTTP sebenarnya. Log diagnosis bertag `YGO_PLAYER`
  (`adb logcat -s YGO_PLAYER`): hasil ekstraksi, sumber yang dipakai, dan penyebab gagal (host saja).

## Build debug (di GitHub Actions, tanpa PC)
Actions → **Build Debug APK** → Run workflow → unduh artefak `youtube-go-debug`.

## Baru di v1.44 - putar di latar belakang & streaming lebih lancar
- **Putar di latar belakang**: suara lanjut saat layar mati / ke Beranda, kontrol di notifikasi & layar kunci (Media3 `MediaSessionService`, foreground service `mediaPlayback`). Bisa dimatikan di Pengaturan.
- Pemutar tunggal bersama (`playback/PlayerHolder`), tidak lagi dibuat-buang per layar; trek video dimatikan otomatis di latar belakang (hemat kuota & baterai).
- Mulai putar tidak lagi menunggu server proksi; tidak restart dari 0 saat detail API tiba; otomatis ambil URL segar bila stream putus/403 dan lanjut dari posisi terakhir.
- Shorts: pra-ekstrak 2 Short berikutnya, fokus audio, tidak menyala sendiri setelah dijeda.

## Halaman & fitur (v1.43)
- **Beranda**: gabungan beberapa sumber per kategori + scroll tak terbatas (halaman lanjutan, deduplikasi, footer "Coba lagi"/"Sudah semua"). Tidak ada video yang dibuang dari feed "Untuk Anda" (yang sudah ditonton dipindah ke belakang).
- **Shorts**: pemutar vertikal (geser atas/bawah), suka/simpan/ikuti/bagikan, halaman tak terbatas.
- **Langganan**: umpan video terbaru dari channel yang diikuti + deret avatar; **Channel**: banner, tombol ikuti, video terbaru.
- **Anda**: Riwayat, Tonton nanti, Video disukai, Unduhan (offline, bisa dilanjutkan), Playlist (buat/ubah nama/hapus), Pengaturan.
- Menu titik tiga di semua kartu video: Tonton nanti, Simpan ke playlist, Unduh, Bagikan.
