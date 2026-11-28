package com.fatih.stopmotion

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fatih.stopmotion.audio.SoundFx
import com.fatih.stopmotion.data.AppDatabase
import com.fatih.stopmotion.data.AudioKind
import com.fatih.stopmotion.data.AudioTrackEntity
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectManager
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Context yang mengarahkan filesDir ke folder sementara, supaya uji tidak menyentuh proyek asli di perangkat. */
private class SandboxContext(base: Context, private val dir: File) : ContextWrapper(base) {
    override fun getFilesDir(): File = dir
}

/** Uji di perangkat/emulator: Room (in-memory), cascade delete, serta cadangan dan duplikat proyek. */
@RunWith(AndroidJUnit4::class)
class ProjectDataInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var storage: ProjectStorage
    private lateinit var sandbox: File
    private val createdProjects = ArrayList<Long>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        sandbox = File(context.cacheDir, "uji_${System.nanoTime()}").apply { mkdirs() }
        storage = ProjectStorage(SandboxContext(context, sandbox))
    }

    @After
    fun tearDown() {
        createdProjects.forEach { storage.deleteProject(it) }
        db.close()
        sandbox.deleteRecursively()
    }

    private suspend fun newProject(name: String): Long {
        val id = db.projectDao().insert(ProjectEntity(name = name, createdAt = 1, updatedAt = 1))
        createdProjects.add(id)
        return id
    }

    private suspend fun addFrame(projectId: Long, position: Int): FrameEntity {
        val photo = storage.newFrameFile(projectId)
        val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        val thumb = File(storage.thumbsDir(projectId), photo.name)
        assertTrue(storage.createThumbnail(photo, thumb))
        val frame = FrameEntity(
            projectId = projectId, position = position, filePath = photo.absolutePath,
            thumbPath = thumb.absolutePath, holdCount = 2, createdAt = 10L + position
        )
        return frame.copy(id = db.frameDao().insert(frame))
    }

    private suspend fun addAudio(projectId: Long): AudioTrackEntity {
        val file = storage.newAudioFile(projectId, "wav")
        SoundFx.writeWav(file, "pop")
        val track = AudioTrackEntity(
            projectId = projectId, name = "Pop", filePath = file.absolutePath, kind = AudioKind.SFX,
            durationMs = 180, startMs = 250, trimStartMs = 0, trimEndMs = 180, volume = 0.6f,
            fadeInMs = 0, fadeOutMs = 50, muted = false, createdAt = 5
        )
        return track.copy(id = db.audioDao().insert(track))
    }

    @Test
    fun deletingProjectCascadesToFramesAndAudio() = runBlocking {
        val id = newProject("Cascade")
        addFrame(id, 0)
        addAudio(id)
        assertEquals(1, db.frameDao().getFrames(id).size)
        assertEquals(1, db.audioDao().getAll(id).size)

        db.projectDao().delete(id)

        assertEquals(0, db.frameDao().getAll(id).size)
        assertEquals(0, db.audioDao().getAll(id).size)
    }

    @Test
    fun effectsArePersisted() = runBlocking {
        val id = newProject("Efek")
        db.projectDao().updateEffects(id, 3, 0.5f, 0.2f, 4, 5, true)
        val p = db.projectDao().get(id)!!
        assertEquals(3, p.fxFilter)
        assertEquals(0.5f, p.fxIntensity, 0f)
        assertEquals(0.2f, p.fxVignette, 0f)
        assertEquals(4, p.fxFadeIn)
        assertEquals(5, p.fxFadeOut)
        assertTrue(p.fxFadeWhite)
    }

    @Test
    fun chromaSettingsArePersisted() = runBlocking {
        val id = newProject("Chroma")
        db.projectDao().updateChroma(id, 1, 0.6f, 0.2f, 4)
        db.projectDao().updateChromaBgPath(id, "/x/y.jpg")
        val p = db.projectDao().get(id)!!
        assertEquals(1, p.chromaMode)
        assertEquals(0.6f, p.chromaTolerance, 0f)
        assertEquals(0.2f, p.chromaSoftness, 0f)
        assertEquals(4, p.chromaBgMode)
        assertEquals("/x/y.jpg", p.chromaBgPath)
    }

    @Test
    fun softDeletedFramesAreHiddenFromListsAndCounts() = runBlocking {
        val id = newProject("Hapus lunak")
        val a = addFrame(id, 0)
        addFrame(id, 1)
        db.frameDao().markDeleted(listOf(a.id))

        assertEquals(1, db.frameDao().getFrames(id).size)
        assertEquals(2, db.frameDao().getAll(id).size)
        val summary = db.projectDao().observeSummaries().first().first { it.project.id == id }
        assertEquals(1, summary.frameCount)
    }

    @Test
    fun backupThenRestoreRecreatesProject() = runBlocking {
        val manager = ProjectManager(db, storage)
        val repo = ProjectRepository(db, storage)
        val id = repo.createProject("Uji Cadangan")
        createdProjects.add(id)
        addFrame(id, 0)
        addFrame(id, 1)
        addAudio(id)

        val out = ByteArrayOutputStream()
        manager.backup(id, out)
        val newId = manager.restore(ByteArrayInputStream(out.toByteArray()))
        createdProjects.add(newId)

        assertNotEquals(id, newId)
        val restored = db.projectDao().get(newId)!!
        assertEquals("Uji Cadangan (2)", restored.name)
        val frames = db.frameDao().getFrames(newId)
        assertEquals(2, frames.size)
        assertEquals(listOf(2, 2), frames.map { it.holdCount })
        assertTrue(frames.all { File(it.filePath).exists() && File(it.thumbPath).exists() })
        val audio = db.audioDao().getAll(newId)
        assertEquals(1, audio.size)
        assertEquals(250L, audio[0].startMs)
        assertEquals(0.6f, audio[0].volume, 0f)
        assertTrue(File(audio[0].filePath).exists())
    }

    @Test
    fun duplicateCopiesFilesInsteadOfSharingThem() = runBlocking {
        val manager = ProjectManager(db, storage)
        val id = newProject("Asli")
        val frame = addFrame(id, 0)
        addAudio(id)

        val copyId = manager.duplicate(id)
        createdProjects.add(copyId)

        assertEquals("Asli (salinan)", db.projectDao().get(copyId)!!.name)
        val copied = db.frameDao().getFrames(copyId).single()
        assertNotEquals(frame.filePath, copied.filePath)
        assertTrue(File(copied.filePath).exists())
        assertEquals(1, db.audioDao().getAll(copyId).size)

        // Menghapus salinan tidak boleh merusak proyek asli.
        db.projectDao().delete(copyId)
        storage.deleteProject(copyId)
        assertTrue(File(frame.filePath).exists())
    }
}
