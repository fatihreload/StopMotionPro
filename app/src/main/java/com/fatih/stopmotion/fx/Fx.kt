package com.fatih.stopmotion.fx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

val FilterNames = listOf("Normal", "Hitam Putih", "Sepia", "Vivid", "Dingin", "Hangat", "Film", "Negatif")

/** Efek tingkat proyek: dipakai di playback (dan nanti saat ekspor). */
data class FxState(
    val filter: Int = 0,
    val intensity: Float = 1f,
    val vignette: Float = 0f,
    /** Jumlah tick (1 tick = 1/FPS detik). */
    val fadeIn: Int = 0,
    val fadeOut: Int = 0,
    val fadeWhite: Boolean = false
) {
    val isDefault: Boolean get() = filter == 0 && vignette < 0.01f && fadeIn == 0 && fadeOut == 0
}

data class AspectOption(val label: String, val ratio: Float?)

val AspectOptions = listOf(
    AspectOption("Asli", null),
    AspectOption("1:1", 1f),
    AspectOption("3:4", 3f / 4f),
    AspectOption("4:3", 4f / 3f),
    AspectOption("9:16", 9f / 16f),
    AspectOption("16:9", 16f / 9f)
)

data class GeoParams(
    /** Putaran searah jarum jam, kelipatan 90 derajat. */
    val quarterTurns: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    /** Indeks AspectOptions. */
    val aspect: Int = 0,
    val zoom: Float = 1f,
    /** Geser area potong, -1..1. */
    val offX: Float = 0f,
    val offY: Float = 0f
) {
    val isIdentity: Boolean
        get() = ((quarterTurns % 4) + 4) % 4 == 0 && !flipH && !flipV && aspect == 0 && zoom <= 1.001f
}

data class EditParams(
    val geo: GeoParams = GeoParams(),
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val filter: Int = 0,
    val filterIntensity: Float = 1f
) {
    val isIdentity: Boolean get() = geo.isIdentity && ImageFx.colorArray(this) == null
}

object ImageFx {

    private fun cm(vararg v: Float) = ColorMatrix(v)

    private val IDENTITY = ColorMatrix().array.copyOf()

    private fun baseFilter(id: Int): ColorMatrix? = when (id) {
        1 -> ColorMatrix().apply { setSaturation(0f) }
        2 -> cm(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        3 -> ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(contrastMatrix(1.12f))
        }
        4 -> cm(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
        5 -> cm(
            1.12f, 0f, 0f, 0f, 6f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.86f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        6 -> ColorMatrix().apply {
            setSaturation(0.8f)
            postConcat(contrastMatrix(0.85f))
            postConcat(
                cm(
                    1.05f, 0f, 0f, 0f, 4f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 0.92f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        }
        7 -> cm(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
        else -> null
    }

    private fun contrastMatrix(c: Float): ColorMatrix {
        val t = 128f * (1f - c)
        return cm(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    }

    fun filterMatrix(id: Int, intensity: Float): ColorMatrix? {
        val base = baseFilter(id) ?: return null
        val k = intensity.coerceIn(0f, 1f)
        if (k >= 0.999f) return base
        val a = base.array
        return ColorMatrix(FloatArray(20) { i -> IDENTITY[i] + (a[i] - IDENTITY[i]) * k })
    }

    /** Matriks warna gabungan (nilai 4x5 gaya Android), atau null bila tidak ada perubahan. */
    fun colorArray(
        brightness: Float, contrast: Float, saturation: Float, warmth: Float,
        filter: Int, intensity: Float
    ): FloatArray? {
        val m = ColorMatrix()
        var changed = false
        if (abs(saturation - 1f) > 0.005f) {
            m.postConcat(ColorMatrix().apply { setSaturation(saturation) }); changed = true
        }
        if (abs(contrast - 1f) > 0.005f) {
            m.postConcat(contrastMatrix(contrast)); changed = true
        }
        if (abs(warmth) > 0.005f) {
            val k = 0.18f * warmth
            m.postConcat(
                cm(
                    1f + k, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f - k, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            ); changed = true
        }
        if (abs(brightness) > 0.005f) {
            val o = brightness * 100f
            m.postConcat(
                cm(
                    1f, 0f, 0f, 0f, o,
                    0f, 1f, 0f, 0f, o,
                    0f, 0f, 1f, 0f, o,
                    0f, 0f, 0f, 1f, 0f
                )
            ); changed = true
        }
        filterMatrix(filter, intensity)?.let { m.postConcat(it); changed = true }
        return if (changed) m.array.copyOf() else null
    }

    fun colorArray(e: EditParams): FloatArray? =
        colorArray(e.brightness, e.contrast, e.saturation, e.warmth, e.filter, e.filterIntensity)

    // ---------------- Geometri ----------------

    /** Memutar/membalik/memotong. src tidak pernah di-recycle; hasil bisa berupa src itu sendiri. */
    fun applyGeometry(src: Bitmap, g: GeoParams): Bitmap {
        if (g.isIdentity) return src
        var cur = src
        val turns = ((g.quarterTurns % 4) + 4) % 4
        if (turns != 0 || g.flipH || g.flipV) {
            val m = Matrix()
            if (turns != 0) m.postRotate(90f * turns)
            if (g.flipH || g.flipV) m.postScale(if (g.flipH) -1f else 1f, if (g.flipV) -1f else 1f)
            cur = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        val ratio = AspectOptions.getOrNull(g.aspect)?.ratio
        val zoom = g.zoom.coerceIn(1f, 4f)
        if (ratio != null || zoom > 1.001f) {
            val w = cur.width
            val h = cur.height
            val baseW: Float
            val baseH: Float
            if (ratio == null) {
                baseW = w.toFloat(); baseH = h.toFloat()
            } else if (w.toFloat() / h > ratio) {
                baseH = h.toFloat(); baseW = baseH * ratio
            } else {
                baseW = w.toFloat(); baseH = baseW / ratio
            }
            val cw = (baseW / zoom).roundToInt().coerceIn(1, w)
            val ch = (baseH / zoom).roundToInt().coerceIn(1, h)
            val left = (((w - cw) / 2f) * (1f + g.offX.coerceIn(-1f, 1f))).roundToInt().coerceIn(0, w - cw)
            val top = (((h - ch) / 2f) * (1f + g.offY.coerceIn(-1f, 1f))).roundToInt().coerceIn(0, h - ch)
            val cropped = Bitmap.createBitmap(cur, left, top, cw, ch)
            if (cropped !== cur && cur !== src) cur.recycle()
            cur = cropped
        }
        return cur
    }

    // ---------------- Decode / render ----------------

    private fun exifRotation(path: String): Float = try {
        when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }

    /** Ukuran asli foto (lebar, tinggi) setelah orientasi EXIF, tanpa memuat piksel. */
    fun bounds(path: String): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        val r = exifRotation(path)
        return if (r == 90f || r == 270f) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    /** Decode ter-downsample (sisi terpanjang minimal maxLongSide, kurang dari 2x), orientasi EXIF sudah diterapkan. */
    fun decode(path: String, maxLongSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: return null
            val rotation = exifRotation(path)
            if (rotation == 0f) decoded else {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true
                ).also { if (it !== decoded) decoded.recycle() }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Menerapkan semua suntingan ke foto dan menulis JPEG baru. File asli tidak diubah (agar undo bekerja). */
    fun render(src: File, dest: File, edit: EditParams, maxLongSide: Int = 2560): Boolean {
        return try {
            val bmp = decode(src.path, maxLongSide) ?: return false
            val geo = applyGeometry(bmp, edit.geo)
            if (geo !== bmp) bmp.recycle()
            val array = colorArray(edit)
            val out = if (array == null) geo else {
                val o = Bitmap.createBitmap(geo.width, geo.height, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(ColorMatrix(array))
                }
                Canvas(o).drawBitmap(geo, 0f, 0f, paint)
                geo.recycle()
                o
            }
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            out.recycle()
            true
        } catch (e: Exception) {
            dest.delete()
            false
        } catch (e: OutOfMemoryError) {
            dest.delete()
            false
        }
    }
}
