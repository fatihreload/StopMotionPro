package com.fatih.stopmotion.fx

import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.data.LayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class LayerModelsTest {

    private fun layer(
        kind: Int = LayerKind.IMAGE,
        frameCount: Int = 1,
        frameRate: Float = 0f,
        startMs: Long = 0,
        durationMs: Long = 2000,
        loop: Boolean = false,
        visible: Boolean = true,
        srcW: Int = 200,
        srcH: Int = 100,
        scale: Float = 0.5f,
        posX: Float = 0.5f,
        posY: Float = 0.5f,
        rotation: Float = 0f,
        frameDir: String? = null,
        filePath: String = "/x/src.png"
    ) = LayerEntity(
        id = 1, projectId = 1, name = "L", kind = kind, filePath = filePath, frameDir = frameDir,
        frameCount = frameCount, frameRate = frameRate, srcWidth = srcW, srcHeight = srcH, zOrder = 0,
        startMs = startMs, durationMs = durationMs, loopClip = loop, posX = posX, posY = posY,
        scale = scale, rotation = rotation, visible = visible, createdAt = 0
    )

    private fun video(durationMs: Long, loop: Boolean = false) =
        layer(kind = LayerKind.VIDEO, frameCount = 5, frameRate = 10f, durationMs = durationMs, loop = loop)

    // ---------------- Waktu ----------------

    @Test
    fun image_isShownOnlyInsideItsWindow() {
        val l = layer(startMs = 1000, durationMs = 2000)
        assertNull(LayerTiming.frameIndex(l, 999))
        assertEquals(0, LayerTiming.frameIndex(l, 1000))
        assertEquals(0, LayerTiming.frameIndex(l, 2999))
        assertNull(LayerTiming.frameIndex(l, 3000))
    }

    @Test
    fun hiddenOrEmptyLayer_isNeverShown() {
        assertNull(LayerTiming.frameIndex(layer(visible = false), 100))
        assertNull(LayerTiming.frameIndex(layer(durationMs = 0), 0))
    }

    @Test
    fun video_followsClipFrameRate() {
        val l = video(durationMs = 500)
        assertEquals(0, LayerTiming.frameIndex(l, 0))
        assertEquals(1, LayerTiming.frameIndex(l, 100))
        assertEquals(2, LayerTiming.frameIndex(l, 250))
        assertEquals(4, LayerTiming.frameIndex(l, 450))
        assertNull(LayerTiming.frameIndex(l, 500))
    }

    @Test
    fun video_endsWhenClipRunsOutAndIsNotLooped() {
        val l = video(durationMs = 1000)
        assertEquals(4, LayerTiming.frameIndex(l, 499))
        assertNull(LayerTiming.frameIndex(l, 500))
        assertNull(LayerTiming.frameIndex(l, 900))
    }

    @Test
    fun video_loopsWhenEnabled() {
        val l = video(durationMs = 1000, loop = true)
        assertEquals(0, LayerTiming.frameIndex(l, 500))
        assertEquals(1, LayerTiming.frameIndex(l, 600))
    }

    @Test
    fun singleFrameVideo_alwaysShowsFrameZero() {
        val l = layer(kind = LayerKind.VIDEO, frameCount = 1, frameRate = 10f, durationMs = 1000)
        assertEquals(0, LayerTiming.frameIndex(l, 800))
    }

    @Test
    fun tickAndFilmTime() {
        assertEquals(500L, LayerTiming.tickMs(6, 12))
        assertEquals(0L, LayerTiming.tickMs(0, 12))
        assertEquals(5000L, LayerTiming.tickMs(5, 0)) // fps tidak valid dianggap 1
        assertEquals(2000L, LayerTiming.filmMs(24, 12))
    }

    // ---------------- Geometri ----------------

    @Test
    fun rect_scalesFromFrameWidthAndKeepsAspect() {
        val r = LayerGeometry.rect(layer(srcW = 200, srcH = 100, scale = 0.5f, posX = 0.5f, posY = 0.25f, rotation = 30f), 400f, 300f)
        assertEquals(200f, r.w, 1e-3f)
        assertEquals(100f, r.h, 1e-3f)
        assertEquals(200f, r.cx, 1e-3f)
        assertEquals(75f, r.cy, 1e-3f)
        assertEquals(30f, r.rotation, 1e-6f)
    }

    @Test
    fun rect_survivesBrokenSourceSize() {
        val r = LayerGeometry.rect(layer(srcW = 0, srcH = 0, scale = 1f), 100f, 100f)
        assertEquals(100f, r.w, 1e-3f)
        assertEquals(100f, r.h, 1e-3f)
    }

    // ---------------- Berkas ----------------

    @Test
    fun framePath_imageUsesSourceFile() {
        assertEquals("/x/src.png", LayerFiles.framePath(layer(), 0))
    }

    @Test
    fun framePath_videoUsesNumberedJpeg() {
        val l = layer(kind = LayerKind.VIDEO, frameCount = 20, frameRate = 10f, frameDir = "/x/f")
        assertEquals(File("/x/f", "00001.jpg").path, LayerFiles.framePath(l, 0))
        assertEquals(File("/x/f", "00012.jpg").path, LayerFiles.framePath(l, 11))
    }

    @Test
    fun framePath_videoWithoutFolderIsNull() {
        assertNull(LayerFiles.framePath(layer(kind = LayerKind.VIDEO, frameDir = null), 0))
    }

    @Test
    fun maxLayers_isTwenty() {
        assertEquals(20, LayerTiming.MAX_LAYERS)
    }
}
