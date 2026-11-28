package com.fatih.stopmotion.export

import com.fatih.stopmotion.data.FrameEntity
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipExporter {
    /** Semua foto frame sesuai urutan (versi yang sudah diedit), tanpa efek film. */
    fun export(
        frames: List<FrameEntity>,
        dest: File,
        cancelled: () -> Boolean,
        progress: (Float, String) -> Unit
    ) {
        try {
            ZipOutputStream(BufferedOutputStream(dest.outputStream(), 1 shl 16)).use { zip ->
                zip.setLevel(0) // JPEG sudah terkompresi
                frames.forEachIndexed { i, f ->
                    if (cancelled()) throw CancellationException("Dibatalkan")
                    zip.putNextEntry(ZipEntry(String.format("frame_%04d.jpg", i + 1)))
                    File(f.filePath).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    progress((i + 1f) / frames.size, "Mengemas foto")
                }
            }
        } catch (e: Throwable) {
            dest.delete()
            throw e
        }
    }
}
