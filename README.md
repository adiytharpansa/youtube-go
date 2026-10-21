# YouTube GO : Next — Android Native (Kotlin)

Aplikasi Android native dari konsep web app **YouTube GO**: minimalis, cepat, hemat data, Dark Theme default.
Arsitektur: **MVVM + Clean Architecture** (data / domain / presentation), Hilt, Media3 ExoPlayer, Coil, NewPipeExtractor, Kotlinx Serialization, Coroutines & Flow.

**API:** tanpa server perantara — semua data & stream diekstrak langsung di perangkat dari YouTube memakai NewPipeExtractor (sejak v1.45; jalur Invidious/proksi Vercel sudah dibuang).
Rincian lengkap ada di [BLUEPRINT.md](BLUEPRINT.md).

## Baru di v1.50 - ikon aplikasi baru
- Ikon adaptif modern: dua segitiga play (maju cepat = "go") pada gradien merah. Dibuat sebagai vector drawable (`ic_launcher_background/foreground/monochrome`) sehingga tajam di semua densitas, termasuk ikon bertema Android 13. PNG cadangan untuk Android 7.x ada di `mipmap-*`. Tanda yang sama dipakai di bar atas Beranda (`ic_logo_mark`).

## Baru di v1.49 - teks CC, komentar, geser kecilkan interaktif
- **Teks CC**: Pengaturan pemutar punya "Ukuran teks CC" (Kecil..Sangat besar) dan "Gaya teks CC" (latar transparan, tanpa latar, kuning, latar hitam); pilihan tersimpan.
- **Komentar**: urut Teratas / Terbaru (dari komentar yang sudah dimuat), penanda waktu di komentar bisa diketuk untuk melompat, memuat ~2 halaman komentar.
- **Geser turun di pemutar** (portrait): layar ikut turun & mengecil mengikuti jari; lepas melewati ambang = kecilkan ke mini player, selain itu kembali.

## Baru di v1.48 - playlist berurutan, bilah lanjut menonton, bagikan dengan waktu, kunci layar
- **Playlist berurutan**: putar dari layar playlist (tombol putar atau ketuk video); Sebelumnya/Berikutnya di pemutar & notifikasi mengikuti urutan playlist, panel playlist tampil di halaman pemutar. Keluar otomatis saat pindah ke video di luar playlist.
- **Bilah merah** di dasar thumbnail untuk video yang sudah sebagian ditonton (semua daftar video).
- **Bagikan**: bila sudah ditonton >= 5 dtk, ada pilihan "Mulai dari 1:23" (link `?t=`).
- **Kunci layar** di layar penuh (ikon gembok di bar atas): gestur & kontrol nonaktif, ketuk untuk memunculkan tombol buka kunci.

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

## Baru di v1.51 - polesan tampilan
- **Font**: judul layar, tombol, chip, dan label navigasi memakai Poppins (res/font; lisensi SIL OFL 1.1). Badan teks dan judul video tetap font sistem agar padat. Satu titik pengganti ada di `theme/TypeAndShape.kt` (`YgDisplayFamily`).
- **Langganan**: header "Langganan" selalu tampil; keadaan kosong punya baris "Channel populer" (dari video trending asli) dengan tombol Langganan langsung.
- **Empty state**: cahaya aksen + garis tepi pada ikon, animasi masuk (pudar + geser naik), teks dibatasi lebarnya, isi bergulir di layar pendek.
- **Tombol utama**: bayangan aksen lembut dan getar halus saat disentuh.
- **Navigasi bawah**: getar halus saat pindah tab; mengetuk ulang tab aktif (Beranda / Langganan) menggulir ke atas.

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
