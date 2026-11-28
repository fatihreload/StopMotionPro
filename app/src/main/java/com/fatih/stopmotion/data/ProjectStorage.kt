package com.fatih.stopmotion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Struktur file (penyimpanan internal aplikasi), lokasi: filesDir/projects/ID_PROYEK/
 * folder frames di dalam folder proyek -> foto resolusi penuh
 * folder thumbs di dalam folder proyek -> thumbnail kecil untuk timeline/daftar
 */
class ProjectStorage(private val context: Context) {

    private val root: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(id: Long): File = File(root, id.toString()).apply { mkdirs() }
    fun framesDir(id: Long): File = File(projectDir(id), "frames").apply { mkdirs() }
    fun thumbsDir(id: Long): File = File(projectDir(id), "thumbs").apply { mkdirs() }

    fun audioDir(id: Long): File = File(projectDir(id), "audio").apply { mkdirs() }
    fun bgDir(id: Long): File = File(projectDir(id), "bg").apply { mkdirs() }
    fun layersDir(id: Long): File = File(projectDir(id), "layers").apply { mkdirs() }

    /** Folder baru untuk satu layer: berisi src.* (sumber) dan, untuk video, f/00001.jpg dst. */
    fun newLayerDir(projectId: Long): File {
        var d: File
        do {
            d = File(layersDir(projectId), "L${System.currentTimeMillis()}_${(0..99999).random()}")
        } while (d.exists())
        d.mkdirs()
        return d
    }

    fun exportsDir(id: Long): File = File(projectDir(id), "exports").apply { mkdirs() }

    fun newFrameFile(projectId: Long): File {
        var f: File
        do {
            f = File(framesDir(projectId), "frame_${System.currentTimeMillis()}_${(0..99999).random()}.jpg")
        } while (f.exists())
        return f
    }

    fun newBgFile(projectId: Long, ext: String = "jpg"): File {
        var f: File
        do {
            f = File(bgDir(projectId), "bg_${System.currentTimeMillis()}_${(0..99999).random()}.$ext")
        } while (f.exists())
        return f
    }

    fun newAudioFile(projectId: Long, ext: String): File {
        var f: File
        do {
            f = File(audioDir(projectId), "audio_${System.currentTimeMillis()}_${(0..99999).random()}.$ext")
        } while (f.exists())
        return f
    }

    fun fileSize(f: File): Long {
        if (f.isFile) return f.length()
        var total = 0L
        f.listFiles()?.forEach { total += fileSize(it) }
        return total
    }

    fun projectBytes(id: Long): Long = fileSize(File(root, id.toString()))
    fun totalBytes(): Long = fileSize(root)
    fun usableBytes(): Long = context.filesDir.usableSpace

    /** Menghapus semua hasil ekspor di semua proyek. Mengembalikan jumlah byte yang dibebaskan. */
    fun clearExports(): Long {
        var freed = 0L
        root.listFiles()?.forEach { p ->
            val e = File(p, "exports")
            if (e.exists()) {
                freed += fileSize(e)
                e.deleteRecursively()
            }
        }
        return freed
    }

    fun hasEnoughSpace(minBytes: Long = 50L * 1024 * 1024): Boolean =
        context.filesDir.usableSpace > minBytes

    fun deleteProject(id: Long) {
        File(root, id.toString()).deleteRecursively()
    }

    /**
     * Menghapus file foto/thumbnail yang tidak lagi dipakai frame mana pun.
     * File tidak langsung dihapus saat frame dihapus agar undo bisa mengembalikannya.
     */
    fun cleanOrphans(projectId: Long, keepPaths: Set<String>) {
        listOf(framesDir(projectId), thumbsDir(projectId)).forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath !in keepPaths) f.delete()
            }
        }
    }

    /** Membuat thumbnail ter-downsample (tidak memuat gambar penuh ke RAM). */
    fun createThumbnail(src: File, dest: File, maxSize: Int = 320): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            src.path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return false

        val rotation = when (
            ExifInterface(src.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val result = if (rotation != 0f) {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation) }, true
            ).also { if (it !== decoded) decoded.recycle() }
        } else decoded

        dest.parentFile?.mkdirs()
        dest.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        result.recycle()
        return true
    }
}
