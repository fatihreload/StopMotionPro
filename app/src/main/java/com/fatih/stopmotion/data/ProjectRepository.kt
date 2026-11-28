package com.fatih.stopmotion.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class HistoryInfo(val canUndo: Boolean = false, val canRedo: Boolean = false)

/**
 * Riwayat undo/redo satu proyek. Menyimpan snapshot daftar frame setelah setiap operasi.
 * Berada di memori selama aplikasi berjalan; file foto tidak dihapus selama proyek terbuka.
 */
internal class History {
    private val states = ArrayList<List<FrameEntity>>()
    private var index = -1
    val info = MutableStateFlow(HistoryInfo())

    @Synchronized
    fun isEmpty() = states.isEmpty()

    @Synchronized
    fun push(before: List<FrameEntity>?, after: List<FrameEntity>) {
        if (states.isEmpty()) {
            if (before != null) states.add(before)
            states.add(after)
            index = states.size - 1
        } else {
            while (states.size > index + 1) states.removeAt(states.lastIndex)
            states.add(after)
            index++
            while (states.size > MAX_STATES) {
                states.removeAt(0)
                index--
            }
        }
        publish()
    }

    @Synchronized
    fun stepBack(): List<FrameEntity>? {
        if (index <= 0) return null
        index--
        publish()
        return states[index]
    }

    @Synchronized
    fun stepForward(): List<FrameEntity>? {
        if (index >= states.size - 1) return null
        index++
        publish()
        return states[index]
    }

    private fun publish() {
        info.value = HistoryInfo(canUndo = index > 0, canRedo = index < states.size - 1)
    }

    companion object {
        const val MAX_STATES = 30
    }
}

class ProjectRepository(
    private val db: AppDatabase,
    private val storage: ProjectStorage
) {
    private val projectDao = db.projectDao()
    private val frameDao = db.frameDao()
    private val histories = ConcurrentHashMap<Long, History>()

    val projects: Flow<List<ProjectSummary>> = projectDao.observeSummaries()

    fun observeProject(id: Long): Flow<ProjectEntity?> = projectDao.observe(id)
    fun observeFrames(projectId: Long): Flow<List<FrameEntity>> = frameDao.observeFrames(projectId)

    private fun historyOf(projectId: Long): History =
        histories.getOrPut(projectId) { History() }

    fun observeHistory(projectId: Long): StateFlow<HistoryInfo> = historyOf(projectId).info

    // ---------------- Proyek ----------------

    suspend fun createProject(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val finalName = name.trim().ifBlank {
            "Proyek " + SimpleDateFormat("dd MMM HH:mm", Locale("id", "ID")).format(Date(now))
        }
        val id = projectDao.insert(ProjectEntity(name = finalName, createdAt = now, updatedAt = now))
        storage.projectDir(id)
        id
    }

    /** Dipanggil saat proyek dibuka: riwayat undo baru dan membersihkan file yatim. */
    suspend fun prepareProject(id: Long) = withContext(Dispatchers.IO) {
        histories.remove(id)
        val keep = frameDao.getFrames(id).flatMap { listOf(it.filePath, it.thumbPath) }.toSet()
        storage.cleanOrphans(id, keep)
    }

    suspend fun deleteProject(id: Long) = withContext(Dispatchers.IO) {
        histories.remove(id)
        projectDao.delete(id) // frame ikut terhapus (CASCADE)
        storage.deleteProject(id)
    }

    suspend fun renameProject(id: Long, name: String) = withContext(Dispatchers.IO) {
        val clean = ProjectNames.clean(name)
        if (clean.isNotEmpty()) projectDao.rename(id, clean)
    }

    suspend fun updateOnion(id: Long, enabled: Boolean, opacity: Float, count: Int, color: Int) =
        withContext(Dispatchers.IO) {
            projectDao.updateOnion(id, enabled, opacity.coerceIn(0f, 1f), count.coerceIn(1, 5), color)
        }

    suspend fun updateFps(id: Long, fps: Int) = withContext(Dispatchers.IO) {
        projectDao.updateFps(id, fps.coerceIn(1, 60))
    }

    suspend fun updateEffects(
        id: Long, filter: Int, intensity: Float, vignette: Float,
        fadeIn: Int, fadeOut: Int, white: Boolean
    ) = withContext(Dispatchers.IO) {
        projectDao.updateEffects(
            id, filter.coerceIn(0, 7), intensity.coerceIn(0f, 1f), vignette.coerceIn(0f, 1f),
            fadeIn.coerceIn(0, 120), fadeOut.coerceIn(0, 120), white
        )
    }

    suspend fun updateChroma(id: Long, mode: Int, tolerance: Float, softness: Float, bgMode: Int) =
        withContext(Dispatchers.IO) {
            projectDao.updateChroma(id, mode.coerceIn(0, 2), tolerance.coerceIn(0f, 1f), softness.coerceIn(0f, 1f), bgMode.coerceIn(0, 4))
        }

    /** Baris yang ditandai terhapus (sisa versi lama) dibuang saat aplikasi dibuka. */
    suspend fun purgeAllDeleted() = withContext(Dispatchers.IO) {
        frameDao.getAllDeleted().map { it.id }.chunked(500).forEach { frameDao.deleteRows(it) }
    }

    suspend fun getFrame(id: Long): FrameEntity? = withContext(Dispatchers.IO) { frameDao.getById(id) }

    suspend fun getFrames(projectId: Long): List<FrameEntity> =
        withContext(Dispatchers.IO) { frameDao.getFrames(projectId) }

    // ---------------- Undo / redo ----------------

    suspend fun undo(projectId: Long): Boolean = withContext(Dispatchers.IO) {
        val snapshot = historyOf(projectId).stepBack() ?: return@withContext false
        restore(projectId, snapshot)
        true
    }

    suspend fun redo(projectId: Long): Boolean = withContext(Dispatchers.IO) {
        val snapshot = historyOf(projectId).stepForward() ?: return@withContext false
        restore(projectId, snapshot)
        true
    }

    private suspend fun restore(projectId: Long, snapshot: List<FrameEntity>) {
        db.withTransaction {
            frameDao.getFrames(projectId).map { it.id }.chunked(500).forEach { frameDao.deleteByIds(it) }
            frameDao.insertAll(snapshot)
            refreshProjectThumb(projectId)
        }
    }

    /** Menjalankan operasi frame dalam satu transaksi lalu mencatat hasilnya ke riwayat undo. */
    private suspend fun <T> recorded(projectId: Long, block: suspend () -> T): T {
        val history = historyOf(projectId)
        var before: List<FrameEntity>? = null
        var after: List<FrameEntity> = emptyList()
        val result = db.withTransaction {
            if (history.isEmpty()) before = frameDao.getFrames(projectId)
            val r = block()
            after = frameDao.getFrames(projectId)
            r
        }
        history.push(before, after)
        return result
    }

    // ---------------- Frame ----------------

    /**
     * Foto sudah ada di disk. Membuat thumbnail lalu mencatat frame di posisi tertentu
     * (null = paling akhir). Frame setelahnya otomatis bergeser.
     */
    suspend fun insertFrame(projectId: Long, photo: File, atPosition: Int? = null): FrameEntity =
        withContext(Dispatchers.IO) {
            val thumb = File(storage.thumbsDir(projectId), photo.name)
            if (!storage.createThumbnail(photo, thumb)) throw IOException("Gagal membuat thumbnail")
            recorded(projectId) {
                val count = frameDao.count(projectId)
                val pos = (atPosition ?: count).coerceIn(0, count)
                frameDao.shiftFrom(projectId, pos)
                val frame = FrameEntity(
                    projectId = projectId,
                    position = pos,
                    filePath = photo.absolutePath,
                    thumbPath = thumb.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
                val id = frameDao.insert(frame)
                refreshProjectThumb(projectId)
                frame.copy(id = id)
            }
        }

    suspend fun deleteFrames(projectId: Long, ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        recorded(projectId) {
            val targets = ids.chunked(500).flatMap { frameDao.getByIds(it) }
                .filter { it.projectId == projectId }
            targets.map { it.id }.chunked(500).forEach { frameDao.deleteByIds(it) }
            renumber(projectId)
            refreshProjectThumb(projectId)
        }
        // File sengaja tidak dihapus di sini agar undo bisa mengembalikannya.
    }

    suspend fun duplicateFrames(projectId: Long, ids: Set<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        if (!storage.hasEnoughSpace()) throw IOException("Penyimpanan hampir penuh")
        recorded(projectId) {
            val targets = frameDao.getFrames(projectId)
                .filter { it.id in ids }
                .sortedByDescending { it.position }
            for (f in targets) {
                val newPhoto = storage.newFrameFile(projectId)
                File(f.filePath).copyTo(newPhoto, overwrite = false)
                val newThumb = File(storage.thumbsDir(projectId), newPhoto.name)
                File(f.thumbPath).copyTo(newThumb, overwrite = false)
                frameDao.shiftFrom(projectId, f.position + 1)
                frameDao.insert(
                    f.copy(
                        id = 0,
                        position = f.position + 1,
                        filePath = newPhoto.absolutePath,
                        thumbPath = newThumb.absolutePath,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
            refreshProjectThumb(projectId)
        }
    }

    /** Geser frame terpilih satu langkah. delta = -1 (ke awal) atau +1 (ke akhir). */
    suspend fun moveFrames(projectId: Long, ids: Set<Long>, delta: Int) = withContext(Dispatchers.IO) {
        recorded(projectId) {
            val list = frameDao.getFrames(projectId).toMutableList()
            if (delta < 0) {
                for (i in 1 until list.size) {
                    if (list[i].id in ids && list[i - 1].id !in ids) {
                        val t = list[i]; list[i] = list[i - 1]; list[i - 1] = t
                    }
                }
            } else {
                for (i in list.size - 2 downTo 0) {
                    if (list[i].id in ids && list[i + 1].id !in ids) {
                        val t = list[i]; list[i] = list[i + 1]; list[i + 1] = t
                    }
                }
            }
            writePositions(list)
            refreshProjectThumb(projectId)
        }
    }

    /** Memindahkan satu frame ke indeks tertentu (dipakai drag and drop di timeline). */
    suspend fun moveFrameTo(projectId: Long, frameId: Long, toIndex: Int) = withContext(Dispatchers.IO) {
        recorded(projectId) {
            val list = frameDao.getFrames(projectId).toMutableList()
            val from = list.indexOfFirst { it.id == frameId }
            if (from >= 0) {
                val item = list.removeAt(from)
                list.add(toIndex.coerceIn(0, list.size), item)
                writePositions(list)
                refreshProjectThumb(projectId)
            }
        }
    }

    suspend fun setHold(projectId: Long, ids: Set<Long>, hold: Int) = withContext(Dispatchers.IO) {
        recorded(projectId) {
            ids.toList().chunked(500).forEach { frameDao.setHold(it, hold.coerceIn(1, 10)) }
        }
    }

    /** Mengganti foto sebuah frame. File lama tetap ada sampai proyek dibuka lagi (untuk undo). */
    /** Menyimpan coretan erase satu frame (dapat di-undo). */
    suspend fun setErase(frameId: Long, data: String?) = withContext(Dispatchers.IO) {
        val old = frameDao.getById(frameId) ?: throw IOException("Frame tidak ditemukan")
        recorded(old.projectId) { frameDao.update(old.copy(eraseData = data)) }
    }

    /** Menerapkan coretan erase yang sama ke semua frame proyek (satu langkah undo). */
    suspend fun setEraseAll(projectId: Long, data: String?) = withContext(Dispatchers.IO) {
        recorded(projectId) {
            frameDao.updateAll(frameDao.getFrames(projectId).map { it.copy(eraseData = data) })
        }
    }

    /** keepErase: pertahankan coretan erase (aman bila foto hanya berubah warna, tidak bila digeser/dipotong). */
    suspend fun replaceFrame(frameId: Long, photo: File, keepErase: Boolean = false) = withContext(Dispatchers.IO) {
        val old = frameDao.getById(frameId) ?: throw IOException("Frame tidak ditemukan")
        val thumb = File(storage.thumbsDir(old.projectId), photo.name)
        if (!storage.createThumbnail(photo, thumb)) throw IOException("Gagal membuat thumbnail")
        recorded(old.projectId) {
            frameDao.update(
                old.copy(
                    filePath = photo.absolutePath,
                    thumbPath = thumb.absolutePath,
                    eraseData = if (keepErase) old.eraseData else null
                )
            )
            refreshProjectThumb(old.projectId)
        }
    }

    /** Mengganti foto banyak frame sekaligus (Tahap 4: edit foto untuk semua frame) sebagai satu langkah undo. */
    suspend fun replaceFrames(projectId: Long, photos: Map<Long, File>, keepErase: Boolean = false) = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext
        val thumbs = HashMap<Long, File>()
        for ((id, photo) in photos) {
            val thumb = File(storage.thumbsDir(projectId), photo.name)
            if (!storage.createThumbnail(photo, thumb)) throw IOException("Gagal membuat thumbnail")
            thumbs[id] = thumb
        }
        recorded(projectId) {
            val rows = photos.keys.toList().chunked(500).flatMap { frameDao.getByIds(it) }
                .filter { it.projectId == projectId }
            frameDao.updateAll(rows.map {
                it.copy(
                    filePath = photos.getValue(it.id).absolutePath,
                    thumbPath = thumbs.getValue(it.id).absolutePath,
                    eraseData = if (keepErase) it.eraseData else null
                )
            })
            refreshProjectThumb(projectId)
        }
    }

    // ---------------- Internal ----------------

    private suspend fun writePositions(list: List<FrameEntity>) {
        val changed = list.mapIndexedNotNull { i, f -> if (f.position != i) f.copy(position = i) else null }
        if (changed.isNotEmpty()) frameDao.updateAll(changed)
    }

    private suspend fun renumber(projectId: Long) {
        writePositions(frameDao.getFrames(projectId))
    }

    private suspend fun refreshProjectThumb(projectId: Long) {
        val last = frameDao.getFrames(projectId).lastOrNull()
        projectDao.touch(projectId, System.currentTimeMillis(), last?.thumbPath)
    }
}
