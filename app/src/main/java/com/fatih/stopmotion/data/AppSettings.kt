package com.fatih.stopmotion.data

import android.content.Context

/** Pengaturan aplikasi yang berlaku untuk semua proyek (disimpan di SharedPreferences). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fatih_settings", Context.MODE_PRIVATE)

    /** Indeks CaptureQuality. Bawaan: tertinggi (indeks terakhir). */
    var captureQuality: Int
        get() = prefs.getInt(KEY_QUALITY, DEFAULT_QUALITY)
        set(value) { prefs.edit().putInt(KEY_QUALITY, value).apply() }

    /** Pengaturan kamera manual (lihat ProStateCodec), null = bawaan. */
    var proPrefs: String?
        get() = prefs.getString(KEY_PRO, null)
        set(value) { prefs.edit().putString(KEY_PRO, value).apply() }

    var shutterSound: Boolean
        get() = prefs.getBoolean(KEY_SHUTTER, true)
        set(value) { prefs.edit().putBoolean(KEY_SHUTTER, value).apply() }

    companion object {
        private const val KEY_QUALITY = "capture_quality"
        private const val KEY_SHUTTER = "shutter_sound"
        private const val KEY_PRO = "pro_camera"
        /** CaptureQuality.MAX */
        const val DEFAULT_QUALITY = 5
    }
}
