package com.fatih.stopmotion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BackupManifestTest {

    private val project = ProjectEntity(
        id = 7, name = "Film Saya", createdAt = 1, updatedAt = 2, fps = 24,
        onionEnabled = false, onionOpacity = 0.25f, onionFrameCount = 3, onionColor = 2,
        fxFilter = 2, fxIntensity = 0.5f, fxVignette = 0.3f, fxFadeIn = 6, fxFadeOut = 8, fxFadeWhite = true
    )

    private val frames = listOf(
        FrameEntity(id = 1, projectId = 7, position = 0, filePath = "/x/a.jpg", thumbPath = "/x/t.jpg", holdCount = 3, createdAt = 111),
        FrameEntity(id = 2, projectId = 7, position = 1, filePath = "/x/b.jpg", thumbPath = "/x/u.jpg", holdCount = 1, createdAt = 222)
    )

    private val tracks = listOf(
        AudioTrackEntity(
            id = 1, projectId = 7, name = "Musik", filePath = "/x/a.MP3", kind = AudioKind.IMPORT,
            durationMs = 10_000, startMs = 500, trimStartMs = 1000, trimEndMs = 9000,
            volume = 0.8f, fadeInMs = 200, fadeOutMs = 300, muted = true, createdAt = 5
        )
    )

    @Test
    fun roundTrip_keepsProjectSettings() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks))
        val p = parsed.project
        assertEquals("Film Saya", p.name)
        assertEquals(24, p.fps)
        assertFalse(p.onionEnabled)
        assertEquals(0.25f, p.onionOpacity, 0f)
        assertEquals(3, p.onionFrameCount)
        assertEquals(2, p.onionColor)
        assertEquals(2, p.fxFilter)
        assertEquals(0.5f, p.fxIntensity, 0f)
        assertEquals(0.3f, p.fxVignette, 0f)
        assertEquals(6, p.fxFadeIn)
        assertEquals(8, p.fxFadeOut)
        assertTrue(p.fxFadeWhite)
    }

    @Test
    fun roundTrip_keepsFramesInOrder() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks))
        assertEquals(2, parsed.frames.size)
        assertEquals("frames/00001.jpg", parsed.frames[0].file)
        assertEquals(3, parsed.frames[0].hold)
        assertEquals(111L, parsed.frames[0].createdAt)
        assertEquals("frames/00002.jpg", parsed.frames[1].file)
    }

    @Test
    fun roundTrip_keepsAudioSettings() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks))
        assertEquals(1, parsed.audio.size)
        val a = parsed.audio[0]
        assertEquals("audio/001.mp3", a.file)
        assertEquals("Musik", a.name)
        assertEquals(AudioKind.IMPORT, a.kind)
        assertEquals(10_000L, a.durationMs)
        assertEquals(500L, a.startMs)
        assertEquals(1000L, a.trimStartMs)
        assertEquals(9000L, a.trimEndMs)
        assertEquals(0.8f, a.volume, 0f)
        assertEquals(200L, a.fadeInMs)
        assertEquals(300L, a.fadeOutMs)
        assertTrue(a.muted)
    }

    @Test
    fun roundTrip_keepsChromaSettingsAndBackgroundEntry() {
        val withChroma = project.copy(
            chromaMode = 2, chromaTolerance = 0.7f, chromaSoftness = 0.15f, chromaBgMode = 4, chromaBgPath = "/x/bg.png"
        )
        val entry = BackupManifest.backgroundEntry("/x/bg.png")
        val parsed = BackupManifest.parse(BackupManifest.toJson(withChroma, frames, tracks, entry))
        assertEquals("background.png", parsed.backgroundFile)
        assertEquals(2, parsed.project.chromaMode)
        assertEquals(0.7f, parsed.project.chromaTolerance, 0f)
        assertEquals(0.15f, parsed.project.chromaSoftness, 0f)
        assertEquals(4, parsed.project.chromaBgMode)
        // Path file asli tidak dibawa ke cadangan; dibuat ulang saat dipulihkan.
        assertNull(parsed.project.chromaBgPath)
    }

    @Test
    fun roundTrip_withoutBackgroundHasNoBackgroundFile() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks))
        assertNull(parsed.backgroundFile)
        assertEquals(0, parsed.project.chromaMode)
    }

    @Test
    fun parse_clampsChromaValues() {
        val json = """{"format":"FatihStopMotion","version":1,"frames":[],
            "chromaMode":9,"chromaTolerance":5,"chromaSoftness":-2,"chromaBgMode":99}"""
        val p = BackupManifest.parse(json).project
        assertEquals(2, p.chromaMode)
        assertEquals(1f, p.chromaTolerance, 0f)
        assertEquals(0f, p.chromaSoftness, 0f)
        assertEquals(4, p.chromaBgMode)
    }

    @Test
    fun backgroundEntry_usesSafeExtension() {
        assertEquals("background.png", BackupManifest.backgroundEntry("/a/b.PNG"))
        assertEquals("background.jpg", BackupManifest.backgroundEntry("/a/noext"))
        assertEquals("background.jpg", BackupManifest.backgroundEntry("/a/x.toolongext"))
    }

    @Test
    fun parse_fillsDefaultsAndClampsValues() {
        val json = """{"format":"FatihStopMotion","version":1,"name":"  X  ","fps":500,
            "frames":[{"file":"frames/00001.jpg","hold":99}]}"""
        val parsed = BackupManifest.parse(json)
        assertEquals("X", parsed.project.name)
        assertEquals(60, parsed.project.fps)
        assertTrue(parsed.project.onionEnabled)
        assertEquals(0, parsed.project.fxFilter)
        assertEquals(10, parsed.frames[0].hold)
        assertEquals(0L, parsed.frames[0].createdAt)
        assertTrue(parsed.audio.isEmpty())
    }

    @Test
    fun parse_clampsBrokenAudioValues() {
        val json = """{"format":"FatihStopMotion","version":1,"frames":[],
            "audio":[{"file":"audio/001.mp3","durationMs":1000,"trimStartMs":5000,"trimEndMs":10,"volume":7}]}"""
        val a = BackupManifest.parse(json).audio[0]
        assertEquals("Audio", a.name)
        assertEquals(999L, a.trimStartMs)
        assertEquals(1000L, a.trimEndMs)
        assertEquals(1f, a.volume, 0f)
    }

    @Test(expected = IOException::class)
    fun parse_rejectsBrokenJson() {
        BackupManifest.parse("{not json")
    }

    @Test(expected = IOException::class)
    fun parse_rejectsOtherFormat() {
        BackupManifest.parse("""{"format":"Lain","version":1,"frames":[]}""")
    }

    @Test(expected = IOException::class)
    fun parse_rejectsNewerVersion() {
        BackupManifest.parse("""{"format":"FatihStopMotion","version":99,"frames":[]}""")
    }

    @Test
    fun entryNames_areBuiltFromIndexNotSourcePath() {
        assertEquals("frames/00001.jpg", BackupManifest.frameEntry(0))
        assertEquals("frames/00012.jpg", BackupManifest.frameEntry(11))
        assertEquals("audio/001.m4a", BackupManifest.audioEntry(0, "/a/b.M4A"))
        assertEquals("audio/003.bin", BackupManifest.audioEntry(2, "/a/noext"))
        assertEquals("audio/001.bin", BackupManifest.audioEntry(0, "/a/b.verylongext"))
    }

    @Test
    fun roundTrip_keepsEraseStrokes() {
        val erased = frames.mapIndexed { i, f -> if (i == 0) f.copy(eraseData = "e30:1000,2000,3000,4000;r50:5000,5000") else f }
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, erased, tracks))
        assertEquals("e30:1000,2000,3000,4000;r50:5000,5000", parsed.frames[0].erase)
        assertNull(parsed.frames[1].erase)
    }

    @Test
    fun parse_dropsBrokenEraseData() {
        val json = """{"format":"FatihStopMotion","version":1,
            "frames":[{"file":"frames/00001.jpg","hold":1,"erase":"garbage"}]}"""
        assertNull(BackupManifest.parse(json).frames[0].erase)
    }

    private val videoLayer = LayerEntity(
        id = 1, projectId = 7, name = "Peluru", kind = LayerKind.VIDEO, filePath = "/x/L1/src.mp4",
        frameDir = "/x/L1/f", frameCount = 24, frameRate = 12f, srcWidth = 640, srcHeight = 360, zOrder = 3,
        startMs = 1500, durationMs = 2000, loopClip = true, posX = 0.25f, posY = 0.75f, scale = 0.8f,
        rotation = 45f, opacity = 0.6f, chromaMode = 1, chromaTolerance = 0.5f, chromaSoftness = 0.2f,
        visible = false, createdAt = 9
    )

    private val imageLayer = LayerEntity(
        id = 2, projectId = 7, name = "Api", kind = LayerKind.IMAGE, filePath = "/x/L2/src.PNG",
        frameDir = null, frameCount = 1, frameRate = 0f, srcWidth = 300, srcHeight = 300, zOrder = 4,
        startMs = 0, durationMs = 1000, loopClip = false, createdAt = 10
    )

    @Test
    fun roundTrip_keepsVideoLayerSettings() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks, layers = listOf(videoLayer, imageLayer)))
        assertEquals(2, parsed.layers.size)
        val v = parsed.layers[0]
        assertEquals("Peluru", v.name)
        assertEquals(LayerKind.VIDEO, v.kind)
        assertEquals("layers/001/", v.file)
        assertEquals(24, v.frameCount)
        assertEquals(12f, v.frameRate, 0f)
        assertEquals(640, v.srcWidth)
        assertEquals(360, v.srcHeight)
        assertEquals(3, v.zOrder)
        assertEquals(1500L, v.startMs)
        assertEquals(2000L, v.durationMs)
        assertTrue(v.loopClip)
        assertEquals(0.25f, v.posX, 0f)
        assertEquals(0.75f, v.posY, 0f)
        assertEquals(0.8f, v.scale, 0f)
        assertEquals(45f, v.rotation, 0f)
        assertEquals(0.6f, v.opacity, 0f)
        assertEquals(1, v.chromaMode)
        assertEquals(0.5f, v.chromaTolerance, 0f)
        assertEquals(0.2f, v.chromaSoftness, 0f)
        assertFalse(v.visible)
    }

    @Test
    fun roundTrip_imageLayerUsesIndexBasedEntryName() {
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks, layers = listOf(videoLayer, imageLayer)))
        val i = parsed.layers[1]
        assertEquals(LayerKind.IMAGE, i.kind)
        assertEquals("layers/002/src.png", i.file)
        assertEquals(1, i.frameCount)
    }

    @Test
    fun parse_oldBackupWithoutLayersHasNone() {
        val json = """{"format":"FatihStopMotion","version":1,"frames":[]}"""
        assertTrue(BackupManifest.parse(json).layers.isEmpty())
    }

    @Test
    fun parse_clampsBrokenLayerValues() {
        val json = """{"format":"FatihStopMotion","version":1,"frames":[],
            "layers":[{"kind":1,"frames":999,"scale":99,"opacity":5,"durationMs":1,"chromaMode":9}]}"""
        val l = BackupManifest.parse(json).layers[0]
        assertEquals("Layer", l.name)
        assertEquals(240, l.frameCount)
        assertEquals(4f, l.scale, 0f)
        assertEquals(1f, l.opacity, 0f)
        assertEquals(100L, l.durationMs)
        assertEquals(2, l.chromaMode)
    }

    @Test(expected = IOException::class)
    fun parse_rejectsAbsurdLayerCount() {
        val many = List(41) { "{}" }.joinToString(",")
        BackupManifest.parse("""{"format":"FatihStopMotion","version":1,"frames":[],"layers":[$many]}""")
    }

    @Test
    fun layerEntryNames_areBuiltFromIndex() {
        assertEquals("layers/003/00012.jpg", BackupManifest.layerFrameEntry(2, 12))
        assertEquals("layers/001/", BackupManifest.layerFramesPrefix(0))
        assertEquals("layers/001/src.png", BackupManifest.layerImageEntry(0, "/a/noext"))
        assertEquals("layers/001/src.png", BackupManifest.layerImageEntry(0, "/a/b.verylongext"))
    }

    @Test
    fun roundTrip_keepsLayerKeyframes() {
        val anim = "0,5000,5000,3000,0,1000,1;2000,7000,3000,5000,9000,500,0"
        val layer = imageLayer.copy(keyframes = anim)
        val parsed = BackupManifest.parse(BackupManifest.toJson(project, frames, tracks, layers = listOf(layer, videoLayer)))
        assertEquals(anim, parsed.layers[0].keyframes)
        assertNull(parsed.layers[1].keyframes)
    }

    @Test
    fun parse_dropsBrokenKeyframes() {
        val json = """{"format":"FatihStopMotion","version":1,"frames":[],
            "layers":[{"kind":0,"keyframes":"garbage"}]}"""
        assertNull(BackupManifest.parse(json).layers[0].keyframes)
    }
}

