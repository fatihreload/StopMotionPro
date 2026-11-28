package com.fatih.stopmotion.ui.erase

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.ChromaRepository
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.fx.ChromaKey
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.ImageFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Semua yang dibutuhkan layar Erase. base = foto setelah chroma key (tanpa coretan), ukuran sedang agar mulus. */
class EraseLoad(
    val base: Bitmap,
    val chroma: ChromaState,
    val initialData: String?,
    val previousData: String?,
    val frameNumber: Int,
    val frameCount: Int
)

class EraseViewModel(
    private val repo: ProjectRepository,
    private val chromaRepo: ChromaRepository,
    private val projectId: Long,
    private val frameId: Long
) : ViewModel() {

    /** Pengaturan latar terbaru dari proyek (berubah otomatis setelah latar bersih dipilih). */
    val chroma: StateFlow<ChromaState> = repo.observeProject(projectId)
        .filterNotNull()
        .map { ChromaState(it.chromaMode, it.chromaTolerance, it.chromaSoftness, it.chromaBgMode, it.chromaBgPath) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChromaState())

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Coretan yang belum disimpan, dijaga saat layar ini ditinggalkan sebentar (mis. memotret latar bersih). "" = kosong. */
    var draft: String? = null

    private val _load = MutableStateFlow<EraseLoad?>(null)
    val load: StateFlow<EraseLoad?> = _load.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val frame = repo.getFrame(frameId) ?: throw IllegalStateException("Frame tidak ditemukan")
                val frames = repo.getFrames(projectId)
                val index = frames.indexOfFirst { it.id == frameId }
                val p = repo.observeProject(projectId).first() ?: throw IllegalStateException("Proyek tidak ditemukan")
                val chroma = ChromaState(p.chromaMode, p.chromaTolerance, p.chromaSoftness, p.chromaBgMode, p.chromaBgPath)
                val base = withContext(Dispatchers.Default) {
                    val src = ImageFx.decode(frame.filePath, 1280) ?: throw IllegalStateException("Foto tidak dapat dibuka")
                    ChromaKey.process(src, chroma, emptyList()).also { src.recycle() }
                }
                _load.value = EraseLoad(
                    base = base,
                    chroma = chroma,
                    initialData = frame.eraseData,
                    previousData = frames.getOrNull(index - 1)?.eraseData,
                    frameNumber = index + 1,
                    frameCount = frames.size
                )
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Gagal membuka frame")
                _done.tryEmit(Unit)
            }
        }
    }

    /** Menjadikan foto frame lain (tanpa karakter/penyangga) sebagai latar yang tampil di area yang dihapus. */
    fun useFrameAsBackground(otherFrameId: Long) {
        viewModelScope.launch {
            try {
                val f = repo.getFrame(otherFrameId) ?: throw IllegalStateException("Frame tidak ditemukan")
                chromaRepo.setBackground(projectId, Uri.fromFile(File(f.filePath)))
                _messages.tryEmit("Latar bersih diatur")
            } catch (e: Exception) {
                _messages.tryEmit("Gagal mengatur latar: ${e.message}")
            }
        }
    }

    fun clearPlate() {
        viewModelScope.launch {
            try {
                chromaRepo.clearBackground(projectId)
                _messages.tryEmit("Latar bersih dilepas")
            } catch (e: Exception) {
                _messages.tryEmit("Gagal: ${e.message}")
            }
        }
    }

    fun setBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            try {
                chromaRepo.setBackground(projectId, uri)
                _messages.tryEmit("Latar bersih diatur")
            } catch (e: Exception) {
                _messages.tryEmit("Gagal mengatur latar: ${e.message}")
            }
        }
    }

    fun save(data: String?, applyToAll: Boolean) {
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                if (applyToAll) repo.setEraseAll(projectId, data) else repo.setErase(frameId, data)
                _done.tryEmit(Unit)
            } catch (e: Exception) {
                _messages.tryEmit("Gagal menyimpan: ${e.message}")
            } finally {
                _saving.value = false
            }
        }
    }
}

class EraseViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long,
    private val frameId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EraseViewModel(container.repository, container.chromaRepository, projectId, frameId) as T
}
