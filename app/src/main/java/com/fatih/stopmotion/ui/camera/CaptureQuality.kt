package com.fatih.stopmotion.ui.camera

/**
 * Kualitas foto saat memotret. Ukuran memakai rasio 4:3 (sama dengan bingkai proyek) dan mengikuti sisi pendek
 * yang sama dengan pilihan ekspor: SD 480, HD 720, HD+ 1080, 2K 1440, 4K 2160.
 * Bila kamera tidak punya ukuran persis itu, dipakai ukuran terdekat yang lebih besar (lalu yang lebih kecil).
 */
enum class CaptureQuality(val label: String, val width: Int, val height: Int) {
    SD("SD", 640, 480),
    HD("HD", 960, 720),
    HD_PLUS("HD+", 1440, 1080),
    K2("2K", 1920, 1440),
    K4("4K", 2880, 2160),
    /** Resolusi tertinggi yang didukung kamera (perilaku sebelumnya). */
    MAX("Maks", 0, 0);

    val hasTarget: Boolean get() = width > 0

    val detail: String get() = if (hasTarget) "${width}x$height" else "Tertinggi"

    companion object {
        fun fromIndex(index: Int): CaptureQuality = entries.getOrElse(index) { MAX }
    }
}
