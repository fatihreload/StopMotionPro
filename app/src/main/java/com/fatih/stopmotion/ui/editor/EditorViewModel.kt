package com.fatih.stopmotion.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.AudioRepository
import android.net.Uri
import com.fatih.stopmotion.data.AudioTrackEntity
import com.fatih.stopmotion.data.ChromaRepository
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.data.LayerRepository
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.FxState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CancellationException

/** Proses menambah layer yang sedang berjalan. progress null = tidak diketahui (gambar). */
data class LayerBusy(val progress: Float?, val label: String)

class EditorViewModel(
    private val repo: ProjectRepository,
    audioRepo: AudioRepository,
    private val chromaRepo: ChromaRepository,
    private val layerRepo: LayerRepository,
    private val projectId: Long
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val audio: StateFlow<List<AudioTrackEntity>> = audioRepo.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val layers: StateFlow<List<LayerEntity>> = layerRepo.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _layerBusy = MutableStateFlow<LayerBusy?>(null)
    val layerBusy: StateFlow<LayerBusy?> = _layerBusy.asStateFlow()
    private var layerJob: Job? = null

    val history = repo.observeHistory(projectId)

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val mutex = Mutex()

    fun toggle(id: Long) = _selected.update { if (id in it) it - id else it + id }
    fun clearSelection() { _selected.value = emptySet() }

    private fun op(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                runCatching { block() }.onFailure { _messages.tryEmit("Gagal: ${it.message}") }
            }
        }
    }

    fun deleteSelected(ids: Set<Long>) = op {
        repo.deleteFrames(projectId, ids)
        _selected.value = emptySet()
    }

    fun duplicateSelected(ids: Set<Long>) = op { repo.duplicateFrames(projectId, ids) }
    fun move(frameId: Long, toIndex: Int) = op { repo.moveFrameTo(projectId, frameId, toIndex) }
    fun setFps(fps: Int) = op { repo.updateFps(projectId, fps) }
    fun setEffects(fx: FxState) = op {
        repo.updateEffects(projectId, fx.filter, fx.intensity, fx.vignette, fx.fadeIn, fx.fadeOut, fx.fadeWhite)
    }

    fun setChroma(c: ChromaState) = op {
        repo.updateChroma(projectId, c.mode, c.tolerance, c.softness, c.bgMode)
    }

    fun setBackgroundImage(uri: Uri) = op { chromaRepo.setBackground(projectId, uri) }

    fun clearBackgroundImage() = op { chromaRepo.clearBackground(projectId) }

    // ---------------- Layer ----------------

    fun addImageLayer(uri: Uri, startMs: Long, durationMs: Long, onAdded: (Long) -> Unit) {
        if (layerJob?.isActive == true) return
        layerJob = viewModelScope.launch {
            _layerBusy.value = LayerBusy(null, "Menambah gambar")
            try {
                onAdded(layerRepo.addImage(projectId, uri, startMs, durationMs).id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Gagal menambah layer: ${e.message}")
            } finally {
                _layerBusy.value = null
            }
        }
    }

    fun addVideoLayer(uri: Uri, startMs: Long, fps: Int, chromaMode: Int, onAdded: (Long) -> Unit) {
        if (layerJob?.isActive == true) return
        layerJob = viewModelScope.launch {
            _layerBusy.value = LayerBusy(0f, "Memproses klip")
            try {
                val added = layerRepo.addVideo(projectId, uri, startMs, fps, chromaMode) {
                    _layerBusy.value = LayerBusy(it, "Memproses klip")
                }
                onAdded(added.layer.id)
                if (added.hasAudio) {
                    _messages.tryEmit("Klip punya suara. Suara tidak ikut; tambahkan lewat Studio Audio bila perlu.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("Gagal menambah klip: ${e.message}")
            } finally {
                _layerBusy.value = null
            }
        }
    }

    fun cancelLayerJob() {
        layerJob?.cancel()
    }

    fun updateLayer(l: LayerEntity) {
        viewModelScope.launch {
            runCatching { layerRepo.updateProps(l) }.onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun deleteLayer(l: LayerEntity) {
        viewModelScope.launch {
            runCatching { layerRepo.delete(l) }.onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun moveLayer(id: Long, up: Boolean) {
        viewModelScope.launch {
            runCatching { layerRepo.move(projectId, id, up) }.onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun undo() = op { repo.undo(projectId) }
    fun redo() = op { repo.redo(projectId) }
}

class EditorViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EditorViewModel(
            container.repository, container.audioRepository, container.chromaRepository,
            container.layerRepository, projectId
        ) as T
}
