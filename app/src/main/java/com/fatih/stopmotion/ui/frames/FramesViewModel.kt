package com.fatih.stopmotion.ui.frames

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectRepository
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

class FramesViewModel(
    private val repo: ProjectRepository,
    private val projectId: Long
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history = repo.observeHistory(projectId)

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    private val _focused = MutableStateFlow<Long?>(null)
    val focused: StateFlow<Long?> = _focused.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val mutex = Mutex()

    fun toggle(id: Long) {
        _selected.update { if (id in it) it - id else it + id }
        _focused.value = id
    }

    fun selectAll(ids: List<Long>) {
        _selected.value = ids.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

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

    fun moveSelected(ids: Set<Long>, delta: Int) = op { repo.moveFrames(projectId, ids, delta) }

    fun setHold(ids: Set<Long>, hold: Int) = op { repo.setHold(projectId, ids, hold) }

    fun undo() = op { repo.undo(projectId) }

    fun redo() = op { repo.redo(projectId) }
}

class FramesViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        FramesViewModel(container.repository, projectId) as T
}
