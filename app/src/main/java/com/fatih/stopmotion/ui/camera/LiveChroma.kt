package com.fatih.stopmotion.ui.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.fatih.stopmotion.fx.ChromaKey
import com.fatih.stopmotion.fx.ChromaState

/**
 * Chroma key untuk pratinjau kamera langsung. Memakai pipeline yang sama dengan pratinjau editor dan ekspor
 * (ChromaKey.keyImage), hanya pada gambar analisis yang lebih kecil agar tetap lancar.
 */
object LiveChroma {

    /** Mengubah frame RGBA_8888 CameraX menjadi larik ARGB rapat (baris tanpa padding). */
    private fun pixels(proxy: ImageProxy): IntArray {
        val w = proxy.width
        val h = proxy.height
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val padded = Bitmap.createBitmap(plane.rowStride / pixelStride, h, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val px = IntArray(w * h)
        padded.getPixels(px, 0, w, 0, 0, w, h)
        padded.recycle()
        return px
    }

    /**
     * Frame kamera -> bitmap tegak ber-alpha (latar transparan). mirror = true untuk kamera depan,
     * supaya sama dengan pratinjau kamera yang dicerminkan.
     */
    fun process(proxy: ImageProxy, state: ChromaState, mirror: Boolean): Bitmap {
        val w = proxy.width
        val h = proxy.height
        val px = pixels(proxy)
        ChromaKey.keyImage(px, w, h, state.mode, state.tolerance, state.softness)
        val keyed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        keyed.setPixels(px, 0, w, 0, 0, w, h)

        val rotation = proxy.imageInfo.rotationDegrees
        if (rotation == 0 && !mirror) return keyed
        val m = Matrix()
        if (rotation != 0) m.postRotate(rotation.toFloat())
        if (mirror) m.postScale(-1f, 1f)
        val out = Bitmap.createBitmap(keyed, 0, 0, w, h, m, true)
        if (out !== keyed) keyed.recycle()
        return out
    }
}
