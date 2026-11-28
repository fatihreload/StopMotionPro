package com.fatih.stopmotion.audio

import com.fatih.stopmotion.data.AudioKind
import com.fatih.stopmotion.data.AudioTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackGainTest {

    private fun track(
        volume: Float = 1f,
        trimStart: Long = 0,
        trimEnd: Long = 10_000,
        fadeIn: Long = 0,
        fadeOut: Long = 0
    ) = AudioTrackEntity(
        id = 1, projectId = 1, name = "t", filePath = "x", kind = AudioKind.IMPORT,
        durationMs = trimEnd, startMs = 0, trimStartMs = trimStart, trimEndMs = trimEnd,
        volume = volume, fadeInMs = fadeIn, fadeOutMs = fadeOut, muted = false, createdAt = 0
    )

    private val eps = 1e-3f

    @Test
    fun noFade_returnsVolume() {
        assertEquals(0.5f, trackGain(track(volume = 0.5f), 3000), eps)
    }

    @Test
    fun volumeIsClampedToValidRange() {
        assertEquals(1f, trackGain(track(volume = 2f), 100), eps)
        assertEquals(0f, trackGain(track(volume = -1f), 100), eps)
    }

    @Test
    fun fadeIn_risesLinearly() {
        val t = track(fadeIn = 1000)
        assertEquals(0f, trackGain(t, 0), eps)
        assertEquals(0.5f, trackGain(t, 500), eps)
        assertEquals(1f, trackGain(t, 5000), eps)
    }

    @Test
    fun fadeOut_fallsLinearlyToTheEnd() {
        val t = track(fadeOut = 2000)
        assertEquals(1f, trackGain(t, 5000), eps)
        assertEquals(0.5f, trackGain(t, 9000), eps)
        assertEquals(0f, trackGain(t, 10_000), eps)
    }

    @Test
    fun overlappingFades_multiply() {
        val t = track(trimEnd = 1500, fadeIn = 1000, fadeOut = 1000)
        assertEquals(0.7f * 0.8f, trackGain(t, 700), eps)
    }

    @Test
    fun trimmedTrack_usesTrimmedLength() {
        val t = track(trimStart = 2000, trimEnd = 6000, fadeOut = 1000)
        assertEquals(0.5f, trackGain(t, 3500), eps)
    }
}
