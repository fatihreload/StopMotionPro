package com.fatih.stopmotion.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Efek suara bawaan yang dibuat lewat kode (tanpa file aset). */
object SoundFx {
    data class Preset(val id: String, val label: String)

    val presets = listOf(
        Preset("ding", "Ding"),
        Preset("pop", "Pop"),
        Preset("klik", "Klik"),
        Preset("whoosh", "Whoosh"),
        Preset("boing", "Boing"),
        Preset("drum", "Drum"),
        Preset("shutter", "Shutter kamera")
    )

    private const val RATE = 44100

    private fun render(id: String): ShortArray {
        val rnd = Random(7)
        val seconds = when (id) {
            "ding" -> 0.9f
            "pop" -> 0.18f
            "klik" -> 0.06f
            "whoosh" -> 0.8f
            "boing" -> 0.6f
            "shutter" -> 0.28f
            else -> 0.45f
        }
        val n = (seconds * RATE).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        var lp = 0f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val attack = (t / 0.003f).coerceAtMost(1f)
            val v: Float = when (id) {
                "ding" -> {
                    val s = sin(2 * PI * 880 * t) + 0.3 * sin(2 * PI * 2640 * t)
                    (s * exp(-5.0 * t)).toFloat()
                }
                "pop" -> {
                    val f = 600.0 * exp(-12.0 * t) + 120.0
                    phase += 2 * PI * f / RATE
                    (sin(phase) * exp(-25.0 * t)).toFloat()
                }
                "klik" -> {
                    val noise = rnd.nextFloat() * 2f - 1f
                    (noise * 0.6f + sin(2 * PI * 2000 * t).toFloat() * 0.4f) * exp(-120.0 * t).toFloat()
                }
                "whoosh" -> {
                    val noise = rnd.nextFloat() * 2f - 1f
                    val a = 0.04f + 0.45f * sin(PI * t / seconds).toFloat()
                    lp += a * (noise - lp)
                    val env = sin(PI * t / seconds).toFloat()
                    lp * env * env * 3f
                }
                "shutter" -> {
                    // dua klik mekanis: tirai membuka lalu menutup
                    fun click(tt: Float, level: Float): Float {
                        if (tt < 0f) return 0f
                        val noise = rnd.nextFloat() * 2f - 1f
                        return (noise * 0.7f + sin(2 * PI * 1800 * tt).toFloat() * 0.3f) * exp(-90.0 * tt).toFloat() * level
                    }
                    click(t, 1f) + click(t - 0.07f, 0.8f)
                }
                "boing" -> {
                    val f = 300.0 + 200.0 * exp(-4.0 * t) * sin(2 * PI * 8 * t)
                    phase += 2 * PI * f / RATE
                    (sin(phase) * exp(-3.0 * t)).toFloat()
                }
                else -> {
                    val f = 50.0 + 90.0 * exp(-18.0 * t)
                    phase += 2 * PI * f / RATE
                    val click = (rnd.nextFloat() * 2f - 1f) * exp(-200.0 * t).toFloat() * 0.3f
                    (sin(phase) * exp(-9.0 * t)).toFloat() + click
                }
            }
            val s = (v * attack * 0.8f).coerceIn(-1f, 1f)
            out[i] = (s * 32767f).toInt().toShort()
        }
        // jaga ujung sampel agar tidak berbunyi "tek"
        val fade = (RATE * 0.004f).toInt().coerceAtMost(n / 2)
        for (i in 0 until fade) {
            val k = i / fade.toFloat()
            out[n - 1 - i] = (out[n - 1 - i] * k).toInt().toShort()
        }
        return out
    }

    fun writeWav(file: File, id: String) {
        val pcm = render(id)
        val dataLen = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        buf.putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        for (s in pcm) buf.putShort(s)
        file.parentFile?.mkdirs()
        file.writeBytes(buf.array())
    }

    @Suppress("unused")
    private fun peak(a: ShortArray): Int = a.maxOfOrNull { abs(it.toInt()) } ?: 0
}
