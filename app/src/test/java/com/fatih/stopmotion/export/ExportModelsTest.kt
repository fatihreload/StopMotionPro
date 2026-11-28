package com.fatih.stopmotion.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportModelsTest {

    @Test
    fun outputSize_portraitFollowsPhotoRatio() {
        assertEquals(720 to 960, computeOutputSize(3000, 4000, 720, 16))
        assertEquals(720 to 1280, computeOutputSize(1080, 1920, 720, 16))
    }

    @Test
    fun outputSize_landscapeAndSquare() {
        assertEquals(960 to 720, computeOutputSize(4000, 3000, 720, 16))
        assertEquals(720 to 720, computeOutputSize(1000, 1000, 720, 16))
    }

    @Test
    fun outputSize_isAlwaysMultipleOfAlignment() {
        for (align in listOf(2, 16)) {
            for ((w, h) in listOf(1000 to 1333, 1234 to 987, 640 to 481, 3 to 7)) {
                val (ow, oh) = computeOutputSize(w, h, 360, align)
                assertEquals(0, ow % align)
                assertEquals(0, oh % align)
            }
        }
    }

    @Test
    fun outputSize_roundsToNearestMultiple() {
        assertEquals(360 to 480, computeOutputSize(1000, 1333, 360, 2))
    }

    @Test
    fun outputSize_neverCollapsesToZero() {
        assertEquals(16 to 16, computeOutputSize(1, 1, 1, 16))
        assertEquals(720 to 720, computeOutputSize(0, 0, 720, 16))
    }

    @Test
    fun bitrate_growsWithQuality() {
        val low = videoBitrate(720, 960, 12, 0)
        val mid = videoBitrate(720, 960, 12, 1)
        val high = videoBitrate(720, 960, 12, 2)
        assertTrue(low < mid)
        assertTrue(mid < high)
    }

    @Test
    fun bitrate_isClampedToSaneRange() {
        assertEquals(1_000_000, videoBitrate(16, 16, 1, 0))
        assertEquals(40_000_000, videoBitrate(2160, 2880, 60, 2))
    }

    @Test
    fun bitrate_lowFpsIsTreatedAs15() {
        assertEquals(videoBitrate(720, 960, 15, 1), videoBitrate(720, 960, 5, 1))
        assertEquals(videoBitrate(720, 960, 15, 1), videoBitrate(720, 960, 12, 1))
    }

    @Test
    fun sizeOptions_perFormat() {
        assertEquals(listOf(480, 720, 1080, 1440, 2160), sizeOptions(ExportFormat.MP4))
        assertEquals(listOf(240, 360, 480), sizeOptions(ExportFormat.GIF))
        assertTrue(sizeOptions(ExportFormat.ZIP).isEmpty())
    }

    @Test
    fun defaultShortSide_perFormat() {
        assertEquals(720, defaultShortSide(ExportFormat.MP4))
        assertEquals(360, defaultShortSide(ExportFormat.GIF))
    }

    @Test
    fun formats_haveMatchingExtensionAndMime() {
        assertEquals("mp4", ExportFormat.MP4.ext)
        assertEquals("video/mp4", ExportFormat.MP4.mime)
        assertEquals("gif", ExportFormat.GIF.ext)
        assertEquals("image/gif", ExportFormat.GIF.mime)
        assertEquals("zip", ExportFormat.ZIP.ext)
        assertEquals("application/zip", ExportFormat.ZIP.mime)
    }

    @Test
    fun qualityLabels_followSdHdTwoKFourK() {
        assertEquals("SD 480p", qualityLabel(480))
        assertEquals("HD 720p", qualityLabel(720))
        assertEquals("HD+ 1080p", qualityLabel(1080))
        assertEquals("2K 1440p", qualityLabel(1440))
        assertEquals("4K 2160p", qualityLabel(2160))
        assertEquals("360p", qualityLabel(360))
    }

    @Test
    fun outputSize_forTwoKAndFourK() {
        assertEquals(1440 to 1920, computeOutputSize(3000, 4000, 1440, 16))
        assertEquals(2160 to 2880, computeOutputSize(3000, 4000, 2160, 16))
    }

    @Test
    fun upscaleDetection() {
        assertFalse(isUpscaled(4000, 3000, 2880, 2160))
        assertTrue(isUpscaled(1000, 750, 1440, 1080))
        assertFalse(isUpscaled(1000, 750, 1040, 780))
        assertFalse(isUpscaled(1440, 1080, 1440, 1080))
    }

    @Test
    fun defaultSettings() {
        val s = ExportSettings()
        assertEquals(ExportFormat.MP4, s.format)
        assertEquals(720, s.shortSide)
        assertEquals(1, s.quality)
        assertTrue(s.includeAudio)
        assertTrue(s.loop)
    }
}
