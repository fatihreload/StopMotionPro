package com.fatih.stopmotion.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cache bitmap berukuran sedang (sisi pendek sekitar 720px) untuk playback.
 * Ukuran cache dibatasi, jadi foto resolusi penuh tidak pernah dimuat sekaligus ke RAM.
 */
class FrameBitmapCache(maxBytes: Int = defaultMaxBytes()) {

    private val lru = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(path: String): Bitmap? = lru.get(path)

    /** Dipanggil saat sistem kekurangan memori. */
    fun clear() = lru.evictAll()

    suspend fun load(path: String): Bitmap? {
        lru.get(path)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { decodeSampled(path, 720) } ?: return null
        lru.put(path, bmp)
        return bmp
    }

    companion object {
        private fun defaultMaxBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).toInt().coerceIn(16 * 1024 * 1024, 96 * 1024 * 1024)

        fun decodeSampled(path: String, targetShortSide: Int): Bitmap? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val shortSide = minOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (shortSide / (sample * 2) >= targetShortSide) sample *= 2
                val decoded = BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply { inSampleSize = sample }
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
                null
            }
        }
    }
}
