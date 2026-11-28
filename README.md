# Fatih Stop Motion Studio — Tahap 1

Fondasi: desain dark, halaman utama, izin kamera, live preview CameraX,
tombol shutter, penyimpanan frame (file + Room), thumbnail, frame counter.

## Build lewat GitHub Actions (tanpa Android Studio)
1. Buat repository baru di GitHub, upload seluruh isi folder ini (termasuk folder `.github`).
2. Push ke branch `main` (atau tab Actions -> "Build APK" -> Run workflow).
3. Setelah selesai, buka run tersebut -> Artifacts -> `Fatih-Stop-Motion-Studio` -> unduh, ekstrak, pasang `app-debug.apk`.

Workflow memakai `gradle/actions/setup-gradle` dengan Gradle 8.9, jadi Gradle Wrapper tidak wajib ada.

## Build lokal
Buka folder di Android Studio (Koala atau lebih baru, JDK 17) lalu Run.

## Struktur
- `data/` : Room (proyek, frame), ProjectStorage (file foto/thumbnail), ProjectRepository
- `ui/home/` : halaman utama
- `ui/camera/` : kamera, shutter, timer, grid, flash, film strip
- `ui/theme/`, `ui/navigation/`

## Tahap 2 (v0.2.0)
Onion skin (opacity 0-100%, 1-5 frame, 4 warna, on/off, tersimpan per proyek) dan manajemen frame
(hapus, duplikat, geser, durasi, ganti foto, sisip sebelum/sesudah, pilih banyak).
Database naik ke versi 2 dengan migrasi, proyek Tahap 1 tetap aman.

## Tahap 3 (v0.3.0)
- Studio animasi (ikon PlayCircle di kamera): playback Play/Pause/Stop, Sekali/Loop/Ping-pong,
  FPS 1-60, kecepatan 0.25x-2x, slider seek, durasi, timeline (zoom, pilih banyak, drag and drop,
  hapus, duplikat), undo dan redo.
- Auto capture (ikon Timelapse di kamera): interval 1/2/5/10 detik atau custom, jumlah foto,
  Start/Pause/Stop. Berhenti otomatis saat aplikasi ke latar belakang atau halaman ditinggalkan.
- Undo/redo juga ada di halaman Frame. Riwayat undo berlaku selama proyek terbuka.

## Tahap 4 (v0.4.0)
- **Editor foto** (tombol "Edit Foto" di halaman Frame dan di Studio Animasi): filter (Hitam Putih, Sepia, Vivid,
  Dingin, Hangat, Film, Negatif) dengan kekuatan, kecerahan, kontras, saturasi, suhu warna, putar, balik,
  potong (rasio 1:1, 3:4, 4:3, 9:16, 16:9, zoom, geser). Tahan gambar untuk melihat versi asli.
  Hasil disimpan sebagai file baru (foto asli aman) dan bisa di-undo. Opsi "Semua frame" memproses seluruh proyek
  sebagai satu langkah undo.
- **Studio Audio** (tombol "Audio" di Studio Animasi): impor musik/audio, rekam suara (mikrofon), efek suara bawaan
  (Ding, Pop, Klik, Whoosh, Boing, Drum). Per track: volume, posisi mulai, potong awal/akhir, fade in/out, bisukan,
  pratinjau, hapus. Audio ikut diputar mengikuti playback (Play/Pause/Seek/Loop, dan kecepatan).
- **Efek film** (tombol "Efek" di Studio Animasi): filter, vignette, fade in/out (ke hitam atau putih).
  Efek bersifat non-destruktif (tidak mengubah foto) dan tersimpan per proyek.
- Database naik ke versi 4 dengan migrasi (proyek Tahap 1 sampai 3 tetap aman).
- Perbaikan dari Tahap 3: `ProjectDao.updateFps` terdefinisi dua kali, serta `FrameDao.update/insertAll/deleteByIds`
  dan `ProjectRepository.purgeAllDeleted` yang dipakai tetapi belum ada.
- Izin baru: `RECORD_AUDIO` (diminta saat pertama kali menekan Rekam).

## Tahap 5 (v0.5.0)
- **Ekspor** (ikon film di pojok kanan atas Studio Animasi):
  - **MP4**: H.264 + audio AAC. Ukuran 480p/720p/1080p (sisi pendek, rasio mengikuti foto pertama),
    kualitas Hemat/Seimbang/Tinggi, audio dari Studio Audio dicampur (posisi, potongan, volume, fade).
  - **GIF**: palet 256 warna (median cut) dengan dithering ringan, loop opsional, frame identik digabung
    menjadi satu frame dengan jeda lebih lama agar file lebih kecil.
  - **Foto (ZIP)**: semua foto frame (versi hasil edit) sebagai JPG berurutan.
- Efek film (filter, vignette, fade) ikut diterapkan pada MP4 dan GIF, dengan rumus yang sama seperti pratinjau.
- Hasil bisa **Disimpan** (pilih lokasi), **Dibagikan**, atau **Dibuka** di aplikasi lain.
- Ekspor berjalan dengan layar tetap menyala, ada progres dan tombol batal, dan hasil sementara dibersihkan.
- Tidak ada perubahan database dan tidak ada izin baru (penyimpanan lewat pemilih file dan FileProvider).

## Tahap 6 (v0.6.0)

### Perbaikan warna teks
Tombol berlatar merah (Proyek Baru, Simpan, Mulai ekspor, Rekam, dst.) memakai warna teks bawaan Material
`onTertiary` yang gelap, sehingga hampir tidak terbaca. Sekarang seluruh pasangan warna `on*` ditetapkan eksplisit
di `ui/theme/Theme.kt` dan semua tombol berwarna memakai `FatihColors.OnAccent` (putih).

### Manajemen proyek
Ganti nama, duplikat, cari dan urutkan, info proyek (jumlah frame, durasi, audio, ukuran), cadangan ke satu file ZIP,
impor cadangan, dan pembersihan hasil ekspor sudah tersedia dan ikut membawa audio serta efek film.

### Pengujian
- **Unit test** (`app/src/test`, jalan di JVM, tanpa emulator): nama proyek, filter dan urutan, manifest cadangan
  (round-trip dan data rusak), riwayat undo/redo, ukuran dan bitrate ekspor, fade audio, efek suara bawaan, model efek.
  Jalankan: `gradle testDebugUnitTest`.
- **Instrumented test** (`app/src/androidTest`, perlu emulator/perangkat): Room, hapus berantai, efek tersimpan,
  hapus lunak, cadangan lalu pulihkan, dan duplikat proyek. Memakai folder sementara, tidak menyentuh data asli.
  Jalankan: `gradle connectedDebugAndroidTest` atau workflow **Instrumented tests** di GitHub.

### Optimasi
- Build rilis memakai R8 (`isMinifyEnabled`) dan `shrinkResources`; aturan di `app/proguard-rules.pro`.
  Nomor baris dipertahankan, `mapping.txt` ikut diunggah untuk membaca stack trace.
- Hanya resource bahasa Indonesia dan Inggris yang dikemas.
- `allowBackup` dimatikan: auto-backup Android hanya mencadangkan database tanpa foto, sehingga proyek menjadi
  rusak setelah pasang ulang. Gunakan fitur Cadangan di aplikasi.
- Gradle: build paralel dan cache build aktif.

### GitHub Actions
| Workflow | Kapan | Isi |
|---|---|---|
| `build.yml` (CI) | push ke `main`, pull request, manual | unit test, lint, APK debug dan APK rilis (R8), ringkasan test |
| `release.yml` | tag `v*`, manual | test, APK rilis, mapping.txt, GitHub Release otomatis |
| `instrumented.yml` | manual | instrumented test di emulator Android (API 30) |

**Membuat rilis:** ubah `versionName` di `app/build.gradle.kts` (mis. `0.6.1`), commit, lalu
`git tag v0.6.1 && git push origin v0.6.1`. Tag harus sama dengan `versionName`.

**Tanda tangan rilis (opsional).** Tanpa pengaturan di bawah, APK rilis ditandatangani dengan kunci debug (cukup untuk uji
coba, tidak untuk Play Store). Untuk kunci sendiri:
1. `keytool -genkeypair -v -keystore release.jks -alias fatih -keyalg RSA -keysize 2048 -validity 10000`
2. `base64 -w0 release.jks` lalu simpan sebagai secret `KEYSTORE_BASE64`.
3. Tambahkan secret `KEYSTORE_PASSWORD`, `KEY_ALIAS`, dan `KEY_PASSWORD`.

Simpan file `release.jks` di tempat aman (jangan di-commit). Jika hilang, pembaruan aplikasi tidak bisa ditandatangani lagi.

## Tahap 7 (v0.7.0)

### Penghapus latar green screen / blue screen
- Tombol **Latar** di Studio Animasi. Pilih Green screen atau Blue screen, atur **Toleransi** (makin tinggi makin banyak
  warna layar yang dihapus) dan **Tepi halus**.
- Latar pengganti: warna polos (Hitam, Putih, Abu-abu, Langit) atau **gambar dari galeri** (disalin dan dikecilkan ke
  folder proyek).
- Non-destruktif: foto asli tidak diubah. Berlaku di pratinjau, **MP4 dan GIF**. Pantulan warna layar (spill) pada tepi
  objek dikurangi. Pratinjau memproses gambar lebih kecil (640 px) agar cepat, jadi tepi hasil ekspor lebih halus.
- Ikut dalam cadangan, impor, dan duplikat proyek (termasuk gambar latar).

### Kualitas SD, HD, HD+, 2K, 4K
| Nama | Sisi pendek | Foto kamera (4:3) |
|---|---|---|
| SD | 480 | 640x480 |
| HD | 720 | 960x720 |
| HD+ | 1080 | 1440x1080 |
| 2K | 1440 | 1920x1440 |
| 4K | 2160 | 2880x2160 |

- **Kamera:** ikon kualitas di bilah atas kamera memilih ukuran foto (ada juga **Maks** = resolusi tertinggi kamera,
  bawaan). Bila kamera tidak punya ukuran persis itu, dipakai ukuran terdekat yang lebih besar. Berlaku untuk semua
  proyek dan tersimpan.
- **Ekspor MP4:** SD sampai 4K. Ukuran yang tidak didukung encoder video perangkat ditampilkan redup. Aplikasi memberi
  peringatan bila foto asli lebih kecil dari ukuran hasil (diperbesar, tidak lebih tajam).
- Bitrate maksimum naik ke 40 Mbps untuk 4K; `largeHeap` diaktifkan agar ekspor 4K tidak kehabisan memori.

### Suara shutter
- Klik shutter kamera bawaan sistem diputar setiap foto diambil (juga pada auto capture). Bisa dimatikan di panel
  kualitas kamera; pilihan tersimpan. Mengikuti pengaturan volume/senyap perangkat.
- Efek **Shutter kamera** juga tersedia di Studio Audio (Efek suara) untuk ditambahkan ke film.

### Lain-lain
- Database naik ke versi 5 (migrasi 4 ke 5, data lama aman).
- Tes baru: algoritma chroma key (piksel hijau/biru/kulit/gelap/tepi), state dan cache key, daftar kualitas kamera dan
  ekspor, cadangan dengan gambar latar, dan efek suara shutter.

## v0.7.1 (penyesuaian tampilan)
- **Studio Audio:** tombol rekam kini hanya berupa ikon mikrofon (teks "Rekam"/"Selesai" dihapus). Fungsinya tetap sama,
  dan penanda "Merekam 0:00" tetap muncul selama merekam.
- **Kamera:** fitur timer (3/5/10 detik) dihapus karena sudah ada Auto Capture.
- **Hapus latar** dipindahkan dari Studio Animasi ke kamera, menempati posisi tombol timer (ikon wallpaper, ungu bila
  aktif). Pengaturannya tetap tersimpan per proyek; pratinjau di Studio Animasi serta ekspor MP4/GIF tetap memakainya.

## Tahap 8 (v0.8.0)

### Chroma key otomatis di kamera
- Saat Green/Blue screen aktif (tombol **Hapus latar** di kamera), pratinjau kamera langsung menampilkan objek tanpa
  latar, di atas latar pilihan Anda, jadi komposisi bisa diatur sebelum memotret. Foto yang disimpan tetap foto asli
  (non-destruktif); latar dihapus lagi dengan kualitas penuh di editor dan ekspor.
- Pratinjau kamera memakai gambar analisis sekitar 640x480 agar lancar; bila kamera tidak mendukung kombinasi ini,
  aplikasi memberi tahu dan chroma key tetap bekerja di editor/ekspor.

### Kualitas chroma key (tidak burik)
Pipeline `ChromaKey.keyImage`, dipakai sama persis oleh kamera, pratinjau editor, dan ekspor:
1. Peta dominasi warna per piksel dinormalisasi terhadap kecerahan (bayangan pada layar ikut terhapus).
2. Peta dihaluskan untuk membuang bintik noise JPEG pada layar; alpha memakai yang lebih ketat dari peta asli dan peta halus
   sehingga tidak ada pinggiran hijau/biru selebar satu piksel.
3. Tepi dilembutkan ke arah dalam objek (rata-rata alpha dan erosinya), bukan melebar ke arah layar.
4. Piksel tepi campuran dibersihkan dari warna layar (unmix), sisa pantulan (spill) pada objek dikurangi.
5. Area sangat gelap dipertahankan; gambar tanpa layar tidak dirusak.

### Erase (hapus bagian yang tidak perlu)
- Tombol **Erase** di Studio Animasi membuka kanvas erase untuk frame yang sedang tampil. Cocok untuk membuang tangan,
  batang penyangga, atau benang saat membuat karakter melompat.
- Kuas hapus dan **pulihkan**, ukuran kuas, undo/redo, bersihkan, dan opsi menerapkan coretan ke **semua frame**.
- Non-destruktif: coretan disimpan sebagai data kecil per frame (bukan mengubah foto), dinormalisasi sehingga sama di
  pratinjau kecil maupun ekspor 4K. Berlaku bersama chroma key; tanpa chroma key pun area yang dihapus diganti latar
  pilihan. Ikut dalam undo/redo, cadangan, impor, duplikat, dan ekspor MP4/GIF.
- Mengedit foto dengan putar/potong menghapus coretan frame itu (posisinya tidak lagi cocok); edit warna saja tidak.

### Lain-lain
- Database naik ke versi 6 (kolom `eraseData`, migrasi 5 ke 6).
- Tes baru: kualitas chroma key pada gambar utuh (layar hilang, objek utuh, tepi tanpa pinggiran, noise dibuang, gambar
  tanpa layar tidak rusak), codec coretan erase, dan cadangan yang membawa data erase.

## v0.8.1 (perbaikan Erase)
- **Latar bersih:** di layar Erase ada tombol *Latar bersih*. Pilih frame yang tidak ada karakter/penyangganya (atau foto
  dari galeri); area yang dihapus lalu menampilkan foto itu, bukan lubang hitam. Cocok untuk karakter melompat di latar
  biasa tanpa green screen. Gunakan frame dengan posisi kamera yang sama.
- Pilihan **Latar pengganti** di panel Hapus latar kini selalu tampil (sebelumnya hanya saat green/blue screen aktif),
  karena juga dipakai oleh Erase.
- Tombol *Bayangan* tidak lagi menyala bawaan (membuat bagian yang sudah dihapus tampak belum terhapus), dan ada petunjuk
  di atas kanvas.

### Cara memakai Erase
1. Studio Animasi > pilih frame di timeline > tombol **Erase**.
2. Geser jari di foto pada bagian yang tidak diinginkan (tangan, batang, kawat). Atur ukuran dengan slider **Kuas**.
3. Salah hapus: ketuk **Pulihkan** lalu coret bagian itu, atau pakai undo di pojok kanan atas.
4. Pilih **Latar bersih** bila latar belakangnya foto biasa, lalu **Simpan**. Aktifkan **Semua frame** bila penyangganya
   diam di tempat yang sama pada semua frame.

## v0.8.2 (membuat benda melayang)
Untuk menghilangkan tangan yang memegang benda, area yang dihapus harus diganti foto latar yang **sama tanpa tangan dan
benda**. Di layar Erase, *Latar bersih* > **Foto latar bersih sekarang** membuka kamera (mode khusus, tidak menambah frame),
memotret, lalu kembali ke Erase dengan latar bersih sudah terpasang. Coretan yang belum disimpan tetap terjaga.

Cara kerja yang disarankan: letakkan kamera (sebaiknya di tripod) -> foto latar kosong lebih dulu -> pegang benda dan ambil
frame animasi seperti biasa -> di Erase, coret hanya tangan -> Simpan. Pilih **Semua frame** bila gerak tangan serupa.

## v0.8.3 (perbaikan tampilan Erase)
- Kanvas Erase digambar ulang dengan cara bawaan Compose (lapisan foto dikali mask, coretan memakai `BlendMode.Clear`)
  sehingga tampilan pasti ter-update saat mencoret; sebelumnya memakai bitmap yang diubah-ubah.
- **Kotak-kotak aktif bawaan**: bagian yang dihapus langsung tampak transparan. Matikan untuk melihat hasil akhir dengan latar.
- Mask di pratinjau/ekspor kini ARGB beresolusi maksimal 1600 px lalu diskalakan ke ukuran foto (hemat memori pada 4K).
- Dialog Latar bersih punya tombol **Lepas latar** untuk membuang foto latar yang terlanjur dipasang.

## v0.8.4 (panel Hapus latar yang tidak menutupi layar)
- Lembar penuh (bottom sheet) diganti **panel ringkas di atas pratinjau**, seperti panel Onion dan Kualitas. Tingginya
  sekitar 140 dp, dengan dua tab: **Layar** (Mati/Hijau/Biru, Toleransi, Tepi halus) dan **Latar** (warna atau gambar).
- Saat slider disentuh, panel menjadi hampir transparan sehingga efek chroma key terlihat langsung di bawah jari.
- Tombol panah di panel mengciutkan panel menjadi satu baris; tombol X menutupnya.
- **Studio Animasi** kini punya tombol **Chroma** di baris alat (baris alat bisa digeser ke samping). Pengaturannya
  sama dengan di kamera dan langsung terlihat di pratinjau.

## v0.8.5 (zoom di layar Erase)
- Layar Erase kini bisa di-**zoom dengan cubitan dua jari** (1x sampai 6x) dan **digeser ke kanan, kiri, atas, bawah**
  dengan dua jari. Satu jari tetap dipakai untuk menghapus/memulihkan. Bila jari kedua menyentuh saat satu jari sedang
  mencoret, coretan itu dibatalkan (tidak meninggalkan titik) lalu berganti ke zoom.
- Ukuran kuas di layar tetap sama saat zoom, jadi makin besar zoom, coretan di gambar makin halus (cocok untuk jari dan
  kawat kecil). Hasil coretan tetap tersimpan dalam koordinat gambar sehingga sama di semua zoom dan di ekspor.
- Chip **Zoom** di baris alat menampilkan skala dan mengembalikan ke 1x saat diketuk.

## v0.8.6
- **Panel Hapus latar:** chip Mati/Hijau/Biru selalu satu baris (teks "Biru" tidak lagi turun huruf demi huruf pada panel
  sempit di Studio Animasi); bila layar sangat sempit barisnya bisa digeser.
- **Kamera:** ikon kualitas tidak lagi selalu "HQ". Sekarang berupa lencana yang menampilkan pilihan saat ini:
  SD, HD, HD+, 2K, 4K, atau MAX.

## v0.9.0 (Layer dan Klip)
- Tombol **Layer** di baris alat Studio Animasi membuka panel layer di bawah pratinjau. **+ Layer** menambah:
  - **Gambar** (PNG transparan didukung), atau
  - **Klip video**, dengan opsi *Tanpa chroma / Hijau / Biru* saat menambah. Cocok untuk efek berlatar green screen,
    misalnya peluru pada stop motion tembakan.
- **Banyak layer** (sampai 20 per proyek). Urutan tumpukan bisa dinaikkan/diturunkan; layer lain di atas foto, di bawah
  vignette dan fade.
- Per layer: **Waktu** (mulai, lama, "Mulai di sini", ulangi klip), **Tampilan** (ukuran, putar, opasitas, tengah),
  **Chroma** (Mati/Hijau/Biru, toleransi, tepi halus, memakai pipeline chroma key yang sama dengan foto), dan **Atur**
  (urutan, sembunyikan, hapus).
- Atur langsung di pratinjau: **geser** untuk memindah, **cubit** untuk ukuran, **putar dua jari** untuk memutar.
- Klip diekstrak sekali menjadi urutan JPEG (fps proyek, maksimal 30 fps dan 240 frame), jadi pratinjau dan ekspor tidak
  bergantung pada decoder video. Klip yang lebih panjang dipotong. Suara klip tidak ikut; tambahkan lewat Studio Audio.
- Ikut dalam **ekspor MP4 dan GIF** (rumus waktu dan posisi sama dengan pratinjau), **cadangan/impor**, dan **duplikat**
  proyek. Database naik ke versi 7 (migrasi 6 ke 7).
- Tes baru: waktu dan geometri layer, nama berkas, dan cadangan yang membawa layer (termasuk data rusak).

## v0.10.0 (keyframe layer dan kamera manual)

### Keyframe pada layer
- Panel Layer punya tab **Animasi**. Geser playhead ke waktu yang diinginkan, atur layer di pratinjau (geser, cubit,
  putar) atau lewat tab Tampilan, lalu ketuk **Mulai animasi di sini** / **+ Keyframe di sini**. Setelah layer punya
  keyframe, setiap perubahan pada waktu tertentu otomatis membuat atau memperbarui keyframe di waktu itu.
- Yang dianimasikan: posisi, ukuran, putaran, dan opasitas. Gerak ke keyframe berikutnya bisa **Lurus**, **Halus**
  (mulai dan berhenti perlahan), atau **Tahan** (tetap lalu melompat).
- **Hapus keyframe** menghapus satu titik (keyframe terakhir menjadi pose statis). **Hapus animasi** membekukan layer
  di pose saat ini.
- Pratinjau, ekspor MP4/GIF, cadangan, impor, dan duplikat proyek memakai rumus interpolasi yang sama. Maksimal 60
  keyframe per layer.

### Kamera manual (tombol Tune di bilah atas kamera)
| Tab | Isi |
|---|---|
| Fokus | **Auto Focus**, **Manual Focus** (slider dekat sampai jauh), **Lock Focus & Exposure**, **Lock Focus + Manual Exposure** |
| AW | Auto, Locked, Incandescent, Fluorescent, Daylight, Cloudy, Twilight, Shade |
| EV | Kompensasi eksposur sesuai langkah kamera |
| Zoom | 1.0x sampai 10.0x (dibatasi kemampuan kamera) |
| Shutter | 1/4000 sampai 1/4 detik, aktif pada Lock Focus + Manual Exposure |
| ISO | 50 sampai 3200 (dibatasi kamera), aktif pada Lock Focus + Manual Exposure |
- Lock Focus & Exposure mengunci fokus dan eksposur dari tengah bingkai (dan AW) agar kecerahan tidak kedip antar frame.
- Opsi yang tidak didukung kamera ditampilkan redup atau diganti otomatis (mis. fokus manual pada kamera fokus tetap).
- Pilihan tersimpan antar sesi dan diterapkan ulang setiap kamera dipasang ulang (ganti lensa, kualitas, atau chroma).
- Tombol bilah atas kamera dikecilkan (40 dp) agar delapan tombol muat di layar sempit.
- Tes baru: keyframe (codec, interpolasi, penyuntingan), model dan rencana kamera manual, dan cadangan yang membawa keyframe.

