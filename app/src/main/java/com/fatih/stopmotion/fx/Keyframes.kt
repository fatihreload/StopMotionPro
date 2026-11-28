package com.fatih.stopmotion.fx

import com.fatih.stopmotion.data.LayerEntity
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cara gerak dari sebuah keyframe menuju keyframe berikutnya. */
object KeyframeEase {
    const val LINEAR = 0
    /** Mulai dan berhenti perlahan (smoothstep). */
    const val SMOOTH = 1
    /** Tetap di nilai ini sampai keyframe berikutnya, lalu melompat. */
    const val HOLD = 2
}

/** Pose layer pada satu waktu. timeMs relatif terhadap awal layer. */
data class Keyframe(
    val timeMs: Long,
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val ease: Int = KeyframeEase.LINEAR
) {
    fun transform() = LayerTransform(posX, posY, scale, rotation, opacity)
}

/** Posisi (pusat, pecahan lebar/tinggi bingkai), ukuran (pecahan lebar bingkai), putaran, dan opasitas. */
data class LayerTransform(
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float
) {
    fun clamped() = LayerTransform(
        posX.coerceIn(-0.5f, 1.5f), posY.coerceIn(-0.5f, 1.5f),
        scale.coerceIn(0.03f, 4f), rotation, opacity.coerceIn(0f, 1f)
    )
}

/** Teks ringkas untuk database dan cadangan: "t,x,y,skala,putar,opasitas,ease;..." (bilangan bulat). */
object KeyframeCodec {
    private const val MAX_LENGTH = 20_000

    fun encode(keys: List<Keyframe>): String? {
        if (keys.isEmpty()) return null
        return keys.take(KeyframeMath.MAX_KEYFRAMES).joinToString(";") { k ->
            "${k.timeMs},${(k.posX * 10000).roundToInt()},${(k.posY * 10000).roundToInt()}," +
                "${(k.scale * 10000).roundToInt()},${(k.rotation * 100).roundToInt()}," +
                "${(k.opacity * 1000).roundToInt()},${k.ease}"
        }
    }

    /** Bagian yang rusak dilewati; hasil diurutkan menurut waktu. */
    fun decode(data: String?): List<Keyframe> {
        if (data.isNullOrBlank() || data.length > MAX_LENGTH) return emptyList()
        val out = ArrayList<Keyframe>()
        for (part in data.split(';')) {
            if (out.size >= KeyframeMath.MAX_KEYFRAMES) break
            val f = part.split(',')
            if (f.size != 7) continue
            val n = f.map { it.trim().toLongOrNull() }
            if (n.any { it == null }) continue
            out.add(
                Keyframe(
                    timeMs = n[0]!!.coerceAtLeast(0L),
                    posX = (n[1]!! / 10000f).coerceIn(-0.5f, 1.5f),
                    posY = (n[2]!! / 10000f).coerceIn(-0.5f, 1.5f),
                    scale = (n[3]!! / 10000f).coerceIn(0.03f, 4f),
                    rotation = n[4]!! / 100f,
                    opacity = (n[5]!! / 1000f).coerceIn(0f, 1f),
                    ease = n[6]!!.toInt().coerceIn(KeyframeEase.LINEAR, KeyframeEase.HOLD)
                )
            )
        }
        return out.sortedBy { it.timeMs }
    }
}

/**
 * Perhitungan animasi layer. Dipakai sama oleh pratinjau dan ekspor, dan tidak bergantung pada Android.
 * Tanpa keyframe layer memakai pose statisnya; dengan keyframe, pose diinterpolasi antar keyframe.
 */
object KeyframeMath {
    const val MAX_KEYFRAMES = 60

    /** Dua waktu yang selisihnya sekecil ini dianggap satu keyframe (sekitar setengah tick). */
    const val SAME_TIME_MS = 40L

    private class Cached(val text: String, val keys: List<Keyframe>)

    @Volatile
    private var cache: Cached? = null

    fun keys(l: LayerEntity): List<Keyframe> {
        val text = l.keyframes
        if (text.isNullOrEmpty()) return emptyList()
        cache?.let { if (it.text == text) return it.keys }
        val keys = KeyframeCodec.decode(text)
        cache = Cached(text, keys)
        return keys
    }

    fun hasKeyframes(l: LayerEntity): Boolean = keys(l).isNotEmpty()

    private fun base(l: LayerEntity) = LayerTransform(l.posX, l.posY, l.scale, l.rotation, l.opacity)

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

    /** Pose layer pada waktu film tMs. */
    fun sample(l: LayerEntity, tMs: Long): LayerTransform {
        val keys = keys(l)
        return if (keys.isEmpty()) base(l) else sample(keys, tMs - l.startMs)
    }

    fun sample(keys: List<Keyframe>, localMs: Long): LayerTransform {
        val first = keys.first()
        val last = keys.last()
        if (localMs <= first.timeMs) return first.transform()
        if (localMs >= last.timeMs) return last.transform()
        var i = 0
        while (i < keys.size - 1 && keys[i + 1].timeMs <= localMs) i++
        val a = keys[i]
        val b = keys[i + 1]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val raw = ((localMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val f = when (a.ease) {
            KeyframeEase.HOLD -> 0f
            KeyframeEase.SMOOTH -> raw * raw * (3f - 2f * raw)
            else -> raw
        }
        return LayerTransform(
            lerp(a.posX, b.posX, f), lerp(a.posY, b.posY, f), lerp(a.scale, b.scale, f),
            lerp(a.rotation, b.rotation, f), lerp(a.opacity, b.opacity, f)
        )
    }

    /** Waktu lokal (ms sejak layer mulai) yang dibatasi ke rentang layer. */
    private fun localTime(l: LayerEntity, tMs: Long): Long = (tMs - l.startMs).coerceIn(0L, l.durationMs.coerceAtLeast(0L))

    /** Indeks keyframe yang waktunya sama dengan localMs (dalam toleransi), atau -1. */
    fun indexAt(keys: List<Keyframe>, localMs: Long): Int {
        var best = -1
        var bestDiff = Long.MAX_VALUE
        keys.forEachIndexed { i, k ->
            val d = abs(k.timeMs - localMs)
            if (d <= SAME_TIME_MS && d < bestDiff) { best = i; bestDiff = d }
        }
        return best
    }

    private fun upsert(keys: List<Keyframe>, local: Long, t: LayerTransform): List<Keyframe> {
        val c = t.clamped()
        val idx = indexAt(keys, local)
        val out = keys.toMutableList()
        if (idx >= 0) {
            out[idx] = out[idx].copy(posX = c.posX, posY = c.posY, scale = c.scale, rotation = c.rotation, opacity = c.opacity)
            return out
        }
        if (out.size >= MAX_KEYFRAMES) return out
        val inheritedEase = out.lastOrNull { it.timeMs < local }?.ease ?: KeyframeEase.LINEAR
        out.add(Keyframe(local, c.posX, c.posY, c.scale, c.rotation, c.opacity, inheritedEase))
        return out.sortedBy { it.timeMs }
    }

    /**
     * Menerapkan perubahan pose pada waktu film tMs. Tanpa keyframe, pose statis yang berubah. Dengan keyframe,
     * keyframe pada waktu itu diperbarui, atau dibuat bila belum ada.
     */
    fun applyEdit(l: LayerEntity, tMs: Long, t: LayerTransform): LayerEntity {
        val c = t.clamped()
        val keys = keys(l)
        if (keys.isEmpty()) {
            return l.copy(posX = c.posX, posY = c.posY, scale = c.scale, rotation = c.rotation, opacity = c.opacity)
        }
        return l.copy(keyframes = KeyframeCodec.encode(upsert(keys, localTime(l, tMs), c)))
    }

    /**
     * Menambah keyframe dari pose yang tampil pada waktu tMs. Keyframe pertama yang dibuat setelah awal layer
     * ditemani keyframe di awal (pose statis), sehingga gerak dimulai dari pose semula.
     */
    fun addKeyframe(l: LayerEntity, tMs: Long): LayerEntity {
        val local = localTime(l, tMs)
        var keys = keys(l)
        val pose = if (keys.isEmpty()) base(l) else sample(keys, local)
        if (keys.isEmpty() && local > SAME_TIME_MS) {
            keys = listOf(Keyframe(0L, l.posX, l.posY, l.scale, l.rotation, l.opacity, KeyframeEase.LINEAR))
        }
        return l.copy(keyframes = KeyframeCodec.encode(upsert(keys, local, pose)))
    }

    /** Menghapus keyframe pada waktu tMs. Bila itu keyframe terakhir, posenya menjadi pose statis. */
    fun removeKeyframeAt(l: LayerEntity, tMs: Long): LayerEntity {
        val keys = keys(l)
        val idx = indexAt(keys, localTime(l, tMs))
        if (idx < 0) return l
        val out = keys.toMutableList()
        val removed = out.removeAt(idx)
        return if (out.isEmpty()) {
            l.copy(
                keyframes = null, posX = removed.posX, posY = removed.posY, scale = removed.scale,
                rotation = removed.rotation, opacity = removed.opacity
            )
        } else l.copy(keyframes = KeyframeCodec.encode(out))
    }

    /** Menghapus seluruh animasi; layer tetap di pose yang tampil pada waktu tMs. */
    fun clearAnimation(l: LayerEntity, tMs: Long): LayerEntity {
        if (!hasKeyframes(l)) return l
        val s = sample(l, tMs)
        return l.copy(keyframes = null, posX = s.posX, posY = s.posY, scale = s.scale, rotation = s.rotation, opacity = s.opacity)
    }

    fun setEaseAt(l: LayerEntity, tMs: Long, ease: Int): LayerEntity {
        val keys = keys(l)
        val idx = indexAt(keys, localTime(l, tMs))
        if (idx < 0) return l
        val out = keys.toMutableList()
        out[idx] = out[idx].copy(ease = ease.coerceIn(KeyframeEase.LINEAR, KeyframeEase.HOLD))
        return l.copy(keyframes = KeyframeCodec.encode(out))
    }

    /** Tanda pose pada waktu tMs; dua tick dengan tanda sama menghasilkan gambar layer yang identik. */
    fun signature(l: LayerEntity, tMs: Long): Int {
        val s = sample(l, tMs)
        return listOf(
            (s.posX * 1000).roundToInt(), (s.posY * 1000).roundToInt(), (s.scale * 1000).roundToInt(),
            (s.rotation * 10).roundToInt(), (s.opacity * 255).roundToInt()
        ).hashCode()
    }
}
