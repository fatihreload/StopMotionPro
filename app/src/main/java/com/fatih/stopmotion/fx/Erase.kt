package com.fatih.stopmotion.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.roundToInt

/**
 * Satu coretan erase. Koordinat dinormalisasi (0..1 terhadap lebar/tinggi foto) dan radius sebagai pecahan lebar foto,
 * sehingga coretan berlaku sama di pratinjau kecil maupun ekspor 4K.
 * restore = true berarti menghapus coretan sebelumnya (memulihkan), bukan menghapus.
 */
class EraseStroke(val restore: Boolean, val radius: Float, val points: FloatArray) {
    val pointCount: Int get() = points.size / 2
}

/** Format teks ringkas untuk disimpan di database dan file cadangan. */
object EraseCodec {
    const val MAX_STROKES = 300

    /** "e30:1234,5678,1300,5700;r50:..." : e/r, radius per mil lebar, lalu pasangan x,y per 10000. Kosong = null. */
    fun encode(strokes: List<EraseStroke>): String? {
        if (strokes.isEmpty()) return null
        val sb = StringBuilder()
        for (s in strokes.takeLast(MAX_STROKES)) {
            if (s.pointCount == 0) continue
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(if (s.restore) 'r' else 'e')
            sb.append((s.radius * 1000f).roundToInt().coerceIn(1, 500))
            sb.append(':')
            for (i in s.points.indices) {
                if (i > 0) sb.append(',')
                sb.append((s.points[i] * 10000f).roundToInt().coerceIn(0, 10000))
            }
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Toleran terhadap data rusak: coretan yang tidak valid dilewati. */
    fun decode(data: String?): List<EraseStroke> {
        if (data.isNullOrBlank()) return emptyList()
        val out = ArrayList<EraseStroke>()
        for (part in data.split(';')) {
            if (part.length < 4) continue
            val kind = part[0]
            if (kind != 'e' && kind != 'r') continue
            val colon = part.indexOf(':')
            if (colon < 2) continue
            val radius = part.substring(1, colon).toIntOrNull() ?: continue
            val nums = part.substring(colon + 1).split(',').map { it.trim().toIntOrNull() }
            if (nums.isEmpty() || nums.any { it == null } || nums.size % 2 != 0) continue
            out.add(
                EraseStroke(
                    restore = kind == 'r',
                    radius = (radius / 1000f).coerceIn(0.001f, 0.5f),
                    points = FloatArray(nums.size) { (nums[it]!!.coerceIn(0, 10000)) / 10000f }
                )
            )
            if (out.size >= MAX_STROKES) break
        }
        return out
    }

    fun isEmpty(data: String?): Boolean = decode(data).isEmpty()
}

/** Menerapkan coretan erase sebagai mask alpha (ALPHA_8): objek tetap, area terhapus menjadi transparan. */
object EraseMask {

    private fun paintFor(restore: Boolean, widthPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = widthPx
        if (!restore) xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /** Sisi terpanjang mask. Foto beresolusi lebih besar memakai mask ini lalu diskalakan (tepi coretan memang lembut). */
    private const val MASK_MAX = 1600

    /** Mask baru berisi 255 (semua dipertahankan) lalu coretan digambar berurutan. */
    fun createMask(w: Int, h: Int, strokes: List<EraseStroke>): Bitmap {
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.eraseColor(Color.BLACK)
        val canvas = Canvas(mask)
        for (s in strokes) drawStroke(canvas, w, h, s)
        return mask
    }

    fun drawStroke(c: Canvas, w: Int, h: Int, s: EraseStroke) {
        val n = s.pointCount
        if (n == 0) return
        val paint = paintFor(s.restore, 2f * s.radius * w)
        if (n == 1) {
            paint.style = Paint.Style.FILL
            c.drawCircle(s.points[0] * w, s.points[1] * h, s.radius * w, paint)
        } else {
            paint.style = Paint.Style.STROKE
            val path = Path()
            path.moveTo(s.points[0] * w, s.points[1] * h)
            for (i in 1 until n) path.lineTo(s.points[i * 2] * w, s.points[i * 2 + 1] * h)
            c.drawPath(path, paint)
        }
    }

    /** Satu ruas garis (untuk menggambar coretan secara bertahap saat jari bergerak). */
    fun drawSegment(c: Canvas, w: Int, h: Int, restore: Boolean, radius: Float, x0: Float, y0: Float, x1: Float, y1: Float) {
        val paint = paintFor(restore, 2f * radius * w).apply { style = Paint.Style.STROKE }
        c.drawLine(x0 * w, y0 * h, x1 * w, y1 * h, paint)
    }

    fun drawDot(c: Canvas, w: Int, h: Int, restore: Boolean, radius: Float, x: Float, y: Float) {
        val paint = paintFor(restore, 2f * radius * w).apply { style = Paint.Style.FILL }
        c.drawCircle(x * w, y * h, radius * w, paint)
    }

    /** Mengalikan alpha target (bitmap ARGB yang dapat diubah) dengan mask coretan. */
    fun apply(target: Bitmap, strokes: List<EraseStroke>) {
        if (strokes.isEmpty()) return
        val longSide = maxOf(target.width, target.height)
        val scale = if (longSide > MASK_MAX) MASK_MAX / longSide.toFloat() else 1f
        val mw = (target.width * scale).roundToInt().coerceAtLeast(1)
        val mh = (target.height * scale).roundToInt().coerceAtLeast(1)
        val mask = createMask(mw, mh, strokes)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        Canvas(target).drawBitmap(mask, null, Rect(0, 0, target.width, target.height), paint)
        mask.recycle()
    }
}
