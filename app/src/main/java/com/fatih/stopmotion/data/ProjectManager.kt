package com.fatih.stopmotion.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Operasi tingkat proyek: info, duplikat, cadangan (ZIP) dan pemulihan, serta pembersihan penyimpanan. */
class ProjectManager(
    private val db: AppDatabase,
    private val storage: ProjectStorage
) {
    private val projectDao = db.projectDao()
    private val frameDao = db.frameDao()
    private val audioDao = db.audioDao()
    private val layerDao = db.layerDao()

    data class Info(
        val project: ProjectEntity,
        val frameCount: Int,
        val ticks: Int,
        val audioCount: Int,
        val bytes: Long
    )

    data class StorageStats(val projectsBytes: Long, val freeBytes: Long)

    suspend fun info(id: Long): Info = withContext(Dispatchers.IO) {
        val p = projectDao.get(id) ?: throw IOException("Proyek tidak ditemukan")
        val frames = frameDao.getFrames(id)
        Info(
            project = p,
            frameCount = frames.size,
            ticks = frames.sumOf { it.holdCount.coerceAtLeast(1) },
            audioCount = audioDao.getAll(id).size,
            bytes = storage.projectBytes(id)
        )
    }

    suspend fun storageStats(): StorageStats = withContext(Dispatchers.IO) {
        StorageStats(storage.totalBytes(), storage.usableBytes())
    }

    suspend fun clearExports(): Long = withContext(Dispatchers.IO) { storage.clearExports() }

    /** Menyalin seluruh proyek (foto, thumbnail, audio, pengaturan) menjadi proyek baru. */
    suspend fun duplicate(id: Long, onProgress: (Float) -> Unit = {}): Long = withContext(Dispatchers.IO) {
        val src = projectDao.get(id) ?: throw IOException("Proyek tidak ditemukan")
        val needed = storage.projectBytes(id) + 50L * 1024 * 1024
        if (storage.usableBytes() < needed) throw IOException("Penyimpanan tidak cukup untuk menduplikat proyek")

        val frames = frameDao.getFrames(id)
        val tracks = audioDao.getAll(id)
        val layers = layerDao.getAll(id)
        val now = System.currentTimeMillis()
        val newName = ProjectNames.copyName(src.name, projectDao.allNames())
        val newId = projectDao.insert(
            src.copy(id = 0, name = newName, createdAt = now, updatedAt = now, thumbnailPath = null, chromaBgPath = null)
        )
        try {
            storage.projectDir(newId)
            val total = (frames.size + tracks.size).coerceAtLeast(1)
            var done = 0
            var lastThumb: String? = null
            for (f in frames) {
                ensureActive()
                val photo = storage.newFrameFile(newId)
                File(f.filePath).copyTo(photo, overwrite = false)
                val thumb = File(storage.thumbsDir(newId), photo.name)
                File(f.thumbPath).copyTo(thumb, overwrite = false)
                frameDao.insert(
                    f.copy(id = 0, projectId = newId, filePath = photo.absolutePath, thumbPath = thumb.absolutePath)
                )
                lastThumb = thumb.absolutePath
                onProgress(++done / total.toFloat())
            }
            for (t in tracks) {
                ensureActive()
                val ext = File(t.filePath).extension.ifEmpty { "bin" }
                val dest = storage.newAudioFile(newId, ext)
                File(t.filePath).copyTo(dest, overwrite = false)
                audioDao.insert(t.copy(id = 0, projectId = newId, filePath = dest.absolutePath))
                onProgress(++done / total.toFloat())
            }
            for (l in layers) {
                ensureActive()
                val dir = storage.newLayerDir(newId)
                val srcFile = File(l.filePath)
                if (l.kind == LayerKind.IMAGE) {
                    srcFile.copyTo(File(dir, srcFile.name), overwrite = false)
                } else {
                    l.frameDir?.let { File(it).copyRecursively(File(dir, "f"), overwrite = false) }
                }
                layerDao.insert(
                    l.copy(
                        id = 0, projectId = newId, filePath = File(dir, srcFile.name).absolutePath,
                        frameDir = if (l.kind == LayerKind.VIDEO) File(dir, "f").absolutePath else null
                    )
                )
            }
            src.chromaBgPath?.let { File(it) }?.takeIf { it.exists() }?.let { bg ->
                val dest = storage.newBgFile(newId, bg.extension.ifEmpty { "jpg" })
                bg.copyTo(dest, overwrite = false)
                projectDao.updateChromaBgPath(newId, dest.absolutePath)
            }
            projectDao.touch(newId, now, lastThumb)
            newId
        } catch (e: Throwable) {
            projectDao.delete(newId)
            storage.deleteProject(newId)
            throw e
        }
    }

    /** Menulis proyek ke satu file ZIP: manifest.json lebih dulu, lalu foto dan audio. */
    suspend fun backup(id: Long, out: OutputStream, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val p = projectDao.get(id) ?: throw IOException("Proyek tidak ditemukan")
            val frames = frameDao.getFrames(id)
            val tracks = audioDao.getAll(id)
            val layers = layerDao.getAll(id)
            val total = (frames.size + tracks.size + layers.sumOf { if (it.kind == LayerKind.IMAGE) 1 else it.frameCount })
                .coerceAtLeast(1)
            var done = 0
            val bgFile = p.chromaBgPath?.let { File(it) }?.takeIf { it.exists() }
            val bgEntry = bgFile?.let { BackupManifest.backgroundEntry(it.path) }
            ZipOutputStream(BufferedOutputStream(out, 1 shl 16)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManifest.ENTRY))
                zip.write(BackupManifest.toJson(p, frames, tracks, bgEntry, layers).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (bgFile != null && bgEntry != null) {
                    zip.putNextEntry(ZipEntry(bgEntry))
                    bgFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                frames.forEachIndexed { i, f ->
                    ensureActive()
                    zip.putNextEntry(ZipEntry(BackupManifest.frameEntry(i)))
                    File(f.filePath).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    onProgress(++done / total.toFloat())
                }
                tracks.forEachIndexed { i, t ->
                    ensureActive()
                    zip.putNextEntry(ZipEntry(BackupManifest.audioEntry(i, t.filePath)))
                    File(t.filePath).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    onProgress(++done / total.toFloat())
                }
                // Layer: gambar disimpan apa adanya; klip video hanya frame JPEG-nya (video asli tidak diperlukan lagi).
                layers.forEachIndexed { i, l ->
                    if (l.kind == LayerKind.IMAGE) {
                        ensureActive()
                        zip.putNextEntry(ZipEntry(BackupManifest.layerImageEntry(i, l.filePath)))
                        File(l.filePath).inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        onProgress(++done / total.toFloat())
                    } else {
                        for (n in 1..l.frameCount) {
                            ensureActive()
                            val f = File(l.frameDir ?: "", String.format(java.util.Locale.US, "%05d.jpg", n))
                            if (!f.exists()) throw IOException("Frame klip \"${l.name}\" hilang")
                            zip.putNextEntry(ZipEntry(BackupManifest.layerFrameEntry(i, n)))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                            onProgress(++done / total.toFloat())
                        }
                    }
                }
            }
        }

    /** Membaca file cadangan menjadi proyek baru. Mengembalikan id proyek. Bila gagal, tidak ada yang tersisa. */
    suspend fun restore(input: InputStream, onProgress: (Float) -> Unit = {}): Long =
        withContext(Dispatchers.IO) {
            if (!storage.hasEnoughSpace(100L * 1024 * 1024)) throw IOException("Penyimpanan hampir penuh")
            val zip = ZipInputStream(BufferedInputStream(input, 1 shl 16))
            val first = zip.nextEntry
            if (first == null || first.name != BackupManifest.ENTRY) {
                throw IOException("Bukan file cadangan Fatih Stop Motion")
            }
            val manifestBytes = readLimited(zip, 8 * 1024 * 1024)
            val parsed = BackupManifest.parse(String(manifestBytes, Charsets.UTF_8))

            val now = System.currentTimeMillis()
            val name = ProjectNames.uniqueName(parsed.project.name, projectDao.allNames())
            val newId = projectDao.insert(parsed.project.copy(name = name, createdAt = now, updatedAt = now))
            try {
                storage.projectDir(newId)
                val frameIndex = HashMap<String, Int>()
                parsed.frames.forEachIndexed { i, m -> frameIndex[m.file] = i }
                val audioByFile = parsed.audio.associateBy { it.file }
                val frameDone = BooleanArray(parsed.frames.size)
                val total = (parsed.frames.size + parsed.audio.size +
                    parsed.layers.sumOf { it.frameCount }).coerceAtLeast(1)
                var done = 0
                val layerDirs = HashMap<Int, File>()
                val layerImage = HashMap<Int, File>()
                val layerGot = IntArray(parsed.layers.size)

                while (true) {
                    ensureActive()
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val fi = frameIndex[e.name]
                    val am = audioByFile[e.name]
                    if (parsed.backgroundFile != null && e.name == parsed.backgroundFile) {
                        val ext = BackupManifest.backgroundEntry(e.name).substringAfter('.')
                        val dest = storage.newBgFile(newId, ext)
                        dest.outputStream().use { zip.copyTo(it) }
                        projectDao.updateChromaBgPath(newId, dest.absolutePath)
                    } else if (fi != null && !frameDone[fi]) {
                        val meta = parsed.frames[fi]
                        val photo = storage.newFrameFile(newId)
                        photo.outputStream().use { zip.copyTo(it) }
                        val thumb = File(storage.thumbsDir(newId), photo.name)
                        if (!storage.createThumbnail(photo, thumb)) throw IOException("Foto frame ${fi + 1} rusak")
                        frameDao.insert(
                            FrameEntity(
                                projectId = newId,
                                position = fi,
                                filePath = photo.absolutePath,
                                thumbPath = thumb.absolutePath,
                                holdCount = meta.hold,
                                createdAt = if (meta.createdAt > 0) meta.createdAt else now,
                                eraseData = meta.erase
                            )
                        )
                        frameDone[fi] = true
                        onProgress(++done / total.toFloat())
                    } else if (LAYER_ENTRY.matches(e.name)) {
                        val m = LAYER_ENTRY.matchEntire(e.name)!!
                        val li = m.groupValues[1].toInt() - 1
                        val rest = m.groupValues[2]
                        val meta = parsed.layers.getOrNull(li)
                        if (meta != null) {
                            val dir = layerDirs.getOrPut(li) { storage.newLayerDir(newId) }
                            if (meta.kind == LayerKind.IMAGE) {
                                val ext = IMAGE_NAME.matchEntire(rest)?.groupValues?.get(1)
                                if (ext != null && !layerImage.containsKey(li)) {
                                    val dest = File(dir, "src.$ext")
                                    dest.outputStream().use { zip.copyTo(it) }
                                    layerImage[li] = dest
                                    layerGot[li]++
                                    onProgress(++done / total.toFloat())
                                }
                            } else {
                                val n = FRAME_NAME.matchEntire(rest)?.groupValues?.get(1)?.toInt()
                                if (n != null && n in 1..meta.frameCount) {
                                    val fdir = File(dir, "f").apply { mkdirs() }
                                    File(fdir, String.format(java.util.Locale.US, "%05d.jpg", n))
                                        .outputStream().use { zip.copyTo(it) }
                                    layerGot[li]++
                                    onProgress(++done / total.toFloat())
                                }
                            }
                        }
                    } else if (am != null) {
                        val ext = File(am.file).extension.ifEmpty { "bin" }
                        val dest = storage.newAudioFile(newId, ext)
                        dest.outputStream().use { zip.copyTo(it) }
                        audioDao.insert(
                            AudioTrackEntity(
                                projectId = newId, name = am.name, filePath = dest.absolutePath, kind = am.kind,
                                durationMs = am.durationMs, startMs = am.startMs, trimStartMs = am.trimStartMs,
                                trimEndMs = am.trimEndMs, volume = am.volume, fadeInMs = am.fadeInMs,
                                fadeOutMs = am.fadeOutMs, muted = am.muted,
                                createdAt = if (am.createdAt > 0) am.createdAt else now
                            )
                        )
                        onProgress(++done / total.toFloat())
                    }
                }
                if (frameDone.any { !it }) throw IOException("File cadangan tidak lengkap")
                parsed.layers.forEachIndexed { li, meta ->
                    val dir = layerDirs[li]
                    val expected = if (meta.kind == LayerKind.IMAGE) 1 else meta.frameCount
                    if (dir == null || layerGot[li] < expected) throw IOException("File cadangan tidak lengkap (layer ${li + 1})")
                    layerDao.insert(
                        LayerEntity(
                            projectId = newId, name = meta.name, kind = meta.kind,
                            filePath = (layerImage[li] ?: File(dir, "src.mp4")).absolutePath,
                            frameDir = if (meta.kind == LayerKind.VIDEO) File(dir, "f").absolutePath else null,
                            frameCount = meta.frameCount, frameRate = meta.frameRate,
                            srcWidth = meta.srcWidth, srcHeight = meta.srcHeight, zOrder = meta.zOrder,
                            startMs = meta.startMs, durationMs = meta.durationMs, loopClip = meta.loopClip,
                            posX = meta.posX, posY = meta.posY, scale = meta.scale, rotation = meta.rotation,
                            opacity = meta.opacity, chromaMode = meta.chromaMode,
                            chromaTolerance = meta.chromaTolerance, chromaSoftness = meta.chromaSoftness,
                            visible = meta.visible, createdAt = if (meta.createdAt > 0) meta.createdAt else now,
                            keyframes = meta.keyframes
                        )
                    )
                }
                db.withTransaction {
                    projectDao.touch(newId, now, frameDao.getFrames(newId).lastOrNull()?.thumbPath)
                }
                newId
            } catch (e: Throwable) {
                projectDao.delete(newId)
                storage.deleteProject(newId)
                throw e
            }
        }

    private companion object {
        /** Entri layer di ZIP: layers/001/<berkas>. Nama dibatasi ketat agar tidak ada path dari luar yang dipakai. */
        val LAYER_ENTRY = Regex("^layers/(\\d{3})/([^/]+)$")
        val FRAME_NAME = Regex("^(\\d{5})\\.jpg$")
        val IMAGE_NAME = Regex("^src\\.([A-Za-z0-9]{1,5})$")
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw IOException("File cadangan tidak valid")
        }
        return out.toByteArray()
    }
}
