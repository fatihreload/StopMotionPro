package com.fatih.stopmotion.ui.camera

/** Pengaturan kamera yang dapat diterapkan langsung saat sesi kamera aktif. */
data class CameraManualSettings(
    val focusMode: Int = 0, // 0 auto, 1 manual, 2 lock focus+AE, 3 lock focus + manual exposure
    val focusDistance: Float = 0f,
    val whiteBalance: Int = 0, // index dari pilihan UI
    val exposureCompensation: Int = 0,
    val zoom: Float = 1f,
    val iso: Int = 200,
    val shutterSeconds: Double = 1.0 / 60.0
)
