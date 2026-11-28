package com.fatih.stopmotion.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoundFxTest {

    private fun render(id: String): ByteArray {
        val file = File.createTempFile("sfx_$id", ".wav")
        try {
            SoundFx.writeWav(file, id)
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    @Test
    fun presets_haveUniqueIdsAndLabels() {
        assertEquals(7, SoundFx.presets.size)
        assertEquals(SoundFx.presets.size, SoundFx.presets.map { it.id }.toSet().size)
        assertTrue(SoundFx.presets.all { it.label.isNotBlank() })
    }

    @Test
    fun everyPreset_writesValidMonoWav() {
        for (p in SoundFx.presets) {
            val bytes = render(p.id)
            assertTrue("${p.id} terlalu pendek", bytes.size > 44)
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            assertEquals("data", String(bytes, 36, 4))
            assertEquals(bytes.size - 8, b.getInt(4))
            assertEquals(1, b.getShort(22).toInt())
            assertEquals(44100, b.getInt(24))
            assertEquals(16, b.getShort(34).toInt())
            assertEquals(bytes.size - 44, b.getInt(40))
        }
    }

    @Test
    fun everyPreset_isAudible() {
        for (p in SoundFx.presets) {
            val b = ByteBuffer.wrap(render(p.id)).order(ByteOrder.LITTLE_ENDIAN)
            var peak = 0
            var i = 44
            while (i + 1 < b.limit()) {
                peak = maxOf(peak, abs(b.getShort(i).toInt()))
                i += 2
            }
            assertTrue("${p.id} hampir senyap (puncak $peak)", peak > 500)
        }
    }

    @Test
    fun shortEffectsAreShorterThanLongOnes() {
        assertTrue(render("pop").size < render("ding").size)
        assertTrue(render("klik").size < render("whoosh").size)
        assertTrue(render("klik").size < render("shutter").size)
    }
}
