package com.fatih.stopmotion.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EraseCodecTest {

    @Test
    fun encode_emptyIsNull() {
        assertNull(EraseCodec.encode(emptyList()))
    }

    @Test
    fun encode_writesCompactText() {
        val s = EraseStroke(restore = false, radius = 0.03f, points = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        assertEquals("e30:1000,2000,3000,4000", EraseCodec.encode(listOf(s)))
    }

    @Test
    fun encode_marksRestoreStrokes() {
        val s = EraseStroke(restore = true, radius = 0.05f, points = floatArrayOf(0.5f, 0.5f))
        assertEquals("r50:5000,5000", EraseCodec.encode(listOf(s)))
    }

    @Test
    fun roundTrip_keepsStrokes() {
        val a = EraseStroke(false, 0.04f, floatArrayOf(0.1f, 0.2f, 0.6f, 0.7f))
        val b = EraseStroke(true, 0.02f, floatArrayOf(0.9f, 0.8f))
        val back = EraseCodec.decode(EraseCodec.encode(listOf(a, b)))
        assertEquals(2, back.size)
        assertFalse(back[0].restore)
        assertTrue(back[1].restore)
        assertEquals(0.04f, back[0].radius, 1e-3f)
        assertEquals(2, back[0].pointCount)
        assertEquals(0.6f, back[0].points[2], 1e-4f)
        assertEquals(0.8f, back[1].points[1], 1e-4f)
    }

    @Test
    fun decode_ignoresBrokenData() {
        assertTrue(EraseCodec.decode(null).isEmpty())
        assertTrue(EraseCodec.decode("").isEmpty())
        assertTrue(EraseCodec.decode("garbage").isEmpty())
        assertTrue(EraseCodec.decode("x30:1,2").isEmpty())
        assertTrue(EraseCodec.decode("e30:1,2,3").isEmpty()) // jumlah angka ganjil
        assertTrue(EraseCodec.decode("e30:a,b").isEmpty())
    }

    @Test
    fun decode_keepsValidPartsAroundBrokenOnes() {
        val back = EraseCodec.decode("e30:1000,2000;nonsense;r10:3000,4000")
        assertEquals(2, back.size)
        assertFalse(back[0].restore)
        assertTrue(back[1].restore)
    }

    @Test
    fun radius_isClampedToSaneRange() {
        val big = EraseStroke(false, 2f, floatArrayOf(0.5f, 0.5f))
        assertEquals("e500:5000,5000", EraseCodec.encode(listOf(big)))
        assertEquals(0.5f, EraseCodec.decode("e500:5000,5000")[0].radius, 1e-6f)
    }

    @Test
    fun strokesWithoutPointsAreSkipped() {
        assertNull(EraseCodec.encode(listOf(EraseStroke(false, 0.05f, floatArrayOf()))))
    }

    @Test
    fun onlyTheLastStrokesAreKept() {
        val strokes = (0 until EraseCodec.MAX_STROKES + 5).map {
            EraseStroke(false, 0.02f, floatArrayOf(0.5f, 0.5f))
        }
        assertEquals(EraseCodec.MAX_STROKES, EraseCodec.decode(EraseCodec.encode(strokes)).size)
    }

    @Test
    fun isEmpty_matchesDecode() {
        assertTrue(EraseCodec.isEmpty(null))
        assertTrue(EraseCodec.isEmpty("garbage"))
        assertFalse(EraseCodec.isEmpty("e30:1000,2000"))
    }
}
