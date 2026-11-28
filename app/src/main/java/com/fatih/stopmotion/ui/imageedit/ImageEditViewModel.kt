package com.fatih.stopmotion.ui.imageedit

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import com.fatih.stopmotion.fx.EditParams
import com.fatih.stopmotion.fx.ImageFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ImageEditViewModel(
    private val repo: ProjectRepository,
    private val storage: ProjectStorage,
    private val projectId: Long,
    private val frameId: Long
) : ViewModel() {

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _preview = MutableStateFlow<Bitmap?>(null)
    /** Salinan foto ukuran sedang untuk pratinjau (foto resolusi penuh tidak dimuat ke layar). */
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)
    /** null = tidak sedang menyimpan. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    init {
        viewModelScope.launch {
            val frame = repo.getFrame(frameId)
            if (frame == null) {
                _messages.tryEmit("Frame tidak ditemukan")
                _done.tryEmit(Unit)
                return@launch
            }
            val bmp = withContext(Dispatchers.Default) { ImageFx.decode(frame.filePath, 1280) }
            if (bmp == null) {
                _messages.tryEmit("Foto tidak dapat dibuka")
                _done.tryEmit(Unit)
            } else _preview.value = bmp
        }
    }

    fun save(edit: EditParams, applyAll: Boolean) {
        if (_progress.value != null) return
        viewModelScope.launch {
            _progress.value = 0f
            val created = ArrayList<File>()
            try {
                if (!storage.hasEnoughSpace()) throw IOException("Penyimpanan hampir penuh")
                val targets = if (applyAll) repo.getFrames(projectId)
                else listOfNotNull(repo.getFrame(frameId))
                if (targets.isEmpty()) throw IOException("Tidak ada frame")
                val result = LinkedHashMap<Long, File>()
                withContext(Dispatchers.IO) {
                    targets.forEachIndexed { i, f ->
                        val dest = storage.newFrameFile(projectId)
                        created.add(dest)
                        if (!ImageFx.render(File(f.filePath), dest, edit)) {
                            throw IOException("Gagal memproses frame ${i + 1}")
                        }
                        result[f.id] = dest
                        _progress.value = (i + 1f) / targets.size
                    }
                    repo.replaceFrames(projectId, result, keepErase = edit.geo.isIdentity)
                }
                _done.tryEmit(Unit)
            } catch (e: Exception) {
                created.forEach { it.delete() }
                _messages.tryEmit("Gagal menyimpan: ${e.message}")
            } finally {
                _progress.value = null
            }
        }
    }
}

class ImageEditViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long,
    private val frameId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ImageEditViewModel(container.repository, container.storage, projectId, frameId) as T
}
