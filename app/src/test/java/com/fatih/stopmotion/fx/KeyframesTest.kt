package com.fatih.stopmotion.fx

import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.data.LayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframesTest {

    private val eps = 1e-4f

    private fun key(t: Long, x: Float, ease: Int = KeyframeEase.LINEAR, y: Float = 0.5f, scale: Float = 0.5f, rot: Float = 0f, op: Float = 1f) =
        Keyframe(t, x, y, scale, rot, op, ease)

    /** Layer mulai di 1000 ms, panjang 4000 ms, pose statis (0.5, 0.5, 0.5, 0, 1). */
    private fun layer(keys: List<Keyframe> = emptyList()) = LayerEntity(
        id = 1, projectId = 1, name = "L", kind = LayerKind.IMAGE, filePath = "/x/a.png", frameDir = null,
        frameCount = 1, frameRate = 0f, srcWidth = 100, srcHeight = 100, zOrder = 0,
        startMs = 1000, durationMs = 4000, loopClip = false, createdAt = 0,
        keyframes = KeyframeCodec.encode(keys)
    )

    private fun keysOf(l: LayerEntity) = KeyframeMath.keys(l)

    // ---------------- Codec ----------------

    @Test
    fun codec_emptyIsNull() {
        assertNull(KeyframeCodec.encode(emptyList()))
    }

    @Test
    fun codec_writesCompactText() {
        val k = Keyframe(500, 0.25f, 0.75f, 0.5f, 45f, 0.8f, KeyframeEase.SMOOTH)
        assertEquals("500,2500,7500,5000,4500,800,1", KeyframeCodec.encode(listOf(k)))
    }

    @Test
    fun codec_roundTripKeepsValues() {
        val k = Keyframe(500, 0.25f, 0.75f, 0.5f, 45f, 0.8f, KeyframeEase.SMOOTH)
        val back = KeyframeCodec.decode(KeyframeCodec.encode(listOf(k)))
        assertEquals(1, back.size)
        assertEquals(500L, back[0].timeMs)
        assertEquals(0.25f, back[0].posX, eps)
        assertEquals(0.75f, back[0].posY, eps)
        assertEquals(0.5f, back[0].scale, eps)
        assertEquals(45f, back[0].rotation, 1e-3f)
        assertEquals(0.8f, back[0].opacity, eps)
        assertEquals(KeyframeEase.SMOOTH, back[0].ease)
    }

    @Test
    fun codec_decodeSortsByTime() {
        val back = KeyframeCodec.decode("900,0,0,1000,0,1000,0;100,0,0,1000,0,1000,0")
        assertEquals(listOf(100L, 900L), back.map { it.timeMs })
    }

    @Test
    fun codec_decodeSkipsBrokenParts() {
        assertTrue(KeyframeCodec.decode(null).isEmpty())
        assertTrue(KeyframeCodec.decode("").isEmpty())
        assertTrue(KeyframeCodec.decode("garbage").isEmpty())
        assertTrue(KeyframeCodec.decode("100,1,2,3,4,5").isEmpty())   // kurang satu angka
        assertTrue(KeyframeCodec.decode("x,1,2,3,4,5,0").isEmpty())
        assertEquals(1, KeyframeCodec.decode("garbage;100,5000,5000,5000,0,1000,0").size)
    }

    @Test
    fun codec_decodeClampsValues() {
        val k = KeyframeCodec.decode("100,99999,-99999,999999,0,5000,9")[0]
        assertEquals(1.5f, k.posX, eps)
        assertEquals(-0.5f, k.posY, eps)
        assertEquals(4f, k.scale, eps)
        assertEquals(1f, k.opacity, eps)
        assertEquals(KeyframeEase.HOLD, k.ease)
    }

    @Test
    fun codec_keepsOnlyTheFirstMaxKeyframes() {
        val many = (0 until KeyframeMath.MAX_KEYFRAMES + 10).map { key(it * 100L, 0.5f) }
        assertEquals(KeyframeMath.MAX_KEYFRAMES, KeyframeCodec.decode(KeyframeCodec.encode(many)).size)
    }

    // ---------------- Interpolasi ----------------

    @Test
    fun sample_withoutKeyframesUsesStaticPose() {
        val l = layer()
        val t = KeyframeMath.sample(l, 2000)
        assertEquals(0.5f, t.posX, eps)
        assertEquals(1f, t.opacity, eps)
    }

    @Test
    fun sample_linearHalfway() {
        val keys = listOf(key(0, 0f), key(2000, 1f))
        assertEquals(0.5f, KeyframeMath.sample(keys, 1000).posX, eps)
        assertEquals(0.25f, KeyframeMath.sample(keys, 500).posX, eps)
    }

    @Test
    fun sample_clampsBeforeFirstAndAfterLast() {
        val keys = listOf(key(500, 0.2f), key(2000, 0.8f))
        assertEquals(0.2f, KeyframeMath.sample(keys, 0).posX, eps)
        assertEquals(0.8f, KeyframeMath.sample(keys, 9999).posX, eps)
    }

    @Test
    fun sample_smoothStartsAndEndsSlowly() {
        val keys = listOf(key(0, 0f, KeyframeEase.SMOOTH), key(2000, 1f))
        assertEquals(0.5f, KeyframeMath.sample(keys, 1000).posX, eps)
        assertEquals(0.15625f, KeyframeMath.sample(keys, 500).posX, eps)
    }

    @Test
    fun sample_holdStaysUntilNextKeyframe() {
        val keys = listOf(key(0, 0.1f, KeyframeEase.HOLD), key(2000, 0.9f))
        assertEquals(0.1f, KeyframeMath.sample(keys, 1999).posX, eps)
        assertEquals(0.9f, KeyframeMath.sample(keys, 2000).posX, eps)
    }

    @Test
    fun sample_interpolatesEveryProperty() {
        val keys = listOf(
            Keyframe(0, 0f, 0f, 0.2f, 0f, 0f, KeyframeEase.LINEAR),
            Keyframe(1000, 1f, 1f, 0.6f, 90f, 1f, KeyframeEase.LINEAR)
        )
        val t = KeyframeMath.sample(keys, 500)
        assertEquals(0.5f, t.posX, eps)
        assertEquals(0.5f, t.posY, eps)
        assertEquals(0.4f, t.scale, eps)
        assertEquals(45f, t.rotation, 1e-3f)
        assertEquals(0.5f, t.opacity, eps)
    }

    @Test
    fun sample_layerTimeIsRelativeToLayerStart() {
        val l = layer(listOf(key(0, 0f), key(2000, 1f)))
        assertEquals(0.5f, KeyframeMath.sample(l, 2000).posX, eps) // 2000 - startMs 1000 = 1000 lokal
    }

    // ---------------- Penyuntingan ----------------

    @Test
    fun indexAt_usesSmallTolerance() {
        val keys = listOf(key(0, 0f), key(2000, 1f))
        assertEquals(1, KeyframeMath.indexAt(keys, 2030))
        assertEquals(-1, KeyframeMath.indexAt(keys, 2100))
    }

    @Test
    fun addKeyframe_afterStartAddsStartKeyframeToo() {
        val l = KeyframeMath.addKeyframe(layer(), 3000) // lokal 2000
        assertEquals(listOf(0L, 2000L), keysOf(l).map { it.timeMs })
    }

    @Test
    fun addKeyframe_atStartMakesSingleKeyframe() {
        val l = KeyframeMath.addKeyframe(layer(), 1000)
        assertEquals(listOf(0L), keysOf(l).map { it.timeMs })
    }

    @Test
    fun applyEdit_withoutKeyframesChangesStaticPoseAndClamps() {
        val l = KeyframeMath.applyEdit(layer(), 2000, LayerTransform(2f, 0.25f, 0.5f, 10f, 0.5f))
        assertNull(l.keyframes)
        assertEquals(1.5f, l.posX, eps)
        assertEquals(0.25f, l.posY, eps)
        assertEquals(10f, l.rotation, eps)
    }

    @Test
    fun applyEdit_withKeyframesUpdatesKeyframeAtThatTime() {
        val l0 = layer(listOf(key(0, 0f), key(2000, 1f)))
        val l = KeyframeMath.applyEdit(l0, 3000, LayerTransform(0.9f, 0.5f, 0.5f, 0f, 1f)) // lokal 2000
        assertEquals(2, keysOf(l).size)
        assertEquals(0.9f, keysOf(l)[1].posX, eps)
    }

    @Test
    fun applyEdit_withKeyframesCreatesNewOneAtOtherTime() {
        val l0 = layer(listOf(key(0, 0f, KeyframeEase.SMOOTH), key(2000, 1f)))
        val l = KeyframeMath.applyEdit(l0, 2000, LayerTransform(0.3f, 0.5f, 0.5f, 0f, 1f)) // lokal 1000
        assertEquals(listOf(0L, 1000L, 2000L), keysOf(l).map { it.timeMs })
        assertEquals(KeyframeEase.SMOOTH, keysOf(l)[1].ease)
    }

    @Test
    fun removeKeyframe_lastOneBecomesStaticPose() {
        val l0 = layer(listOf(Keyframe(0, 0.1f, 0.2f, 0.3f, 5f, 0.4f)))
        val l = KeyframeMath.removeKeyframeAt(l0, 1000)
        assertNull(l.keyframes)
        assertEquals(0.1f, l.posX, eps)
        assertEquals(0.4f, l.opacity, eps)
    }

    @Test
    fun removeKeyframe_otherTimeChangesNothing() {
        val l0 = layer(listOf(key(0, 0f), key(2000, 1f)))
        assertEquals(l0, KeyframeMath.removeKeyframeAt(l0, 2500))
    }

    @Test
    fun clearAnimation_freezesCurrentPose() {
        val l0 = layer(listOf(key(0, 0f), key(2000, 1f)))
        val l = KeyframeMath.clearAnimation(l0, 2000) // lokal 1000 -> x 0.5
        assertNull(l.keyframes)
        assertEquals(0.5f, l.posX, eps)
        assertFalse(KeyframeMath.hasKeyframes(l))
    }

    @Test
    fun setEase_changesOnlyTheKeyframeAtThatTime() {
        val l0 = layer(listOf(key(0, 0f), key(2000, 1f)))
        val l = KeyframeMath.setEaseAt(l0, 1000, KeyframeEase.HOLD)
        assertEquals(KeyframeEase.HOLD, keysOf(l)[0].ease)
        assertEquals(KeyframeEase.LINEAR, keysOf(l)[1].ease)
    }

    @Test
    fun signature_equalWhenPoseEqualAndDifferentWhenMoving() {
        val l = layer(listOf(key(0, 0f), key(2000, 1f)))
        assertEquals(KeyframeMath.signature(l, 4000), KeyframeMath.signature(l, 4500)) // keduanya setelah keyframe terakhir
        assertNotEquals(KeyframeMath.signature(l, 1000), KeyframeMath.signature(l, 2000))
    }
}
