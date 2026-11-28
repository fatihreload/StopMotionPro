package com.fatih.stopmotion.ui.audio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.AudioKind
import com.fatih.stopmotion.data.AudioRepository
import com.fatih.stopmotion.data.AudioTrackEntity
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AudioViewModel(
    repo: ProjectRepository,
    private val audio: AudioRepository,
    private val storage: ProjectStorage,
    private val projectId: Long
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<AudioTrackEntity>> = audio.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun importUri(uri: Uri) = run { audio.importFromUri(projectId, uri) }

    fun addSoundFx(id: String, label: String) = run { audio.addSoundFx(projectId, id, label) }

    fun addRecording(file: File) = run {
        val n = tracks.value.count { it.kind == AudioKind.RECORD } + 1
        audio.addRecording(projectId, file, "Rekaman $n")
    }

    fun update(track: AudioTrackEntity) = run { audio.update(track) }

    fun delete(track: AudioTrackEntity) = run { audio.delete(track) }

    /** File tujuan rekaman, atau null bila penyimpanan hampir penuh. */
    fun newAudioFile(ext: String): File? {
        if (!storage.hasEnoughSpace()) {
            _messages.tryEmit("Penyimpanan hampir penuh. Hapus file lalu coba lagi.")
            return null
        }
        return storage.newAudioFile(projectId, ext)
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }
}

class AudioViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AudioViewModel(container.repository, container.audioRepository, container.storage, projectId) as T
}
