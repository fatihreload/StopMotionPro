package com.fatih.stopmotion.data

import android.graphics.Bitmap
import android.util.LruCache

/** Cache hasil penghapus latar (bitmap ber-alpha) untuk pratinjau, dengan batas memori. */
class KeyedBitmapCache(maxBytes: Int = defaultMaxBytes()) {
    private val lru = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = lru.get(key)
    fun put(key: String, bitmap: Bitmap) { lru.put(key, bitmap) }
    fun clear() = lru.evictAll()

    companion object {
        private fun defaultMaxBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 10).toInt().coerceIn(12 * 1024 * 1024, 48 * 1024 * 1024)
    }
}
