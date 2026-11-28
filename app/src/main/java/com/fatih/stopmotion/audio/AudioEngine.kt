package com.fatih.stopmotion.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.fatih.stopmotion.data.AudioTrackEntity
import kotlin.math.abs

/** Volume akhir sebuah track pada posisi lokal tertentu (volume dasar dikali fade in/out). */
fun trackGain(t: AudioTrackEntity, localMs: Long): Float {
    val len = (t.trimEndMs - t.trimStartMs).coerceAtLeast(1)
    var g = t.volume.coerceIn(0f, 1f)
    if (t.fadeInMs > 0 && localMs < t.fadeInMs) g *= (localMs.coerceAtLeast(0) / t.fadeInMs.toFloat())
    val remaining = len - localMs
    if (t.fadeOutMs > 0 && remaining < t.fadeOutMs) g *= (remaining.coerceAtLeast(0) / t.fadeOutMs.toFloat())
    return g.coerceIn(0f, 1f)
}

/**
 * Memutar semua track audio mengikuti waktu timeline film. Setiap track memakai MediaPlayer sendiri.
 * Posisi film (ms) diberikan dari luar lewat update(); engine hanya menyelaraskan player dengan posisi itu.
 */
class AudioEngine {
    private val lock = Any()
    private val players = HashMap<Long, MediaPlayer>()
    private var tracks: List<AudioTrackEntity> = emptyList()
    private var appliedSpeed = 1f

    /** Menyiapkan player (blocking). Panggil dari thread IO. */
    fun setTracks(list: List<AudioTrackEntity>) {
        val existing = synchronized(lock) { players.keys.toSet() }
        val created = HashMap<Long, MediaPlayer>()
        for (t in list) {
            if (t.id in existing) continue
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(t.filePath)
                mp.prepare()
                created[t.id] = mp
            } catch (e: Exception) {
                // file rusak: track dilewati
            }
        }
        val dropped = ArrayList<MediaPlayer>()
        synchronized(lock) {
            tracks = list
            val ids = list.map { it.id }.toSet()
            players.keys.filter { it !in ids }.forEach { id -> players.remove(id)?.let(dropped::add) }
            players.putAll(created)
        }
        dropped.forEach { runCatching { it.release() } }
    }

    fun update(timeMs: Long, playing: Boolean, speed: Float) {
        synchronized(lock) {
            for (t in tracks) {
                val mp = players[t.id] ?: continue
                val len = t.trimEndMs - t.trimStartMs
                val local = timeMs - t.startMs
                val active = playing && !t.muted && len > 0 && local >= 0 && local < len
                try {
                    if (!active) {
                        if (mp.isPlaying) mp.pause()
                        continue
                    }
                    val expected = (t.trimStartMs + local).toInt()
                    val g = trackGain(t, local)
                    mp.setVolume(g, g)
                    if (!mp.isPlaying) {
                        mp.seekTo(expected.toLong(), MediaPlayer.SEEK_CLOSEST)
                        mp.start()
                        setSpeed(mp, speed)
                    } else {
                        if (abs(speed - appliedSpeed) > 0.01f) setSpeed(mp, speed)
                        if (abs(mp.currentPosition - expected) > 400) {
                            mp.seekTo(expected.toLong(), MediaPlayer.SEEK_CLOSEST)
                        }
                    }
                } catch (e: IllegalStateException) {
                    // player sudah dilepas
                }
            }
            appliedSpeed = speed
        }
    }

    private fun setSpeed(mp: MediaPlayer, speed: Float) {
        try {
            mp.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.25f, 2f)).setPitch(1f)
        } catch (e: Exception) {
            // perangkat tidak mendukung perubahan kecepatan
        }
    }

    fun pauseAll() = update(0L, false, 1f)

    fun release() {
        val all = synchronized(lock) {
            val copy = players.values.toList()
            players.clear()
            tracks = emptyList()
            copy
        }
        all.forEach { runCatching { it.release() } }
    }
}
