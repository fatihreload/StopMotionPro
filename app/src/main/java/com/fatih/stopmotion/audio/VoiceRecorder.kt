package com.fatih.stopmotion.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Perekam suara sederhana (mikrofon, AAC di wadah MP4/M4A). */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    @Suppress("DEPRECATION")
    private fun create(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    fun start(dest: File): Boolean {
        return try {
            val r = create()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(dest.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = dest
            true
        } catch (e: Exception) {
            cancel()
            dest.delete()
            false
        }
    }

    /** Amplitudo terakhir 0..32767. */
    val level: Int
        get() = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }

    /** Menghentikan rekaman. Mengembalikan file, atau null jika gagal (misalnya terlalu singkat). */
    fun stop(): File? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        return try {
            r.stop()
            r.release()
            f
        } catch (e: RuntimeException) {
            runCatching { r.release() }
            f?.delete()
            null
        }
    }

    /** Membatalkan dan membuang rekaman. */
    fun cancel() {
        val r = recorder
        val f = file
        recorder = null
        file = null
        if (r != null) {
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        f?.delete()
    }
}
