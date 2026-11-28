# Tahap 6 — Manajemen proyek, pengujian, optimasi, dan CI

Dokumen ini melengkapi proyek Tahap 5. Tahap 6 menyiapkan alur kerja dan pemeriksaan otomatis; tidak mengklaim bahwa fitur rendering Tahap 5 yang belum diverifikasi menjadi selesai.

## Manajemen proyek

- Simpan satu salinan ZIP yang diketahui bisa dibangun sebelum melakukan perubahan besar.
- Gunakan Git: commit kecil untuk satu perubahan, dan tag rilis setelah pengujian perangkat berhasil.
- Jangan memasukkan `local.properties`, keystore, kata sandi, token, atau file rahasia ke repository.
- Catat perangkat, versi Android, langkah reproduksi, hasil yang diharapkan, dan hasil aktual untuk setiap bug.

## GitHub Actions

Workflow `.github/workflows/build.yml` berjalan pada push dan pull request ke `main`, serta dapat dijalankan manual dari tab **Actions**. Ia:

1. Menyiapkan JDK 17 dan Gradle 8.9.
2. Membangun APK debug.
3. Menjalankan `testDebugUnitTest`.
4. Menjalankan `lintDebug`.
5. Mengunggah APK dan laporan lint sebagai artifacts.

Untuk memakai workflow, unggah isi folder proyek ini ke repository GitHub, pastikan workflow berada di `.github/workflows/build.yml`, lalu push ke `main`. Setelah workflow sukses, unduh APK dari bagian **Artifacts** pada run tersebut.

## Checklist pengujian manual

- [ ] Aplikasi terbuka dan proyek lama masih terlihat.
- [ ] Kamera mengambil frame dan frame tetap ada setelah aplikasi ditutup lalu dibuka kembali.
- [ ] Timeline, playback, FPS, undo, dan redo tidak crash.
- [ ] Editor gambar, audio, dan efek tetap dapat dibuka.
- [ ] MP4 diekspor, tersimpan, dan dapat diputar di galeri.
- [ ] Uji proyek kecil dan proyek dengan banyak frame; pastikan memori tidak cepat habis.
- [ ] Uji perangkat Android target dan catat versi Android serta hasilnya.

GIF dan sinkronisasi audio belum dianggap lulus hanya karena workflow build berhasil; tandai sebagai belum tersedia/terverifikasi sampai pengujian fitur tersendiri dilakukan.

## Catatan optimasi dan batasan

- Uji dahulu pada resolusi rendah dan proyek pendek sebelum memilih 1080p atau banyak frame.
- Hindari memuat semua bitmap resolusi penuh sekaligus; gunakan thumbnail/downsampling untuk pratinjau dan lepaskan resource setelah digunakan.
- Uji orientasi, izin penyimpanan/media, ruang penyimpanan, serta pembatalan render.
- CI hanya membangun dan menjalankan pemeriksaan otomatis. Ia tidak menggantikan uji kamera, codec, galeri, audio, atau performa pada perangkat fisik.
- Workflow belum membuat rilis bertanda tangan. Jangan menyimpan keystore atau kredensial di repository.
