package com.fatih.stopmotion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaStateTest {

    @Test
    fun active_onlyForGreenAndBlue() {
        assertFalse(ChromaState(mode = ChromaMode.OFF).active)
        assertTrue(ChromaState(mode = ChromaMode.GREEN).active)
        assertTrue(ChromaState(mode = ChromaMode.BLUE).active)
        assertFalse(ChromaState(mode = 3).active)
    }

    @Test
    fun cacheKey_changesWithKeyingSettingsOnly() {
        val base = ChromaState(mode = ChromaMode.GREEN, tolerance = 0.4f, softness = 0.3f)
        assertEquals("1|40|30", base.cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(tolerance = 0.5f).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(softness = 0.1f).cacheKey())
        assertNotEquals(base.cacheKey(), base.copy(mode = ChromaMode.BLUE).cacheKey())
        // Latar digambar terpisah, jadi tidak mengubah hasil keying.
        assertEquals(base.cacheKey(), base.copy(bgMode = 2, bgPath = "/x.jpg").cacheKey())
    }

    @Test
    fun backgroundColors() {
        assertEquals(0xFF000000.toInt(), ChromaBg.color(0))
        assertEquals(0xFFFFFFFF.toInt(), ChromaBg.color(1))
        assertEquals(0xFF000000.toInt(), ChromaBg.color(99))
        assertEquals(5, ChromaBg.names.size)
        assertEquals(4, ChromaBg.IMAGE)
    }
}
