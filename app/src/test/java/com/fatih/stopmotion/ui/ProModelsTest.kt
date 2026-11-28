package com.fatih.stopmotion.ui

import com.fatih.stopmotion.ui.camera.FocusMode
import com.fatih.stopmotion.ui.camera.ProCaps
import com.fatih.stopmotion.ui.camera.ProMath
import com.fatih.stopmotion.ui.camera.ProPlan
import com.fatih.stopmotion.ui.camera.ProState
import com.fatih.stopmotion.ui.camera.ProStateCodec
import com.fatih.stopmotion.ui.camera.WbMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProModelsTest {

    private val full = ProCaps(
        manualSensor = true, isoMin = 50, isoMax = 3200,
        shutterMinNs = 250_000L, shutterMaxNs = 250_000_000L,
        minFocusDiopter = 10f, awbModes = emptySet(),
        evMin = -12, evMax = 12, evStep = 0.33f, zoomMin = 1f, zoomMax = 8f
    )

    // ---------------- Codec ----------------

    @Test
    fun codec_roundTrip() {
        val s = ProState(FocusMode.LOCK_MANUAL, 2.5f, WbMode.DAYLIGHT, -3, 4.5f, 16_666_666L, 400)
        assertEquals("3,2.5,4,-3,4.5,16666666,400", ProStateCodec.encode(s))
        assertEquals(s, ProStateCodec.decode(ProStateCodec.encode(s)))
    }

    @Test
    fun codec_brokenDataGivesDefaults() {
        assertEquals(ProState(), ProStateCodec.decode(null))
        assertEquals(ProState(), ProStateCodec.decode(""))
        assertEquals(ProState(), ProStateCodec.decode("1,2,3"))
        assertEquals(ProState(), ProStateCodec.decode("a,b,c,d,e,f,g"))
    }

    @Test
    fun codec_clampsValues() {
        val s = ProStateCodec.decode("9,99,9,500,99,-5,-7")
        assertEquals(FocusMode.AUTO, s.focus)
        assertEquals(50f, s.focusDistance, 0f)
        assertEquals(WbMode.AUTO, s.wb)
        assertEquals(50, s.ev)
        assertEquals(10f, s.zoom, 0f)
        assertEquals(0L, s.shutterNs)
        assertEquals(0, s.iso)
    }

    @Test
    fun state_defaultDetection() {
        assertTrue(ProState().isDefault)
        assertFalse(ProState(zoom = 2f).isDefault)
    }

    // ---------------- Nilai rana dan ISO ----------------

    @Test
    fun shutterStops_filteredByCameraRange() {
        assertEquals(11, ProMath.shutterStops(250_000L, 250_000_000L).size)
        assertEquals(9, ProMath.shutterStops(1_000_000L, 250_000_000L).size)
        assertTrue(ProMath.shutterStops(5L, 5L).isEmpty())
    }

    @Test
    fun isoStops_filteredAndFallback() {
        assertEquals(listOf(100, 200, 400, 800, 1600), ProMath.isoStops(100, 1600))
        assertTrue(ProMath.isoStops(0, 0).isEmpty())
        assertEquals(listOf(60, 90), ProMath.isoStops(60, 90))
    }

    @Test
    fun defaults_arePracticalForStopMotion() {
        assertEquals(1_000_000_000L / 60, ProMath.defaultShutter(ProMath.shutterStops(250_000L, 250_000_000L)))
        assertEquals(0L, ProMath.defaultShutter(emptyList()))
        assertEquals(100, ProMath.defaultIso(listOf(50, 100, 200)))
        assertEquals(0, ProMath.defaultIso(emptyList()))
    }

    @Test
    fun nearestIndex() {
        assertEquals(1, ProMath.nearestIndex(listOf(10L, 20L, 30L), 24L))
        assertEquals(2, ProMath.nearestIsoIndex(listOf(100, 200, 400), 390))
    }

    @Test
    fun labels() {
        assertEquals("Otomatis", ProMath.shutterLabel(0))
        assertEquals("1/125", ProMath.shutterLabel(8_000_000L))
        assertEquals("1/60", ProMath.shutterLabel(16_666_666L))
        assertEquals("0.5 s", ProMath.shutterLabel(500_000_000L))
        assertEquals("1.0 s", ProMath.shutterLabel(1_000_000_000L))
        assertEquals("+1.0 EV", ProMath.evLabel(3, 0.33f))
        assertEquals("-1.0 EV", ProMath.evLabel(-2, 0.5f))
        assertEquals("2.5x", ProMath.zoomLabel(2.5f))
        assertEquals("10.0x", ProMath.zoomLabel(10f))
    }

    @Test
    fun zoom_isClampedToCameraAndTenTimes() {
        assertEquals(5f, ProMath.clampZoom(8f, ProCaps(zoomMin = 1f, zoomMax = 5f)), 0f)
        assertEquals(1f, ProMath.clampZoom(0.5f, ProCaps(zoomMin = 1f, zoomMax = 5f)), 0f)
        assertEquals(10f, ProMath.clampZoom(15f, ProCaps(zoomMin = 0.6f, zoomMax = 20f)), 0f)
        assertEquals(1f, ProMath.clampZoom(3f, ProCaps(zoomMin = 1f, zoomMax = 1f)), 0f)
    }

    // ---------------- Kemampuan ----------------

    @Test
    fun caps_featureFlags() {
        assertTrue(full.hasManualExposure)
        assertTrue(full.hasManualFocus)
        assertTrue(full.hasEv)
        assertFalse(ProCaps().hasManualExposure)
        assertFalse(ProCaps(manualSensor = false, isoMax = 100, shutterMaxNs = 10).hasManualExposure)
        assertFalse(ProCaps().hasManualFocus)
        assertFalse(ProCaps().hasEv)
    }

    @Test
    fun caps_awbSupport() {
        assertTrue(ProCaps().supportsWb(WbMode.TWILIGHT)) // tidak diketahui = dianggap ada
        val limited = ProCaps(awbModes = setOf(1, 5))
        assertTrue(limited.supportsWb(WbMode.DAYLIGHT))
        assertFalse(limited.supportsWb(WbMode.INCANDESCENT))
    }

    // ---------------- Rencana ----------------

    @Test
    fun plan_autoFocusChangesNothing() {
        val p = ProPlan.build(ProState(), full)
        assertFalse(p.aeManual)
        assertFalse(p.aeLock)
        assertEquals(ProPlan.AWB_AUTO, p.awbMode)
        assertFalse(p.awbLock)
        assertFalse(p.afManual)
        assertFalse(p.holdFocus)
        assertFalse(p.holdExposure)
        assertEquals(0, p.evIndex)
        assertEquals(1f, p.zoom, 0f)
        assertFalse(p.needsCamera2)
    }

    @Test
    fun plan_manualFocus() {
        val p = ProPlan.build(ProState(focus = FocusMode.MANUAL, focusDistance = 3f), full)
        assertTrue(p.afManual)
        assertEquals(3f, p.focusDistance, 0f)
        assertTrue(p.needsCamera2)
        assertEquals(10f, ProPlan.build(ProState(focus = FocusMode.MANUAL, focusDistance = 20f), full).focusDistance, 0f)
        assertFalse(ProPlan.build(ProState(focus = FocusMode.MANUAL, focusDistance = 3f), full.copy(minFocusDiopter = 0f)).afManual)
    }

    @Test
    fun plan_lockFocusAndExposure() {
        val p = ProPlan.build(ProState(focus = FocusMode.LOCK), full)
        assertTrue(p.aeLock)
        assertFalse(p.aeManual)
        assertTrue(p.holdFocus)
        assertTrue(p.holdExposure)
        assertTrue(p.needsCamera2)
    }

    @Test
    fun plan_lockFocusWithManualExposure() {
        val p = ProPlan.build(ProState(focus = FocusMode.LOCK_MANUAL, ev = 5), full)
        assertTrue(p.aeManual)
        assertFalse(p.aeLock)
        assertTrue(p.holdFocus)
        assertFalse(p.holdExposure)
        assertEquals(1_000_000_000L / 60, p.exposureNs)
        assertEquals(100, p.iso)
        assertEquals(0, p.evIndex) // EV tidak berlaku saat eksposur manual
    }

    @Test
    fun plan_manualExposureUsesChosenShutterAndIso() {
        val p = ProPlan.build(ProState(focus = FocusMode.LOCK_MANUAL, shutterNs = 8_000_000L, iso = 800), full)
        assertEquals(8_000_000L, p.exposureNs)
        assertEquals(800, p.iso)
    }

    @Test
    fun plan_manualExposureNeedsManualSensor() {
        assertFalse(ProPlan.build(ProState(focus = FocusMode.LOCK_MANUAL), full.copy(manualSensor = false)).aeManual)
    }

    @Test
    fun plan_exposureCompensation() {
        assertEquals(5, ProPlan.build(ProState(ev = 5), full).evIndex)
        assertEquals(12, ProPlan.build(ProState(ev = 99), full).evIndex)
        assertEquals(0, ProPlan.build(ProState(ev = 5), full.copy(evMin = 0, evMax = 0)).evIndex)
    }

    @Test
    fun plan_whiteBalance() {
        val locked = ProPlan.build(ProState(wb = WbMode.LOCKED), full)
        assertTrue(locked.awbLock)
        assertEquals(1, locked.awbMode)
        assertTrue(locked.needsCamera2)
        assertEquals(5, ProPlan.build(ProState(wb = WbMode.DAYLIGHT), full).awbMode)
        val fallback = ProPlan.build(ProState(wb = WbMode.INCANDESCENT), full.copy(awbModes = setOf(1)))
        assertEquals(ProPlan.AWB_AUTO, fallback.awbMode)
        assertFalse(fallback.awbLock)
    }

    @Test
    fun plan_zoomIsClampedToCamera() {
        assertEquals(8f, ProPlan.build(ProState(zoom = 20f), full).zoom, 0f)
        assertEquals(2.5f, ProPlan.build(ProState(zoom = 2.5f), full).zoom, 0f)
    }
}
