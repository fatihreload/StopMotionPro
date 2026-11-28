package com.fatih.stopmotion.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.fx.LayerFiles
import com.fatih.stopmotion.fx.KeyframeMath
import com.fatih.stopmotion.fx.LayerGeometry
import com.fatih.stopmotion.fx.LayerTiming
import com.fatih.stopmotion.fx.ChromaBg
import com.fatih.stopmotion.fx.ChromaKey
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.EraseCodec
import com.fatih.stopmotion.fx.FxState
import com.fatih.stopmotion.fx.ImageFx
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    MP4("MP4 (video)", "mp4", "video/mp4"),
    GIF("GIF", "gif", "image/gif"),
    ZIP("Foto (ZIP)", "zip", "application/zip")
}

data class ExportSettings(
    val format: ExportFormat = ExportFormat.MP4,
    /** Sisi pendek hasil dalam piksel. */
    val shortSide: Int = 720,
    /** 0 = hemat, 1 = seimbang, 2 = tinggi. Hanya untuk MP4. */
    val quality: Int = 1,
    val includeAudio: Boolean = true,
    /** Hanya untuk GIF. */
    val loop: Boolean = true
)

fun sizeOptions(format: ExportFormat): List<Int> = when (format) {
    ExportFormat.MP4 -> listOf(480, 720, 1080, 1440, 2160)
    ExportFormat.GIF -> listOf(240, 360, 480)
    ExportFormat.ZIP -> emptyList()
}

/** Nama kualitas menurut sisi pendek: SD, HD, HD+ (Full HD), 2K, 4K. */
fun qualityLabel(shortSide: Int): String = when (shortSide) {
    480 -> "SD 480p"
    720 -> "HD 720p"
    1080 -> "HD+ 1080p"
    1440 -> "2K 1440p"
    2160 -> "4K 2160p"
    else -> "${shortSide}p"
}

/** True bila hasil lebih besar dari foto asli (foto diperbesar, jadi tidak lebih tajam). */
fun isUpscaled(srcWidth: Int, srcHeight: Int, outWidth: Int, outHeight: Int): Boolean =
    maxOf(outWidth, outHeight) > maxOf(srcWidth, srcHeight) * 1.05

fun defaultShortSide(format: ExportFormat): Int = if (format == ExportFormat.GIF) 360 else 720

/** Ukuran hasil mengikuti rasio foto pertama. align = kelipatan (16 untuk H.264 agar aman di semua encoder). */
fun computeOutputSize(srcWidth: Int, srcHeight: Int, shortSide: Int, align: Int): Pair<Int, Int> {
    val w = srcWidth.coerceAtLeast(1)
    val h = srcHeight.coerceAtLeast(1)
    val rw: Double
    val rh: Double
    if (w <= h) {
        rw = shortSide.toDouble(); rh = shortSide.toDouble() * h / w
    } else {
        rh = shortSide.toDouble(); rw = shortSide.toDouble() * w / h
    }
    fun snap(v: Double) = ((v / align).roundToInt() * align).coerceAtLeast(align)
    return snap(rw) to snap(rh)
}

fun videoBitrate(w: Int, h: Int, fps: Int, quality: Int): Int {
    val bpp = when (quality) {
        0 -> 0.08
        1 -> 0.14
        else -> 0.24
    }
    return (w.toDouble() * h * maxOf(fps, 15) * bpp).toInt().coerceIn(1_000_000, 40_000_000)
}

/**
 * Menggambar satu tick film (foto + filter + vignette + fade) ke satu Bitmap yang dipakai ulang.
 * Rumusnya sama dengan pratinjau di Studio Animasi, jadi hasil ekspor sama dengan yang terlihat.
 */
class FrameComposer(
    private val frames: List<FrameEntity>,
    private val fx: FxState,
    val width: Int,
    val height: Int,
    private val chroma: ChromaState = ChromaState(),
    /** Layer tambahan (gambar/klip) yang digambar di atas foto, berurutan dari bawah ke atas. */
    layers: List<LayerEntity> = emptyList(),
    /** FPS proyek, untuk mengubah nomor tick menjadi waktu film (ms) bagi layer. */
    private val fps: Int = 12
) {
    private val tickToFrame: IntArray = run {
        val out = ArrayList<Int>()
        frames.forEachIndexed { i, f -> repeat(f.holdCount.coerceAtLeast(1)) { out.add(i) } }
        out.toIntArray()
    }
    val totalTicks: Int get() = tickToFrame.size

    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        ImageFx.colorArray(0f, 1f, 1f, 0f, fx.filter, fx.intensity)?.let {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(it))
        }
    }
    private val vignettePaint = Paint().apply {
        if (fx.vignette > 0.01f) {
            val radius = hypot(width.toDouble(), height.toDouble()).toFloat() / 2f
            shader = RadialGradient(
                width / 2f, height / 2f, radius,
                intArrayOf(Color.TRANSPARENT, Color.argb((fx.vignette.coerceIn(0f, 1f) * 255).toInt(), 0, 0, 0)),
                floatArrayOf(0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }
    private val fadePaint = Paint()

    private val activeLayers: List<LayerEntity> = layers.filter { it.visible && it.durationMs > 0 }.sortedBy { it.zOrder }
    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private class CachedLayer(val key: String, val bitmap: Bitmap)
    private val layerCache = HashMap<Long, CachedLayer>()
    private val photoBgPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Latar pengganti dipakai bila ada bagian yang dihapus (chroma key aktif atau ada coretan erase);
    // selain itu latar hitam seperti biasa.
    private val hasCutout: Boolean = chroma.active || frames.any { !it.eraseData.isNullOrEmpty() }
    private val bgColor: Int = if (hasCutout) ChromaBg.color(chroma.bgMode) else Color.BLACK
    private val bgBitmap: Bitmap? = chroma.bgPath
        ?.takeIf { hasCutout && chroma.bgMode == ChromaBg.IMAGE }
        ?.let { ImageFx.decode(it, maxOf(width, height)) }

    private var cachedPath: String? = null
    private var cachedBitmap: Bitmap? = null

    fun frameAt(tick: Int): Int = tickToFrame[tick]

    fun fadeAlpha(tick: Int): Float {
        var a = 0f
        val n = totalTicks
        if (fx.fadeIn > 0 && tick < fx.fadeIn) a = maxOf(a, 1f - tick / fx.fadeIn.toFloat())
        val left = n - 1 - tick
        if (fx.fadeOut > 0 && left < fx.fadeOut) a = maxOf(a, 1f - left / fx.fadeOut.toFloat())
        return a.coerceIn(0f, 1f)
    }

    /** Tick tanpa fade: isinya identik dengan tick lain dari frame yang sama. */
    fun isStatic(tick: Int): Boolean = fadeAlpha(tick) <= 0f

    /**
     * Isi sebuah tick dalam bentuk yang bisa dibandingkan: frame foto, tingkat fade, dan frame tiap layer.
     * Dua tick berurutan dengan tanda yang sama menghasilkan gambar identik, jadi tidak perlu digambar/diencode ulang.
     */
    fun signature(tick: Int): List<Int> {
        val t = LayerTiming.tickMs(tick, fps)
        val sig = ArrayList<Int>(2 + activeLayers.size)
        sig.add(tickToFrame[tick])
        sig.add((fadeAlpha(tick) * 255).toInt())
        for (l in activeLayers) {
            sig.add(LayerTiming.frameIndex(l, t) ?: -1)
            if (KeyframeMath.hasKeyframes(l)) sig.add(KeyframeMath.signature(l, t))
        }
        return sig
    }

    /** Bitmap satu frame layer (sudah dihapus latarnya bila layer memakai chroma key). Disimpan satu per layer. */
    private fun layerBitmap(l: LayerEntity, index: Int): Bitmap? {
        val path = LayerFiles.framePath(l, index) ?: return null
        val key = "$path|${l.chromaMode}|${(l.chromaTolerance * 100).roundToInt()}|${(l.chromaSoftness * 100).roundToInt()}"
        layerCache[l.id]?.let { if (it.key == key) return it.bitmap }
        layerCache.remove(l.id)?.bitmap?.recycle()
        val raw = ImageFx.decode(path, maxOf(width, height)) ?: return null
        val result = if (l.chromaMode != 0) {
            ChromaKey.process(raw, ChromaState(l.chromaMode, l.chromaTolerance, l.chromaSoftness), emptyList())
                .also { raw.recycle() }
        } else raw
        layerCache[l.id] = CachedLayer(key, result)
        return result
    }

    private fun drawLayers(tick: Int) {
        if (activeLayers.isEmpty()) return
        val tMs = LayerTiming.tickMs(tick, fps)
        for (l in activeLayers) {
            val index = LayerTiming.frameIndex(l, tMs) ?: continue
            val bmp = layerBitmap(l, index) ?: continue
            val tf = KeyframeMath.sample(l, tMs)
            val r = LayerGeometry.rect(l, tf, width.toFloat(), height.toFloat())
            layerPaint.alpha = (tf.opacity.coerceIn(0f, 1f) * 255).toInt()
            canvas.save()
            canvas.translate(r.cx, r.cy)
            canvas.rotate(r.rotation)
            canvas.drawBitmap(bmp, null, RectF(-r.w / 2f, -r.h / 2f, r.w / 2f, r.h / 2f), layerPaint)
            canvas.restore()
        }
    }

    private fun load(frame: FrameEntity): Bitmap {
        val cacheId = frame.filePath + "|" + (frame.eraseData?.hashCode() ?: 0)
        if (cacheId == cachedPath) cachedBitmap?.let { return it }
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedPath = null
        val bmp = ImageFx.decode(frame.filePath, maxOf(width, height))
            ?: throw IOException("Foto frame tidak dapat dibaca")
        val strokes = EraseCodec.decode(frame.eraseData)
        var result = if (chroma.active || strokes.isNotEmpty()) {
            ChromaKey.process(bmp, chroma, strokes).also { bmp.recycle() }
        } else bmp
        // Perkecil halus ke ukuran akhir (bilinear satu langkah dari <2x terlihat tajam tanpa bergerigi).
        if (result.width > width * 1.25f && result.height > height * 1.25f) {
            val scale = min(width / result.width.toFloat(), height / result.height.toFloat())
            val smaller = Bitmap.createScaledBitmap(
                result, (result.width * scale).toInt().coerceAtLeast(1), (result.height * scale).toInt().coerceAtLeast(1), true
            )
            if (smaller !== result) { result.recycle(); result = smaller }
        }
        cachedPath = cacheId
        cachedBitmap = result
        return result
    }

    /** Hasil dipakai ulang di pemanggilan berikutnya: salin/konversi sebelum memanggil render lagi. */
    fun render(tick: Int): Bitmap {
        canvas.drawColor(bgColor)
        bgBitmap?.let { bg ->
            val s = maxOf(width / bg.width.toFloat(), height / bg.height.toFloat())
            val bw = bg.width * s
            val bh = bg.height * s
            val l = (width - bw) / 2f
            val t = (height - bh) / 2f
            canvas.drawBitmap(bg, null, RectF(l, t, l + bw, t + bh), photoBgPaint)
        }
        val src = load(frames[tickToFrame[tick]])
        val scale = min(width / src.width.toFloat(), height / src.height.toFloat())
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (width - dw) / 2f
        val top = (height - dh) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + dw, top + dh), photoPaint)
        drawLayers(tick)
        if (fx.vignette > 0.01f) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        val a = fadeAlpha(tick)
        if (a > 0f) {
            fadePaint.color = if (fx.fadeWhite) Color.WHITE else Color.BLACK
            fadePaint.alpha = (a * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        }
        return bitmap
    }

    fun release() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedPath = null
        bgBitmap?.recycle()
        layerCache.values.forEach { it.bitmap.recycle() }
        layerCache.clear()
        bitmap.recycle()
    }
}
