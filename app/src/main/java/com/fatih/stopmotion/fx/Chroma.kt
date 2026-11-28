package com.fatih.stopmotion.fx

import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ChromaMode {
    const val OFF = 0
    const val GREEN = 1
    const val BLUE = 2
}

/** Pilihan latar pengganti. 0..3 = warna polos, 4 = gambar dari galeri. */
object ChromaBg {
    const val IMAGE = 4
    val names = listOf("Hitam", "Putih", "Abu-abu", "Langit", "Gambar")
    private val colors = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt(), 0xFF4FA3E0.toInt())

    /** Warna latar; untuk mode gambar dipakai hitam sebagai cadangan bila file tidak ada. */
    fun color(mode: Int): Int = colors.getOrElse(mode) { colors[0] }
}

/** Pengaturan penghapus latar (green screen / blue screen) tingkat proyek. Non-destruktif. */
data class ChromaState(
    val mode: Int = ChromaMode.OFF,
    /** 0..1: makin tinggi, makin banyak warna mirip layar yang dihapus. */
    val tolerance: Float = 0.4f,
    /** 0..1: kehalusan tepi antara objek dan latar. */
    val softness: Float = 0.3f,
    val bgMode: Int = 0,
    val bgPath: String? = null
) {
    val active: Boolean get() = mode == ChromaMode.GREEN || mode == ChromaMode.BLUE

    /** Kunci cache untuk hasil keying (latar tidak ikut karena digambar terpisah). */
    fun cacheKey(): String = "$mode|${(tolerance * 100).roundToInt()}|${(softness * 100).roundToInt()}"
}

/**
 * Chroma key berbasis dominasi warna: untuk layar hijau dipakai (G - max(R,B)), untuk biru (B - max(R,G)),
 * dinormalisasi terhadap kecerahan sehingga bagian layar yang terang maupun yang bayangan sama-sama terhapus.
 * Tepi dihaluskan, dan pantulan warna layar (spill) pada objek dikurangi.
 */
object ChromaKey {

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Memproses larik ARGB (tidak-premultiplied) di tempat. Alpha hasil menyatakan bagian yang dipertahankan. */
    fun keyPixels(px: IntArray, mode: Int, tolerance: Float, softness: Float) {
        if (mode != ChromaMode.GREEN && mode != ChromaMode.BLUE) return
        val green = mode == ChromaMode.GREEN
        // Ambang dominasi (0..255): makin besar toleransi, makin kecil ambang, makin banyak yang terhapus.
        val threshold = 140f - 120f * tolerance.coerceIn(0f, 1f)
        val width = 10f + 100f * softness.coerceIn(0f, 1f)
        for (i in px.indices) {
            val p = px[i]
            val a0 = (p ushr 24) and 0xFF
            if (a0 == 0) continue
            var r = (p shr 16) and 0xFF
            var g = (p shr 8) and 0xFF
            var b = p and 0xFF
            val luma = (299 * r + 587 * g + 114 * b) / 1000
            if (luma < 24) continue // area sangat gelap: warna tidak bisa diandalkan, pertahankan

            val key = if (green) g else b
            val other = if (green) max(r, b) else max(r, g)
            val dominance = (key - other) * 255f / max(key, 40)
            val keep = 1f - smoothstep(threshold, threshold + width, dominance)

            if (keep > 0f && dominance > 0f) {
                // Kurangi pantulan: tepi dikoreksi penuh, bagian dalam sebagian kecil.
                val k = if (keep < 1f) 1f else 0.5f * (dominance / max(threshold, 1f)).coerceIn(0f, 1f)
                val reduced = (key - (key - other) * k).roundToInt().coerceIn(0, 255)
                if (green) g = reduced else b = reduced
            }
            val alpha = (keep * a0).roundToInt().coerceIn(0, 255)
            px[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun dominance(r: Int, g: Int, b: Int, green: Boolean): Float {
        val key = if (green) g else b
        val other = if (green) max(r, b) else max(r, g)
        return (key - other) * 255f / max(key, 40)
    }

    /** Box blur terpisah (horizontal lalu vertikal) dengan tepi direplikasi. */
    private fun boxBlur(src: ShortArray, w: Int, h: Int, r: Int): ShortArray {
        val tmp = ShortArray(src.size)
        val out = ShortArray(src.size)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = (sum / div).toShort()
                sum += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = (sum / div).toShort()
                sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    /** Erosi (nilai minimum di jendela) terpisah; piksel di luar gambar diabaikan. */
    private fun erode(src: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val tmp = ByteArray(src.size)
        val out = ByteArray(src.size)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var m = 255
                for (k in max(0, x - r)..min(w - 1, x + r)) {
                    val v = src[row + k].toInt() and 0xFF
                    if (v < m) m = v
                }
                tmp[row + x] = m.toByte()
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var m = 255
                for (k in max(0, y - r)..min(h - 1, y + r)) {
                    val v = tmp[k * w + x].toInt() and 0xFF
                    if (v < m) m = v
                }
                out[y * w + x] = m.toByte()
            }
        }
        return out
    }

    /**
     * Pipeline kualitas tinggi untuk gambar utuh (dipakai kamera langsung, pratinjau, dan ekspor).
     *  1. Peta dominasi warna per piksel, lalu dihaluskan agar bintik noise JPEG pada layar ikut terhapus.
     *  2. Alpha = yang lebih ketat dari peta asli dan peta halus, sehingga tidak ada pinggiran hijau/biru 1 piksel.
     *  3. Tepi dilembutkan ke arah dalam objek (rata-rata alpha dan erosinya), tidak melebar ke arah layar.
     *  4. Piksel tepi campuran dibersihkan dari warna layar (unmix), sisanya dikurangi pantulannya.
     */
    fun keyImage(px: IntArray, w: Int, h: Int, mode: Int, tolerance: Float, softness: Float) {
        if (mode != ChromaMode.GREEN && mode != ChromaMode.BLUE) return
        val n = w * h
        if (n <= 0 || px.size < n) return
        val green = mode == ChromaMode.GREEN
        val threshold = 140f - 120f * tolerance.coerceIn(0f, 1f)
        val width = 10f + 100f * softness.coerceIn(0f, 1f)
        val longSide = max(w, h)

        val own = ShortArray(n)
        for (i in 0 until n) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (299 * r + 587 * g + 114 * b) / 1000
            own[i] = if (luma < 24 || (p ushr 24) == 0) 0.toShort()
            else dominance(r, g, b, green).coerceIn(-255f, 255f).toInt().toShort()
        }
        val smooth = boxBlur(own, w, h, (longSide / 700).coerceIn(1, 6))

        val keepOwn = ByteArray(n)
        val keep = ByteArray(n)
        var rs = 0L
        var gs = 0L
        var bs = 0L
        var cnt = 0
        for (i in 0 until n) {
            val ko = 1f - smoothstep(threshold, threshold + width, own[i].toFloat())
            val ks = 1f - smoothstep(threshold, threshold + width, smooth[i].toFloat())
            val m8 = (min(ko, ks) * 255f + 0.5f).toInt()
            keepOwn[i] = (ko * 255f + 0.5f).toInt().toByte()
            keep[i] = m8.toByte()
            if (m8 == 0 && (i and 3) == 0) {
                val p = px[i]
                rs += (p shr 16) and 0xFF
                gs += (p shr 8) and 0xFF
                bs += p and 0xFF
                cnt++
            }
        }
        // Warna layar rata-rata, dipakai untuk melepas campuran warna pada tepi.
        val kr: Float
        val kg: Float
        val kb: Float
        if (cnt >= 64) {
            kr = rs / cnt.toFloat(); kg = gs / cnt.toFloat(); kb = bs / cnt.toFloat()
        } else if (green) {
            kr = 40f; kg = 190f; kb = 70f
        } else {
            kr = 40f; kg = 80f; kb = 200f
        }

        val eroded = erode(keep, w, h, (longSide / 1000).coerceIn(1, 3))
        for (i in 0 until n) {
            val p = px[i]
            val a0 = p ushr 24
            val kA = keep[i].toInt() and 0xFF
            if (a0 == 0 || kA == 0) { px[i] = 0; continue }
            val a = (((kA + (eroded[i].toInt() and 0xFF)) / 2) * a0 + 127) / 255
            if (a == 0) { px[i] = 0; continue }

            var r = ((p shr 16) and 0xFF).toFloat()
            var g = ((p shr 8) and 0xFF).toFloat()
            var b = (p and 0xFF).toFloat()
            val o = keepOwn[i].toInt() and 0xFF
            if (o in 1..254) {
                // Piksel tepi = campuran objek + layar: lepaskan kontribusi layar.
                val cov = max(o / 255f, 0.2f)
                val inv = 1f - o / 255f
                r = ((r - inv * kr) / cov).coerceIn(0f, 255f)
                g = ((g - inv * kg) / cov).coerceIn(0f, 255f)
                b = ((b - inv * kb) / cov).coerceIn(0f, 255f)
                if (green) {
                    val other = max(r, b)
                    if (g > other) g = other + (g - other) * 0.3f
                } else {
                    val other = max(r, g)
                    if (b > other) b = other + (b - other) * 0.3f
                }
            } else {
                val d = own[i].toFloat()
                if (d > 0f) {
                    val k = 0.5f * (d / max(threshold, 1f)).coerceIn(0f, 1f)
                    if (green) {
                        val other = max(r, b)
                        g -= (g - other) * k
                    } else {
                        val other = max(r, g)
                        b -= (b - other) * k
                    }
                }
            }
            px[i] = (a shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
    }

    /** Mengecilkan src bila lebih besar dari maxLongSide (0 = tanpa pengecilan). */
    private fun scaled(src: Bitmap, maxLongSide: Int): Bitmap {
        val longSide = max(src.width, src.height)
        if (maxLongSide !in 1 until longSide) return src
        val s = maxLongSide / longSide.toFloat()
        return Bitmap.createScaledBitmap(
            src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true
        )
    }

    /** Hasil berupa bitmap ARGB (dapat diubah) dengan area latar transparan. maxLongSide > 0 mengecilkan dulu. */
    fun render(src: Bitmap, mode: Int, tolerance: Float, softness: Float, maxLongSide: Int = 0): Bitmap {
        val work = scaled(src, maxLongSide)
        val w = work.width
        val h = work.height
        val px = IntArray(w * h)
        work.getPixels(px, 0, w, 0, 0, w, h)
        keyImage(px, w, h, mode, tolerance, softness)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        if (work !== src) work.recycle()
        return out
    }

    fun render(src: Bitmap, state: ChromaState, maxLongSide: Int = 0): Bitmap =
        render(src, state.mode, state.tolerance, state.softness, maxLongSide)

    /**
     * Penghapus latar (bila aktif) lalu coretan erase manual. Tanpa chroma key pun coretan erase tetap berlaku.
     * Hasil selalu bitmap ARGB baru yang boleh diubah; src tidak diubah.
     */
    fun process(src: Bitmap, state: ChromaState, strokes: List<EraseStroke>, maxLongSide: Int = 0): Bitmap {
        val out = if (state.active) {
            render(src, state, maxLongSide)
        } else {
            val work = scaled(src, maxLongSide)
            Bitmap.createBitmap(work.width, work.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(work, 0f, 0f, null)
                if (work !== src) work.recycle()
            }
        }
        EraseMask.apply(out, strokes)
        return out
    }
}
