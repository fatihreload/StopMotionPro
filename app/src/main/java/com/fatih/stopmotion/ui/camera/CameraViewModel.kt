package com.fatih.stopmotion.ui.camera

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.ChromaRepository
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import com.fatih.stopmotion.fx.ChromaState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object CaptureMode {
    const val APPEND = "append"
    const val INSERT = "insert"
    const val REPLACE = "replace"
    /** Memotret latar bersih (tanpa tangan/benda) untuk Erase; tidak menambah frame. */
    const val PLATE = "plate"
}

class CameraViewModel(
    private val repo: ProjectRepository,
    private val storage: ProjectStorage,
    private val chromaRepo: ChromaRepository,
    private val projectId: Long,
    val mode: String,
    /** INSERT: posisi sisip awal. REPLACE: id frame yang diganti. */
    val target: Long
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Posisi sisip berikutnya (hanya dipakai pada mode INSERT). */
    val insertPosition = MutableStateFlow(target.toInt().coerceAtLeast(0))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val mutex = Mutex()

    /** Menyiapkan file tujuan foto. Mengembalikan null jika penyimpanan hampir penuh. */
    fun newFrameFile(): File? {
        if (!storage.hasEnoughSpace()) {
            _messages.tryEmit("Penyimpanan hampir penuh. Hapus file lalu coba lagi.")
            return null
        }
        return storage.newFrameFile(projectId)
    }

    fun onPhotoSaved(file: File, onReplaced: () -> Unit = {}) {
        val pos = insertPosition.value
        if (mode == CaptureMode.INSERT) insertPosition.value = pos + 1
        viewModelScope.launch {
            mutex.withLock { // jaga urutan jika foto diambil berturut-turut
                runCatching {
                    when (mode) {
                        CaptureMode.INSERT -> repo.insertFrame(projectId, file, pos)
                        CaptureMode.REPLACE -> repo.replaceFrame(target, file)
                        CaptureMode.PLATE -> {
                            chromaRepo.setBackground(projectId, Uri.fromFile(file))
                            file.delete() // salinannya sudah disimpan sebagai gambar latar proyek
                        }
                        else -> repo.insertFrame(projectId, file, null)
                    }
                }.onSuccess {
                    if (mode == CaptureMode.REPLACE || mode == CaptureMode.PLATE) onReplaced()
                }.onFailure {
                    file.delete()
                    _messages.tryEmit("Gagal menyimpan frame: ${it.message}")
                }
            }
        }
    }

    fun setOnion(enabled: Boolean, opacity: Float, count: Int, color: Int) {
        viewModelScope.launch { repo.updateOnion(projectId, enabled, opacity, count, color) }
    }

    /** Penghapus latar (green/blue screen): disimpan per proyek, dipakai di editor dan ekspor. */
    fun setChroma(c: ChromaState) {
        viewModelScope.launch {
            runCatching { repo.updateChroma(projectId, c.mode, c.tolerance, c.softness, c.bgMode) }
                .onFailure { _messages.tryEmit("Gagal menyimpan: ${it.message}") }
        }
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            runCatching { chromaRepo.setBackground(projectId, uri) }
                .onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun clearBackgroundImage() {
        viewModelScope.launch { runCatching { chromaRepo.clearBackground(projectId) } }
    }

    fun reportError(message: String) {
        _messages.tryEmit(message)
    }
}

class CameraViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long,
    private val mode: String,
    private val target: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        CameraViewModel(container.repository, container.storage, container.chromaRepository, projectId, mode, target) as T
}
