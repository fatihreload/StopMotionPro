package com.fatih.stopmotion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FxModelsTest {

    @Test
    fun geo_identityCases() {
        assertTrue(GeoParams().isIdentity)
        assertTrue(GeoParams(quarterTurns = 4).isIdentity)
        assertTrue(GeoParams(quarterTurns = -4).isIdentity)
        assertTrue(GeoParams(zoom = 1.0005f).isIdentity)
    }

    @Test
    fun geo_anyChangeIsNotIdentity() {
        assertFalse(GeoParams(quarterTurns = 1).isIdentity)
        assertFalse(GeoParams(flipH = true).isIdentity)
        assertFalse(GeoParams(flipV = true).isIdentity)
        assertFalse(GeoParams(aspect = 2).isIdentity)
        assertFalse(GeoParams(zoom = 1.5f).isIdentity)
    }

    @Test
    fun fxState_defaultDetection() {
        assertTrue(FxState().isDefault)
        assertTrue(FxState(vignette = 0.005f).isDefault)
        assertFalse(FxState(filter = 1).isDefault)
        assertFalse(FxState(vignette = 0.3f).isDefault)
        assertFalse(FxState(fadeIn = 1).isDefault)
        assertFalse(FxState(fadeOut = 1).isDefault)
    }

    @Test
    fun aspectOptions_matchTheCropUi() {
        assertEquals(listOf("Asli", "1:1", "3:4", "4:3", "9:16", "16:9"), AspectOptions.map { it.label })
        assertNull(AspectOptions[0].ratio)
        assertEquals(0.75f, AspectOptions[2].ratio!!, 1e-6f)
    }

    @Test
    fun filterNames_coverAllFilterIds() {
        assertEquals(8, FilterNames.size)
        assertEquals("Normal", FilterNames[0])
    }
}
