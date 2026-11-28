package com.fatih.stopmotion.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fatih.stopmotion.data.KeyedBitmapCache
import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.fx.ChromaKey
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.ImageFx
import com.fatih.stopmotion.fx.LayerFiles
import com.fatih.stopmotion.fx.KeyframeMath
import com.fatih.stopmotion.fx.LayerGeometry
import com.fatih.stopmotion.fx.LayerTransform
import com.fatih.stopmotion.fx.LayerTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Sisi terpanjang gambar layer untuk pratinjau (ekspor memakai ukuran penuh). */
private const val PREVIEW_MAX_SIDE = 720

/** Bitmap satu frame layer (latar sudah dihapus bila layer memakai chroma key). Frame lama tetap tampil sampai yang baru siap. */
@Composable
private fun rememberLayerImage(l: LayerEntity, index: Int?, cache: KeyedBitmapCache): ImageBitmap? {
    val path = index?.let { LayerFiles.framePath(l, it) }
    val key = path?.let { layerCacheKey(l, it) }
    val state = produceState<ImageBitmap?>(initialValue = key?.let { cache.get(it) }?.asImageBitmap(), key) {
        if (key == null || path == null) {
            value = null
            return@produceState
        }
        val hit = cache.get(key)
        if (hit != null) {
            value = hit.asImageBitmap()
            return@produceState
        }
        val bmp = withContext(Dispatchers.Default) { decodeLayerBitmap(l, path) }
        if (bmp != null) {
            cache.put(key, bmp)
            value = bmp.asImageBitmap()
        }
    }
    return state.value
}

internal fun layerCacheKey(l: LayerEntity, path: String): String =
    "L|$path|${l.chromaMode}|${(l.chromaTolerance * 100).roundToInt()}|${(l.chromaSoftness * 100).roundToInt()}"

/** Memuat satu frame layer untuk pratinjau (dengan chroma key bila aktif). Panggil dari thread latar. */
internal fun decodeLayerBitmap(l: LayerEntity, path: String): android.graphics.Bitmap? {
    val raw = ImageFx.decode(path, PREVIEW_MAX_SIDE) ?: return null
    return if (l.chromaMode != 0) {
        ChromaKey.process(raw, ChromaState(l.chromaMode, l.chromaTolerance, l.chromaSoftness), emptyList())
            .also { if (it !== raw) raw.recycle() }
    } else raw
}

private class LayerDraw(val layer: LayerEntity, val image: ImageBitmap?)

/**
 * Menggambar semua layer yang aktif pada waktu timeMs di atas pratinjau foto, berurutan dari bawah ke atas.
 * Rumus waktu dan geometrinya sama dengan ekspor (LayerTiming dan LayerGeometry), jadi tampilannya sama dengan hasil.
 */
@Composable
fun LayerPreview(
    layers: List<LayerEntity>,
    timeMs: Long,
    selectedId: Long?,
    cache: KeyedBitmapCache,
    modifier: Modifier = Modifier
) {
    val sorted = remember(layers) { layers.filter { it.visible && it.durationMs > 0 }.sortedBy { it.zOrder } }
    val items = sorted.map { l ->
        key(l.id) { LayerDraw(l, rememberLayerImage(l, LayerTiming.frameIndex(l, timeMs), cache)) }
    }
    // Layer yang sedang dipilih tetap diberi bingkai walau di luar rentang waktunya, supaya jelas di mana ia berada.
    val selected = layers.firstOrNull { it.id == selectedId }
    Canvas(modifier) {
        for (item in items) {
            val img = item.image ?: continue
            val l = item.layer
            val tf = KeyframeMath.sample(l, timeMs)
            val r = LayerGeometry.rect(l, tf, size.width, size.height)
            withTransform({
                translate(r.cx, r.cy)
                rotate(r.rotation, Offset.Zero)
            }) {
                drawImage(
                    img,
                    dstOffset = IntOffset((-r.w / 2f).roundToInt(), (-r.h / 2f).roundToInt()),
                    dstSize = IntSize(r.w.roundToInt().coerceAtLeast(1), r.h.roundToInt().coerceAtLeast(1)),
                    alpha = tf.opacity.coerceIn(0f, 1f)
                )
            }
        }
        if (selected != null) {
            val r = LayerGeometry.rect(selected, KeyframeMath.sample(selected, timeMs), size.width, size.height)
            withTransform({
                translate(r.cx, r.cy)
                rotate(r.rotation, Offset.Zero)
            }) {
                drawRect(
                    Color(0xFF8E5CFF),
                    topLeft = Offset(-r.w / 2f, -r.h / 2f),
                    size = Size(r.w, r.h),
                    style = Stroke(width = 2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                )
            }
        }
    }
}

/**
 * Mengatur layer terpilih langsung di pratinjau: geser untuk memindah, cubit untuk mengubah ukuran, putar dengan dua jari.
 * onChange dipanggil terus selama gesture (tampilan langsung), onCommit sekali saat jari diangkat (disimpan).
 */
@Composable
fun LayerGestureOverlay(
    layer: LayerEntity,
    /** Waktu film saat ini; dipakai agar perubahan pada layer beranimasi menjadi keyframe di waktu ini. */
    timeMs: Long,
    onChange: (LayerEntity) -> Unit,
    onDragging: (Boolean) -> Unit,
    onCommit: (LayerEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val latest by rememberUpdatedState(layer)
    val atTime by rememberUpdatedState(timeMs)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val dragging by rememberUpdatedState(onDragging)
    Box(
        modifier.pointerInput(layer.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val sw = size.width.toFloat().coerceAtLeast(1f)
                val sh = size.height.toFloat().coerceAtLeast(1f)
                val start = latest
                val t0 = atTime
                var pose = KeyframeMath.sample(start, t0)
                var cur = start
                var moved = false
                dragging(true)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.positionChanged() }) {
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val rot = event.calculateRotation()
                        pose = LayerTransform(
                            posX = pose.posX + pan.x / sw,
                            posY = pose.posY + pan.y / sh,
                            scale = pose.scale * zoom,
                            rotation = pose.rotation + rot,
                            opacity = pose.opacity
                        ).clamped()
                        // Tanpa keyframe: pose statis berubah. Dengan keyframe: keyframe di waktu ini dibuat/diperbarui.
                        cur = KeyframeMath.applyEdit(start, t0, pose)
                        moved = true
                        change(cur)
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (moved) commit(cur)
                dragging(false)
            }
        }
    )
}
