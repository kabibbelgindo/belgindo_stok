# Stok Barang PT. Belgindo Raya (APK Android)

Aplikasi Android **read-only** untuk memantau stok barang terkini dari
**database cloud Turso (libsql)** — dibuat khusus untuk HP pimpinan.

## Fitur

- Stok terkini per barang + ukuran + lokasi (sama dengan dashboard "Stok Barang" desktop)
- Sortir: **Nama barang / Ukuran / Lokasi** (naik–turun), plus pencarian nama/kode/lokasi
- Konfigurasi URL + token Turso diisi **sekali saat aplikasi pertama dibuka**
- Tanpa login tambahan; aplikasi tidak dapat mengubah data (read-only)
- Tombol muat ulang untuk sinkron tampilan dengan data terbaru

## Cara build APK di GitHub (tanpa Android Studio)

1. Upload seluruh isi folder ini ke repository GitHub.
2. Buka tab **Actions** → workflow **Build APK Stok BR** berjalan otomatis.
3. Setelah selesai (±5 menit), buka run tersebut → bagian **Artifacts** → unduh `StokBelgindoRaya-APK`.
4. Atau buat release permanen: buat tag `v1.0` → `Releases` berisi APK siap unduh.

> Panduan lengkap langkah demi langkah: lihat `PANDUAN-LENGKAP.md` pada paket ini.

## Teknologi

| Bagian | Pilihan |
|---|---|
| Bahasa/UI | Kotlin + Jetpack Compose (Material 3) |
| Min Android | 7.0 (API 24) |
| Koneksi DB | Turso Cloud — HTTP API Hrana v2 (OkHttp + org.json) |
| CI/CD | GitHub Actions: `gradle assembleDebug assembleRelease` (Gradle 8.9, JDK 17) |

Versi: AGP 8.5.2 · Kotlin 2.0.20 · Compose BOM 2024.09.03 · OkHttp 4.12.0

## Tanda tangan rilis

APK `release` pada workflow ini ditandatangani dengan kunci debug agar bisa
langsung dipasang. Untuk distribusi yang lebih serius, buat keystore sendiri
dan pasang sebagai GitHub Secrets, lalu sesuaikan bagian `signingConfig`
di `app/build.gradle.kts`.
