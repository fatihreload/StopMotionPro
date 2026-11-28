package com.fatih.stopmotion.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.fatih.stopmotion.fx.ImageFx
import com.fatih.stopmotion.fx.LayerTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

class LayerRepository(
    private val context: Context,
    db: AppDatabase,
    private val storage: ProjectStorage
) {
    private val dao = db.layerDao()

    data class AddedVideo(val layer: LayerEntity, val hasAudio: Boolean)

    fun observe(projectId: Long): Flow<List<LayerEntity>> = dao.observe(projectId)

    suspend fun updateProps(l: LayerEntity) = withContext(Dispatchers.IO) {
        dao.updateProps(
            l.id, l.name, l.visible, l.startMs.coerceAtLeast(0), l.durationMs.coerceAtLeast(100),
            l.loopClip, l.posX.coerceIn(-0.5f, 1.5f), l.posY.coerceIn(-0.5f, 1.5f),
            l.scale.coerceIn(0.03f, 4f), l.rotation, l.opacity.coerceIn(0f, 1f),
            l.chromaMode.coerceIn(0, 2), l.chromaTolerance.coerceIn(0f, 1f), l.chromaSoftness.coerceIn(0f, 1f),
            com.fatih.stopmotion.fx.KeyframeCodec.encode(com.fatih.stopmotion.fx.KeyframeCodec.decode(l.keyframes))
        )
    }

    suspend fun delete(l: LayerEntity) = withContext(Dispatchers.IO) {
        dao.delete(l.id)
        File(l.filePath).parentFile?.deleteRecursively()
    }

    /** up = naik ke depan (tampil di atas layer lain). Urutan ditulis ulang berurutan agar tidak ada nilai kembar. */
    suspend fun move(projectId: Long, id: Long, up: Boolean) = withContext(Dispatchers.IO) {
        val list = dao.getAll(projectId)
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i + 1 else i - 1
        if (i < 0 || j !in list.indices) return@withContext
        val reordered = list.toMutableList()
        val t = reordered[i]; reordered[i] = reordered[j]; reordered[j] = t
        reordered.forEachIndexed { z, l -> if (l.zOrder != z) dao.updateZ(l.id, z) }
    }

    private suspend fun checkCanAdd(projectId: Long) {
        if (dao.getAll(projectId).size >= LayerTiming.MAX_LAYERS) {
            throw IOException("Maksimal ${LayerTiming.MAX_LAYERS} layer per proyek")
        }
        if (!storage.hasEnoughSpace(100L * 1024 * 1024)) throw IOException("Penyimpanan hampir penuh")
    }

    /** Menambah layer gambar (PNG transparan didukung). Gambar diperkecil maksimal 2048 px. */
    suspend fun addImage(projectId: Long, uri: Uri, startMs: Long, durationMs: Long): LayerEntity =
        withContext(Dispatchers.IO) {
            checkCanAdd(projectId)
            val dir = storage.newLayerDir(projectId)
            try {
                val tmp = File(dir, "src.tmp")
                copyUri(uri, tmp)
                val bmp = ImageFx.decode(tmp.path, 2048) ?: throw IOException("Gambar tidak dapat dibaca")
                val alpha = bmp.hasAlpha()
                val out = File(dir, if (alpha) "src.png" else "src.jpg")
                out.outputStream().use {
                    bmp.compress(if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
                }
                val w = bmp.width
                val h = bmp.height
                bmp.recycle()
                tmp.delete()
                val name = baseName(uri, "Gambar")
                val l = LayerEntity(
                    projectId = projectId, name = name, kind = LayerKind.IMAGE, filePath = out.absolutePath,
                    frameDir = null, frameCount = 1, frameRate = 0f, srcWidth = w, srcHeight = h,
                    zOrder = dao.maxZ(projectId) + 1, startMs = startMs.coerceAtLeast(0),
                    durationMs = durationMs.coerceAtLeast(500), loopClip = false,
                    scale = 0.5f, createdAt = System.currentTimeMillis()
                )
                l.copy(id = dao.insert(l))
            } catch (e: Throwable) {
                dir.deleteRecursively()
                throw e
            }
        }

    /**
     * Menambah layer klip video. Klip diekstrak menjadi JPEG pada fps proyek (maksimal 30 fps dan 240 frame),
     * sehingga klip panjang dipotong. chromaMode != 0 langsung menghapus latar hijau/biru klip.
     */
    suspend fun addVideo(
        projectId: Long,
        uri: Uri,
        startMs: Long,
        fps: Int,
        chromaMode: Int,
        onProgress: (Float) -> Unit
    ): AddedVideo = withContext(Dispatchers.IO) {
        checkCanAdd(projectId)
        val dir = storage.newLayerDir(projectId)
        try {
            val display = displayName(uri) ?: "Klip"
            val ext = display.substringAfterLast('.', "").lowercase(Locale.US)
                .takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } } ?: "mp4"
            val src = File(dir, "src.$ext")
            copyUri(uri, src)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(src.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                if (durationMs <= 0) throw IOException("Klip video tidak dapat dibaca")
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                val rate = fps.coerceIn(1, 30)
                val count = (durationMs * rate / 1000).toInt().coerceIn(1, MAX_VIDEO_FRAMES)
                val frameDir = File(dir, "f").apply { mkdirs() }

                var w = 0
                var h = 0
                var last: File? = null
                for (i in 0 until count) {
                    currentCoroutineContext().ensureActive()
                    val f = File(frameDir, String.format(Locale.US, "%05d.jpg", i + 1))
                    val raw = retriever.getFrameAtTime(i * 1_000_000L / rate, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (raw == null) {
                        // Frame di ujung klip kadang tidak terbaca: pakai frame sebelumnya.
                        (last ?: throw IOException("Frame video tidak dapat dibaca")).copyTo(f, overwrite = true)
                    } else {
                        val bmp = shrink(raw, FRAME_MAX_SIDE)
                        if (w == 0) { w = bmp.width; h = bmp.height }
                        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        bmp.recycle()
                    }
                    last = f
                    onProgress((i + 1f) / count)
                }
                val shownMs = count * 1000L / rate
                val l = LayerEntity(
                    projectId = projectId, name = baseName(uri, "Klip"), kind = LayerKind.VIDEO,
                    filePath = src.absolutePath, frameDir = frameDir.absolutePath, frameCount = count,
                    frameRate = rate.toFloat(), srcWidth = w.coerceAtLeast(1), srcHeight = h.coerceAtLeast(1),
                    zOrder = dao.maxZ(projectId) + 1, startMs = startMs.coerceAtLeast(0), durationMs = shownMs,
                    loopClip = false, scale = 1f, chromaMode = chromaMode.coerceIn(0, 2),
                    createdAt = System.currentTimeMillis()
                )
                AddedVideo(l.copy(id = dao.insert(l)), hasAudio)
            } finally {
                runCatching { retriever.release() }
            }
        } catch (e: Throwable) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun shrink(src: Bitmap, maxSide: Int): Bitmap {
        val longSide = maxOf(src.width, src.height)
        if (longSide <= maxSide) return src
        val s = maxSide / longSide.toFloat()
        val out = Bitmap.createScaledBitmap(
            src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true
        )
        if (out !== src) src.recycle()
        return out
    }

    private fun copyUri(uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("File tidak dapat dibuka")
        input.use { src -> dest.outputStream().use { src.copyTo(it) } }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun baseName(uri: Uri, fallback: String): String =
        (displayName(uri)?.substringBeforeLast('.') ?: fallback).let { ProjectNames.clean(it) }.ifEmpty { fallback }

    companion object {
        const val MAX_VIDEO_FRAMES = 240
        private const val FRAME_MAX_SIDE = 960
    }
}
