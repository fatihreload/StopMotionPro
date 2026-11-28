package com.fatih.stopmotion.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Jenis file frame. Frame "cutout" adalah PNG ber-alpha (latar sudah dihapus, misalnya hasil hapus latar otomatis
 * saat foto diambil, hasil erase, atau hasil merge). Dikenali dari isi file (tanda tangan PNG), bukan dari nama,
 * karena file bisa disalin atau dipulihkan dari cadangan dengan nama lain.
 */
object FrameKind {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun isCutout(path: String): Boolean = try {
        File(path).inputStream().use { input ->
            val head = ByteArray(PNG_SIGNATURE.size)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            read == head.size && head.contentEquals(PNG_SIGNATURE)
        }
    } catch (e: Exception) {
        false
    }

    fun extensionFor(path: String): String = if (isCutout(path)) "png" else "jpg"
}

/** Satu langkah erase manual. Koordinat dinormalisasi (0..1) terhadap lebar dan tinggi gambar. */
sealed class EraseOp {
    /** Kuas penghapus. radius = pecahan dari sisi terpendek gambar. */
    data class Stroke(val xs: FloatArray, val ys: FloatArray, val radius: Float) : EraseOp()

    /** Ketuk warna: menghapus area bersambung yang warnanya mirip titik yang diketuk. tolerance 0..1. */
    data class Wand(val x: Float, val y: Float, val tolerance: Float) : EraseOp()
}

object Cutout {

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val clearFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
    }

    /** Pastikan bitmap bisa diubah dan punya alpha. Mengembalikan salinan bila perlu (src tidak didaur ulang). */
    fun mutableArgb(src: Bitmap): Bitmap {
        val out = if (src.isMutable && src.config == Bitmap.Config.ARGB_8888) src
        else src.copy(Bitmap.Config.ARGB_8888, true) ?: throw IOException("Memori tidak cukup")
        // Tanpa ini, area yang dihapus dianggap buram (hitam) saat digambar dan saat disimpan sebagai PNG.
        out.setHasAlpha(true)
        return out
    }

    // ---------------- Erase ----------------

    /** Menggambar satu garis kuas (dari titik a ke b, atau satu titik) langsung ke bitmap. Dipakai saat menggambar live. */
    fun eraseSegment(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, radius: Float) {
        bmp.setHasAlpha(true)
        val canvas = Canvas(bmp)
        val r = radius * min(bmp.width, bmp.height)
        clearPaint.strokeWidth = r * 2f
        canvas.drawCircle(x0 * bmp.width, y0 * bmp.height, r, clearFill)
        if (x0 != x1 || y0 != y1) {
            canvas.drawLine(x0 * bmp.width, y0 * bmp.height, x1 * bmp.width, y1 * bmp.height, clearPaint)
        }
        canvas.drawCircle(x1 * bmp.width, y1 * bmp.height, r, clearFill)
    }

    /** Mengembalikan work ke isi original lalu menjalankan ulang ops (dipakai untuk Batal dan Ulang). */
    fun rebuild(work: Bitmap, original: Bitmap, ops: List<EraseOp>) {
        val canvas = Canvas(work)
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        canvas.drawBitmap(original, 0f, 0f, null)
        applyErase(work, ops)
    }

    fun applyErase(bmp: Bitmap, ops: List<EraseOp>) {
        bmp.setHasAlpha(true)
        for (op in ops) {
            when (op) {
                is EraseOp.Stroke -> applyStroke(bmp, op)
                is EraseOp.Wand -> applyWand(bmp, op)
            }
        }
    }

    private fun applyStroke(bmp: Bitmap, s: EraseOp.Stroke) {
        val n = min(s.xs.size, s.ys.size)
        if (n == 0) return
        val canvas = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val r = s.radius * min(bmp.width, bmp.height)
        clearPaint.strokeWidth = r * 2f
        if (n == 1) {
            canvas.drawCircle(s.xs[0] * w, s.ys[0] * h, r, clearFill)
            return
        }
        val path = Path()
        path.moveTo(s.xs[0] * w, s.ys[0] * h)
        for (i in 1 until n) path.lineTo(s.xs[i] * w, s.ys[i] * h)
        canvas.drawCircle(s.xs[0] * w, s.ys[0] * h, r, clearFill)
        canvas.drawPath(path, clearPaint)
        canvas.drawCircle(s.xs[n - 1] * w, s.ys[n - 1] * h, r, clearFill)
    }

    /** Flood fill 4 arah dari titik ketuk; piksel yang selisih warnanya (per kanal terbesar) di bawah ambang dijadikan transparan. */
    private fun applyWand(bmp: Bitmap, op: EraseOp.Wand) {
        val w = bmp.width
        val h = bmp.height
        val sx = (op.x * w).toInt().coerceIn(0, w - 1)
        val sy = (op.y * h).toInt().coerceIn(0, h - 1)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val seed = px[sy * w + sx]
        if ((seed ushr 24) < 16) return // sudah transparan
        val sr = (seed shr 16) and 0xFF
        val sg = (seed shr 8) and 0xFF
        val sb = seed and 0xFF
        val limit = (op.tolerance.coerceIn(0f, 1f) * 150f).roundToInt()

        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        stack[top++] = sy * w + sx
        visited[sy * w + sx] = true
        while (top > 0) {
            val idx = stack[--top]
            val p = px[idx]
            val dr = abs(((p shr 16) and 0xFF) - sr)
            val dg = abs(((p shr 8) and 0xFF) - sg)
            val db = abs((p and 0xFF) - sb)
            if (max(dr, max(dg, db)) > limit) continue
            px[idx] = 0
            val x = idx % w
            val y = idx / w
            if (x > 0 && !visited[idx - 1]) { visited[idx - 1] = true; stack[top++] = idx - 1 }
            if (x < w - 1 && !visited[idx + 1]) { visited[idx + 1] = true; stack[top++] = idx + 1 }
            if (y > 0 && !visited[idx - w]) { visited[idx - w] = true; stack[top++] = idx - w }
            if (y < h - 1 && !visited[idx + w]) { visited[idx + w] = true; stack[top++] = idx + w }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    // ---------------- Hapus latar otomatis ----------------

    /**
     * Mengubah foto hasil kamera (JPEG) menjadi PNG tanpa latar memakai pengaturan chroma key.
     * Mengembalikan false bila gagal (file tujuan dibersihkan); pemanggil tetap bisa memakai foto asli.
     */
    fun keyToPng(src: File, dest: File, state: ChromaState, maxLongSide: Int = 1920): Boolean {
        return try {
            val bmp = ImageFx.decode(src.path, maxLongSide) ?: return false
            val keyed = ChromaKey.render(bmp, state)
            bmp.recycle()
            dest.parentFile?.mkdirs()
            dest.outputStream().use { keyed.compress(Bitmap.CompressFormat.PNG, 100, it) }
            keyed.recycle()
            true
        } catch (e: Exception) {
            dest.delete()
            false
        } catch (e: OutOfMemoryError) {
            dest.delete()
            false
        }
    }

    // ---------------- Merge ----------------

    /** Apakah hasil merge akan berupa PNG ber-alpha (dipakai pemanggil untuk memilih ekstensi file tujuan). */
    fun mergeIsTransparent(bottom: File, chroma: ChromaState): Boolean =
        chroma.active || FrameKind.isCutout(bottom.path)

    /**
     * Menggabungkan beberapa foto menjadi satu gambar. Urutan daftar = urutan tumpukan (yang pertama paling bawah).
     * Foto biasa ikut dihapus latarnya lebih dulu bila chroma key aktif, sedangkan frame cutout dipakai apa adanya.
     * Hasil disimpan sebagai PNG ber-alpha bila lapisan paling bawah punya area transparan (cutout atau hasil keying),
     * selain itu JPEG. Mengembalikan true bila hasilnya PNG.
     */
    fun merge(sources: List<File>, dest: File, chroma: ChromaState, maxLongSide: Int = 1920): Boolean {
        if (sources.size < 2) throw IOException("Pilih minimal 2 frame")
        var canvasBmp: Bitmap? = null
        try {
            var transparent = false
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            sources.forEachIndexed { i, file ->
                val decoded = ImageFx.decode(file.path, maxLongSide)
                    ?: throw IOException("Foto frame ${i + 1} tidak dapat dibaca")
                val cutout = FrameKind.isCutout(file.path)
                val layer = if (!cutout && chroma.active) {
                    ChromaKey.render(decoded, chroma).also { decoded.recycle() }
                } else decoded
                if (i == 0) {
                    transparent = cutout || chroma.active
                    canvasBmp = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
                }
                val target = canvasBmp ?: throw IOException("Gagal membuat kanvas")
                Canvas(target).drawBitmap(layer, null, RectF(0f, 0f, target.width.toFloat(), target.height.toFloat()), paint)
                layer.recycle()
            }
            val out = canvasBmp ?: throw IOException("Gagal membuat kanvas")
            dest.parentFile?.mkdirs()
            dest.outputStream().use {
                if (transparent) out.compress(Bitmap.CompressFormat.PNG, 100, it)
                else out.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            return transparent
        } catch (e: Exception) {
            dest.delete()
            throw if (e is IOException) e else IOException(e.message ?: "Gagal menggabungkan")
        } catch (e: OutOfMemoryError) {
            dest.delete()
            throw IOException("Memori tidak cukup untuk menggabungkan frame")
        } finally {
            canvasBmp?.recycle()
        }
    }
}
