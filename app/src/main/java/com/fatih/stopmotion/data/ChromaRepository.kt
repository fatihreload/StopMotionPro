package com.fatih.stopmotion.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.fatih.stopmotion.fx.ChromaBg
import com.fatih.stopmotion.fx.ImageFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Gambar latar pengganti untuk penghapus latar. Gambar disalin (dikecilkan) ke folder proyek. */
class ChromaRepository(
    private val context: Context,
    db: AppDatabase,
    private val storage: ProjectStorage
) {
    private val projectDao = db.projectDao()

    suspend fun setBackground(projectId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        if (!storage.hasEnoughSpace()) throw IOException("Penyimpanan hampir penuh")
        val tmp = File(context.cacheDir, "bg_import_${System.nanoTime()}")
        val dest = storage.newBgFile(projectId)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Gambar tidak dapat dibuka")
            input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
            val bmp = ImageFx.decode(tmp.path, 2560) ?: throw IOException("File bukan gambar yang valid")
            try {
                dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            } finally {
                bmp.recycle()
            }
            val old = projectDao.get(projectId)?.chromaBgPath
            projectDao.updateChromaBgPath(projectId, dest.absolutePath)
            val p = projectDao.get(projectId)
            if (p != null) {
                projectDao.updateChroma(projectId, p.chromaMode, p.chromaTolerance, p.chromaSoftness, ChromaBg.IMAGE)
            }
            old?.let { File(it).delete() }
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            tmp.delete()
        }
    }

    suspend fun clearBackground(projectId: Long) = withContext(Dispatchers.IO) {
        val old = projectDao.get(projectId)?.chromaBgPath
        projectDao.updateChromaBgPath(projectId, null)
        old?.let { File(it).delete() }
    }
}
