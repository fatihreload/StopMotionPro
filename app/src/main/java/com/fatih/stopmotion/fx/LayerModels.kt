package com.fatih.stopmotion.fx

import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.data.LayerKind
import java.io.File
import java.util.Locale

/** Posisi layer pada bingkai: pusat (cx, cy) dalam piksel, lebar/tinggi, dan putaran dalam derajat. */
data class LayerRect(val cx: Float, val cy: Float, val w: Float, val h: Float, val rotation: Float)

/** Rumus waktu dan geometri layer. Dipakai sama oleh pratinjau dan ekspor, dan tidak bergantung pada Android. */
object LayerTiming {
    const val MAX_LAYERS = 20

    fun tickMs(tick: Int, fps: Int): Long = tick * 1000L / fps.coerceAtLeast(1)

    fun filmMs(ticks: Int, fps: Int): Long = ticks * 1000L / fps.coerceAtLeast(1)

    /**
     * Indeks frame sumber yang tampil pada waktu film tMs, atau null bila layer tidak tampil
     * (tersembunyi, belum mulai, sudah selesai, atau klip habis dan tidak diulang).
     */
    fun frameIndex(l: LayerEntity, tMs: Long): Int? {
        if (!l.visible || l.durationMs <= 0) return null
        val local = tMs - l.startMs
        if (local < 0 || local >= l.durationMs) return null
        if (l.kind == LayerKind.IMAGE || l.frameCount <= 1) return 0
        val raw = (local * l.frameRate / 1000f).toInt()
        return if (l.loopClip) raw % l.frameCount else if (raw >= l.frameCount) null else raw
    }
}

object LayerGeometry {
    /** Geometri dari pose t (hasil KeyframeMath.sample); bentuk mengikuti rasio sumber layer. */
    fun rect(l: LayerEntity, t: LayerTransform, frameW: Float, frameH: Float): LayerRect {
        val aspect = l.srcWidth.coerceAtLeast(1) / l.srcHeight.coerceAtLeast(1).toFloat()
        val w = t.scale * frameW
        return LayerRect(t.posX * frameW, t.posY * frameH, w, w / aspect, t.rotation)
    }

    /** Geometri dari pose statis layer (tanpa animasi). */
    fun rect(l: LayerEntity, frameW: Float, frameH: Float): LayerRect =
        rect(l, LayerTransform(l.posX, l.posY, l.scale, l.rotation, l.opacity), frameW, frameH)
}

object LayerFiles {
    /** Berkas gambar untuk frame ke-index sebuah layer. */
    fun framePath(l: LayerEntity, index: Int): String? = when (l.kind) {
        LayerKind.IMAGE -> l.filePath
        else -> l.frameDir?.let { File(it, String.format(Locale.US, "%05d.jpg", index + 1)).path }
    }
}
