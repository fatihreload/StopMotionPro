package com.fatih.stopmotion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uji pipeline chroma key kualitas tinggi (ChromaKey.keyImage) pada gambar sintetis kecil. */
class ChromaKeyImageTest {

    private val green = 0xFF00B140.toInt()   // (0,177,64)
    private val blue = 0xFF0047BB.toInt()    // (0,71,187)
    private val skin = 0xFFE0AC69.toInt()    // (224,172,105)
    private val size = 40

    /** Kiri (x < 20) = layar, kanan = objek. */
    private fun split(screen: Int, subject: Int) =
        IntArray(size * size) { i -> if (i % size < 20) screen else subject }

    private fun alpha(p: Int) = (p ushr 24) and 0xFF

    private fun key(px: IntArray, mode: Int, tol: Float = 0.5f, soft: Float = 0.3f) {
        ChromaKey.keyImage(px, size, size, mode, tol, soft)
    }

    @Test
    fun greenScreen_isRemovedAndSubjectKeptUntouched() {
        val px = split(green, skin)
        key(px, ChromaMode.GREEN)
        assertEquals(0, alpha(px[20 * size + 5]))
        assertEquals(0, alpha(px[0]))
        assertEquals(skin, px[20 * size + 35])
        assertEquals(skin, px[39 * size + 39])
    }

    @Test
    fun blueScreen_isRemovedAndSubjectKeptUntouched() {
        val px = split(blue, skin)
        key(px, ChromaMode.BLUE)
        assertEquals(0, alpha(px[20 * size + 5]))
        assertEquals(skin, px[20 * size + 35])
    }

    @Test
    fun wrongMode_keepsEverything() {
        val px = split(green, skin)
        key(px, ChromaMode.BLUE)
        assertEquals(green, px[20 * size + 5])
        assertEquals(skin, px[20 * size + 35])
    }

    @Test
    fun off_changesNothing() {
        val px = split(green, skin)
        key(px, ChromaMode.OFF)
        assertEquals(green, px[20 * size + 5])
    }

    @Test
    fun edge_hasNoOpaqueGreenFringe() {
        val px = split(green, skin)
        key(px, ChromaMode.GREEN)
        // Piksel terakhir layar harus transparan penuh; piksel pertama objek boleh setengah transparan, bukan hijau.
        assertEquals(0, alpha(px[20 * size + 19]))
        val edge = px[20 * size + 20]
        assertTrue(alpha(edge) in 1..254)
        val r = (edge shr 16) and 0xFF
        val g = (edge shr 8) and 0xFF
        assertTrue("tepi tidak boleh lebih hijau dari merah", g <= r)
    }

    @Test
    fun noise_specksInsideScreenAreRemoved() {
        val px = IntArray(size * size) { green }
        px[20 * size + 10] = skin // satu piksel noise berwarna objek di tengah layar
        key(px, ChromaMode.GREEN)
        assertEquals(0, alpha(px[20 * size + 10]))
        assertEquals(0, alpha(px[20 * size + 11]))
    }

    @Test
    fun imageWithoutScreen_isNotDamaged() {
        val px = IntArray(size * size) { skin }
        key(px, ChromaMode.GREEN)
        assertTrue(px.all { it == skin })
    }

    @Test
    fun darkPixels_areNotTreatedAsScreen() {
        val dark = 0xFF050805.toInt()
        val px = IntArray(size * size) { dark }
        key(px, ChromaMode.GREEN)
        assertTrue(px.all { alpha(it) == 255 })
    }

    @Test
    fun higherTolerance_removesAtLeastAsMuch() {
        val weak = 0xFF649664.toInt() // hijau lemah (100,150,100)
        val strict = IntArray(size * size) { weak }
        val loose = IntArray(size * size) { weak }
        key(strict, ChromaMode.GREEN, tol = 0.1f)
        key(loose, ChromaMode.GREEN, tol = 0.95f)
        val strictAlpha = strict.sumOf { alpha(it) }
        val looseAlpha = loose.sumOf { alpha(it) }
        assertTrue(looseAlpha <= strictAlpha)
        assertEquals(0, alpha(loose[20 * size + 20]))
    }

    @Test
    fun alreadyTransparentPixels_stayTransparent() {
        val px = IntArray(size * size) { 0x00000000 }
        key(px, ChromaMode.GREEN)
        assertTrue(px.all { alpha(it) == 0 })
    }
}
