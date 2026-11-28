package com.fatih.stopmotion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyTest {

    private val greenScreen = 0xFF00B140.toInt() // (0, 177, 64)
    private val blueScreen = 0xFF0047BB.toInt()  // (0, 71, 187)
    private val skin = 0xFFE0AC69.toInt()        // (224, 172, 105)

    private fun key(color: Int, mode: Int, tolerance: Float = 0.5f, softness: Float = 0.3f): Int {
        val px = intArrayOf(color)
        ChromaKey.keyPixels(px, mode, tolerance, softness)
        return px[0]
    }

    private fun alpha(c: Int) = (c ushr 24) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF

    @Test
    fun greenScreenIsRemovedInGreenMode() {
        assertEquals(0, alpha(key(greenScreen, ChromaMode.GREEN)))
    }

    @Test
    fun blueScreenIsRemovedInBlueMode() {
        assertEquals(0, alpha(key(blueScreen, ChromaMode.BLUE)))
    }

    @Test
    fun greenIsKeptInBlueModeAndViceVersa() {
        assertEquals(greenScreen, key(greenScreen, ChromaMode.BLUE))
        assertEquals(blueScreen, key(blueScreen, ChromaMode.GREEN))
    }

    @Test
    fun skinWhiteAndBlackAreKept() {
        assertEquals(skin, key(skin, ChromaMode.GREEN))
        assertEquals(0xFFFFFFFF.toInt(), key(0xFFFFFFFF.toInt(), ChromaMode.GREEN))
        assertEquals(0xFF000000.toInt(), key(0xFF000000.toInt(), ChromaMode.GREEN))
    }

    @Test
    fun veryDarkPixelsAreKeptBecauseTheirColorIsUnreliable() {
        val darkGreen = 0xFF001E00.toInt()
        assertEquals(darkGreen, key(darkGreen, ChromaMode.GREEN))
    }

    @Test
    fun alreadyTransparentPixelsStayTransparent() {
        assertEquals(0, key(0x00000000, ChromaMode.GREEN))
    }

    @Test
    fun modeOffChangesNothing() {
        assertEquals(greenScreen, key(greenScreen, ChromaMode.OFF))
    }

    @Test
    fun edgePixelsGetPartialAlphaAndLoseTheGreenSpill() {
        val edge = 0xFF7AC87A.toInt() // (122, 200, 122)
        val result = key(edge, ChromaMode.GREEN)
        assertTrue("alpha ${alpha(result)}", alpha(result) in 100..160)
        assertEquals(122, green(result))
    }

    @Test
    fun higherToleranceRemovesMore() {
        val edge = 0xFF7AC87A.toInt()
        assertEquals(255, alpha(key(edge, ChromaMode.GREEN, tolerance = 0.2f)))
        assertEquals(0, alpha(key(edge, ChromaMode.GREEN, tolerance = 0.9f)))
    }

    @Test
    fun weakGreenTintOnKeptPixelsIsReducedButStaysOpaque() {
        val tinted = 0xFF649664.toInt() // (100, 150, 100)
        val result = key(tinted, ChromaMode.GREEN, tolerance = 0f)
        assertEquals(255, alpha(result))
        assertTrue(green(result) in 130..140)
    }

    @Test
    fun invalidModeIsIgnored() {
        assertEquals(greenScreen, key(greenScreen, 7))
        assertFalse(alpha(key(greenScreen, 7)) == 0)
    }
}
