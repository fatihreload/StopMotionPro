package com.fatih.stopmotion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM frames f WHERE f.projectId = p.id AND f.isDeleted = 0) AS frameCount " +
            "FROM projects p ORDER BY p.updatedAt DESC"
    )
    fun observeSummaries(): Flow<List<ProjectSummary>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<ProjectEntity?>

    @Query("UPDATE projects SET updatedAt = :time, thumbnailPath = :thumb WHERE id = :id")
    suspend fun touch(id: Long, time: Long, thumb: String?)

    @Query(
        "UPDATE projects SET onionEnabled = :enabled, onionOpacity = :opacity, " +
            "onionFrameCount = :count, onionColor = :color WHERE id = :id"
    )
    suspend fun updateOnion(id: Long, enabled: Boolean, opacity: Float, count: Int, color: Int)

    @Query("UPDATE projects SET fps = :fps WHERE id = :id")
    suspend fun updateFps(id: Long, fps: Int)

    @Query(
        "UPDATE projects SET fxFilter = :filter, fxIntensity = :intensity, fxVignette = :vignette, " +
            "fxFadeIn = :fadeIn, fxFadeOut = :fadeOut, fxFadeWhite = :white WHERE id = :id"
    )
    suspend fun updateEffects(
        id: Long, filter: Int, intensity: Float, vignette: Float,
        fadeIn: Int, fadeOut: Int, white: Boolean
    )

    @Query(
        "UPDATE projects SET chromaMode = :mode, chromaTolerance = :tolerance, " +
            "chromaSoftness = :softness, chromaBgMode = :bgMode WHERE id = :id"
    )
    suspend fun updateChroma(id: Long, mode: Int, tolerance: Float, softness: Float, bgMode: Int)

    @Query("UPDATE projects SET chromaBgPath = :path WHERE id = :id")
    suspend fun updateChromaBgPath(id: Long, path: String?)

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Query("SELECT name FROM projects")
    suspend fun allNames(): List<String>

    @Query("UPDATE projects SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FrameDao {
    @Insert
    suspend fun insert(frame: FrameEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(frames: List<FrameEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(frames: List<FrameEntity>)

    @Update
    suspend fun update(frame: FrameEntity)

    @Update
    suspend fun updateAll(frames: List<FrameEntity>)

    @Query("DELETE FROM frames WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM frames WHERE projectId = :projectId AND isDeleted = 0 ORDER BY position ASC")
    fun observeFrames(projectId: Long): Flow<List<FrameEntity>>

    @Query("SELECT * FROM frames WHERE projectId = :projectId AND isDeleted = 0 ORDER BY position ASC")
    suspend fun getFrames(projectId: Long): List<FrameEntity>

    /** Semua baris proyek termasuk yang dihapus lunak (untuk snapshot undo/redo). */
    @Query("SELECT * FROM frames WHERE projectId = :projectId")
    suspend fun getAll(projectId: Long): List<FrameEntity>

    @Query("SELECT * FROM frames WHERE id = :id")
    suspend fun getById(id: Long): FrameEntity?

    @Query("SELECT * FROM frames WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FrameEntity>

    @Query("UPDATE frames SET isDeleted = 1 WHERE id IN (:ids)")
    suspend fun markDeleted(ids: List<Long>)

    @Query("UPDATE frames SET holdCount = :hold WHERE id IN (:ids)")
    suspend fun setHold(ids: List<Long>, hold: Int)

    @Query("UPDATE frames SET position = position + 1 WHERE projectId = :projectId AND isDeleted = 0 AND position >= :from")
    suspend fun shiftFrom(projectId: Long, from: Int)

    @Query("SELECT COUNT(*) FROM frames WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun count(projectId: Long): Int

    @Query("SELECT * FROM frames WHERE isDeleted = 1")
    suspend fun getAllDeleted(): List<FrameEntity>

    @Query("DELETE FROM frames WHERE id IN (:ids)")
    suspend fun deleteRows(ids: List<Long>)
}

@Dao
interface AudioDao {
    @Insert
    suspend fun insert(track: AudioTrackEntity): Long

    @Update
    suspend fun update(track: AudioTrackEntity)

    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(projectId: Long): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE projectId = :projectId ORDER BY createdAt ASC, id ASC")
    fun observe(projectId: Long): Flow<List<AudioTrackEntity>>
}

@Dao
interface LayerDao {
    @Insert
    suspend fun insert(layer: LayerEntity): Long

    @Query("DELETE FROM layers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY zOrder ASC, id ASC")
    fun observe(projectId: Long): Flow<List<LayerEntity>>

    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY zOrder ASC, id ASC")
    suspend fun getAll(projectId: Long): List<LayerEntity>

    @Query("SELECT COALESCE(MAX(zOrder), -1) FROM layers WHERE projectId = :projectId")
    suspend fun maxZ(projectId: Long): Int

    @Query("UPDATE layers SET zOrder = :z WHERE id = :id")
    suspend fun updateZ(id: Long, z: Int)

    /** Semua pengaturan yang bisa diedit pengguna, kecuali urutan tumpukan (diatur terpisah lewat updateZ). */
    @Query(
        "UPDATE layers SET name = :name, visible = :visible, startMs = :startMs, durationMs = :durationMs, " +
            "loopClip = :loop, posX = :posX, posY = :posY, scale = :scale, rotation = :rotation, opacity = :opacity, " +
            "chromaMode = :chromaMode, chromaTolerance = :chromaTolerance, chromaSoftness = :chromaSoftness, " +
            "keyframes = :keyframes WHERE id = :id"
    )
    suspend fun updateProps(
        id: Long, name: String, visible: Boolean, startMs: Long, durationMs: Long, loop: Boolean,
        posX: Float, posY: Float, scale: Float, rotation: Float, opacity: Float,
        chromaMode: Int, chromaTolerance: Float, chromaSoftness: Float, keyframes: String?
    )
}
