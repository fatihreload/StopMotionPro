package com.fatih.stopmotion.ui

import com.fatih.stopmotion.data.AppSettings
import com.fatih.stopmotion.export.ExportFormat
import com.fatih.stopmotion.export.sizeOptions
import com.fatih.stopmotion.ui.camera.CaptureQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureQualityTest {

    @Test
    fun labelsAreSdHdHdPlus2kAnd4k() {
        assertEquals(listOf("SD", "HD", "HD+", "2K", "4K", "Maks"), CaptureQuality.entries.map { it.label })
    }

    @Test
    fun targets_areFourByThree() {
        for (q in CaptureQuality.entries.filter { it.hasTarget }) {
            assertEquals(q.label, q.width * 3, q.height * 4)
        }
    }

    @Test
    fun shortSides_matchTheExportQualityOptions() {
        val heights = CaptureQuality.entries.filter { it.hasTarget }.map { it.height }
        assertEquals(sizeOptions(ExportFormat.MP4), heights)
    }

    @Test
    fun max_hasNoTarget() {
        assertFalse(CaptureQuality.MAX.hasTarget)
        assertEquals("Tertinggi", CaptureQuality.MAX.detail)
        assertEquals("640x480", CaptureQuality.SD.detail)
    }

    @Test
    fun fromIndex_fallsBackToMax() {
        assertEquals(CaptureQuality.SD, CaptureQuality.fromIndex(0))
        assertEquals(CaptureQuality.K4, CaptureQuality.fromIndex(4))
        assertEquals(CaptureQuality.MAX, CaptureQuality.fromIndex(99))
        assertEquals(CaptureQuality.MAX, CaptureQuality.fromIndex(-1))
    }

    @Test
    fun defaultSetting_isMax() {
        assertTrue(AppSettings.DEFAULT_QUALITY == CaptureQuality.MAX.ordinal)
    }
}
