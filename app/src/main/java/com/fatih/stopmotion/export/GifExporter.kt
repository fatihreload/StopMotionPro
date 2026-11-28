package com.fatih.stopmotion.export

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/** Palet 256 warna (median cut) dari sampel beberapa frame, dengan dithering ordered ringan. */
internal class GifQuantizer {
    private val hist = IntArray(32768)
    val palette = IntArray(256)
    private val lut = ByteArray(32768)

    fun addSamples(argb: IntArray) {
        for (p in argb) {
            hist[(((p shr 19) and 31) shl 10) or (((p shr 11) and 31) shl 5) or ((p shr 3) and 31)]++
        }
    }

    private fun ch(c: Int, which: Int) = when (which) {
        0 -> (c shr 10) and 31
        1 -> (c shr 5) and 31
        else -> c and 31
    }

    private class Box(var from: Int, var to: Int, var pop: Long)

    fun build() {
        val ids = ArrayList<Int>()
        for (i in hist.indices) if (hist[i] > 0) ids.add(i)
        if (ids.isEmpty()) ids.add(0)
        fun population(from: Int, to: Int): Long {
            var s = 0L
            for (i in from until to) s += hist[ids[i]]
            return s
        }
        val boxes = arrayListOf(Box(0, ids.size, population(0, ids.size)))
        while (boxes.size < 256) {
            val box = boxes.filter { it.to - it.from > 1 }.maxByOrNull { it.pop } ?: break
            var bestCh = 0
            var bestRange = -1
            for (c in 0..2) {
                var lo = 31
                var hi = 0
                for (i in box.from until box.to) {
                    val v = ch(ids[i], c)
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                if (hi - lo > bestRange) { bestRange = hi - lo; bestCh = c }
            }
            ids.subList(box.from, box.to).sortBy { ch(it, bestCh) }
            var acc = 0L
            var split = box.from + 1
            for (i in box.from until box.to) {
                acc += hist[ids[i]]
                if (acc * 2 >= box.pop) { split = i + 1; break }
            }
            split = split.coerceIn(box.from + 1, box.to - 1)
            val right = Box(split, box.to, population(split, box.to))
            box.to = split
            box.pop = population(box.from, box.to)
            boxes.add(right)
        }
        var n = 0
        for (b in boxes) {
            var rs = 0L; var gs = 0L; var bs = 0L; var total = 0L
            for (i in b.from until b.to) {
                val c = ids[i]
                val w = hist[c].toLong()
                rs += ch(c, 0) * w; gs += ch(c, 1) * w; bs += ch(c, 2) * w; total += w
            }
            if (total == 0L) total = 1
            val r = (rs / total).toInt(); val g = (gs / total).toInt(); val bl = (bs / total).toInt()
            palette[n++] = (((r shl 3) or (r shr 2)) shl 16) or (((g shl 3) or (g shr 2)) shl 8) or ((bl shl 3) or (bl shr 2))
        }
        for (i in 0 until 32768) {
            val r = ch(i, 0); val g = ch(i, 1); val b = ch(i, 2)
            val r8 = (r shl 3) or (r shr 2); val g8 = (g shl 3) or (g shr 2); val b8 = (b shl 3) or (b shr 2)
            var best = 0
            var bestD = Int.MAX_VALUE
            for (k in 0 until n) {
                val p = palette[k]
                val dr = r8 - ((p shr 16) and 0xFF)
                val dg = g8 - ((p shr 8) and 0xFF)
                val db = b8 - (p and 0xFF)
                val d = dr * dr * 3 + dg * dg * 4 + db * db * 2
                if (d < bestD) { bestD = d; best = k }
            }
            lut[i] = best.toByte()
        }
    }

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun map(argb: IntArray, w: Int, h: Int, out: ByteArray) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = argb[y * w + x]
                val off = ((bayer[(y and 3) * 4 + (x and 3)] - 7.5f) * 0.9f).toInt()
                val r = (((p shr 16) and 0xFF) + off).coerceIn(0, 255) shr 3
                val g = (((p shr 8) and 0xFF) + off).coerceIn(0, 255) shr 3
                val b = ((p and 0xFF) + off).coerceIn(0, 255) shr 3
                out[y * w + x] = lut[(r shl 10) or (g shl 5) or b]
            }
        }
    }
}

/** Penulis GIF89a: palet global, loop tak hingga (opsional), kompresi LZW. */
internal class GifWriter(private val out: OutputStream, private val w: Int, private val h: Int) {
    private fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    fun header(palette: IntArray, loop: Boolean) {
        out.write("GIF89a".toByteArray())
        short(w); short(h)
        out.write(0xF7); out.write(0); out.write(0)
        for (c in palette) { out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF) }
        if (loop) {
            out.write(0x21); out.write(0xFF); out.write(11)
            out.write("NETSCAPE2.0".toByteArray())
            out.write(3); out.write(1); short(0); out.write(0)
        }
    }

    fun frame(indices: ByteArray, delayCs: Int) {
        out.write(0x21); out.write(0xF9); out.write(4); out.write(0x04)
        short(delayCs); out.write(0); out.write(0)
        out.write(0x2C); short(0); short(0); short(w); short(h); out.write(0)
        lzw(indices)
    }

    fun trailer() { out.write(0x3B) }

    private val block = ByteArray(255)
    private var blockLen = 0
    private var bitBuf = 0
    private var bitCnt = 0

    private fun flushBlock() {
        if (blockLen > 0) { out.write(blockLen); out.write(block, 0, blockLen); blockLen = 0 }
    }

    private fun putByte(b: Int) {
        block[blockLen++] = b.toByte()
        if (blockLen == 255) flushBlock()
    }

    private fun putCode(code: Int, size: Int) {
        bitBuf = bitBuf or (code shl bitCnt)
        bitCnt += size
        while (bitCnt >= 8) { putByte(bitBuf and 0xFF); bitBuf = bitBuf ushr 8; bitCnt -= 8 }
    }

    private val table = IntArray(1 shl 20)
    private val used = IntArray(4100)

    private fun lzw(px: ByteArray) {
        out.write(8)
        blockLen = 0; bitBuf = 0; bitCnt = 0
        val clear = 256
        val eoi = 257
        var codeSize = 9
        var next = 258
        var usedN = 0
        putCode(clear, codeSize)
        var prefix = px[0].toInt() and 0xFF
        for (i in 1 until px.size) {
            val c = px[i].toInt() and 0xFF
            val key = (prefix shl 8) or c
            val code = table[key]
            if (code != 0) {
                prefix = code
            } else {
                putCode(prefix, codeSize)
                if (next < 4096) {
                    table[key] = next
                    used[usedN++] = key
                    next++
                    if (next > (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    putCode(clear, codeSize)
                    for (k in 0 until usedN) table[used[k]] = 0
                    usedN = 0; next = 258; codeSize = 9
                }
                prefix = c
            }
        }
        putCode(prefix, codeSize)
        if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
        putCode(eoi, codeSize)
        if (bitCnt > 0) { putByte(bitBuf and 0xFF); bitBuf = 0; bitCnt = 0 }
        flushBlock()
        out.write(0)
        for (k in 0 until usedN) table[used[k]] = 0
    }
}

object GifExporter {
    private class Run(val startTick: Int, var endTick: Int)

    fun export(
        composer: FrameComposer,
        fps: Int,
        loop: Boolean,
        dest: File,
        cancelled: () -> Boolean,
        progress: (Float, String) -> Unit
    ) {
        val w = composer.width
        val h = composer.height
        val total = composer.totalTicks

        // Tick berurutan yang identik (frame sama, tanpa fade) digabung menjadi satu frame GIF dengan jeda lebih lama.
        val runs = ArrayList<Run>()
        var lastSig: List<Int>? = null
        for (t in 0 until total) {
            val sig = composer.signature(t)
            if (sig == lastSig) runs.last().endTick = t + 1
            else runs.add(Run(t, t + 1))
            lastSig = sig
        }

        val argb = IntArray(w * h)
        val quantizer = GifQuantizer()
        val sampleCount = minOf(12, runs.size)
        for (i in 0 until sampleCount) {
            if (cancelled()) throw CancellationException("Dibatalkan")
            val run = runs[if (sampleCount == 1) 0 else i * (runs.size - 1) / (sampleCount - 1)]
            val bmp: Bitmap = composer.render(run.startTick)
            bmp.getPixels(argb, 0, w, 0, 0, w, h)
            quantizer.addSamples(argb)
            progress(0.1f * (i + 1) / sampleCount, "Menyiapkan palet warna")
        }
        quantizer.build()

        try {
            BufferedOutputStream(dest.outputStream(), 1 shl 16).use { os ->
                val gif = GifWriter(os, w, h)
                gif.header(quantizer.palette, loop)
                val indices = ByteArray(w * h)
                runs.forEachIndexed { i, run ->
                    if (cancelled()) throw CancellationException("Dibatalkan")
                    val bmp = composer.render(run.startTick)
                    bmp.getPixels(argb, 0, w, 0, 0, w, h)
                    quantizer.map(argb, w, h, indices)
                    // Jeda dihitung dari waktu kumulatif agar tidak melenceng akibat pembulatan 1/100 detik.
                    val a = (run.startTick * 100.0 / fps).roundToInt()
                    val b = (run.endTick * 100.0 / fps).roundToInt()
                    gif.frame(indices, (b - a).coerceAtLeast(2))
                    progress(0.1f + 0.9f * (i + 1) / runs.size, "Menyandikan GIF")
                }
                gif.trailer()
            }
        } catch (e: Throwable) {
            dest.delete()
            throw e
        }
    }
}
