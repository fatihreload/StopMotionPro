package com.fatih.stopmotion.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache bitmap ukuran sedang (sisi terpanjang sekitar 1000 px, RGB_565) khusus playback.
 * Batas memori dijaga agar foto resolusi penuh tidak pernah dimuat sekaligus.
 */
class FrameImageCache {
    private val maxBytes = minOf(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5).toInt()

    private val lru = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(path: String): Bitmap? = lru.get(path)

    /** Dipanggil saat sistem kekurangan memori. */
    fun clear() = lru.evictAll()

    suspend fun load(path: String): Bitmap? = withContext(Dispatchers.IO) {
        lru.get(path) ?: decode(path)?.also { lru.put(path, it) }
    }

    private fun decode(path: String, targetLongSide: Int = 960): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetLongSide) sample *= 2
            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            ) ?: return null
            val rotation = when (
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation == 0f) decoded else {
                Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height,
                    Matrix().apply { postRotate(rotation) }, true
                ).also { if (it !== decoded) decoded.recycle() }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            lru.evictAll()
            null
        }
    }
}
