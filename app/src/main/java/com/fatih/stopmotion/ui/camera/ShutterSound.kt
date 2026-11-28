package com.fatih.stopmotion.ui.camera

import android.media.MediaActionSound

/** Suara klik shutter kamera bawaan sistem (dimuat di awal agar tanpa jeda saat dipakai). */
class ShutterSound {
    private val sound: MediaActionSound? = try {
        MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    } catch (e: Exception) {
        null
    }

    fun play() {
        try {
            sound?.play(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            // suara tidak penting untuk hasil foto
        }
    }

    fun release() {
        try {
            sound?.release()
        } catch (e: Exception) {
        }
    }
}
