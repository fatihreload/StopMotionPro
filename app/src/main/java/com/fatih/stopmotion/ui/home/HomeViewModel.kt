package com.fatih.stopmotion.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.ProjectFilter
import com.fatih.stopmotion.data.ProjectManager
import com.fatih.stopmotion.data.ProjectNames
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectSort
import com.fatih.stopmotion.data.ProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

class HomeViewModel(
    private val repo: ProjectRepository,
    private val manager: ProjectManager
) : ViewModel() {

    /** Semua proyek (tanpa filter), dipakai untuk menentukan tampilan kosong vs hasil pencarian kosong. */
    val allProjects: StateFlow<List<ProjectSummary>> = repo.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(ProjectSort.RECENT)

    val projects: StateFlow<List<ProjectSummary>> =
        combine(repo.projects, query, sort) { list, q, s -> ProjectFilter.apply(list, q, s) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    /** Label operasi yang sedang berjalan (null = tidak ada). */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _info = MutableStateFlow<ProjectManager.Info?>(null)
    val info: StateFlow<ProjectManager.Info?> = _info.asStateFlow()

    private val _storage = MutableStateFlow<ProjectManager.StorageStats?>(null)
    val storage: StateFlow<ProjectManager.StorageStats?> = _storage.asStateFlow()

    private fun busyOp(label: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            _progress.value = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _messages.tryEmit("Memori tidak cukup")
            } catch (e: Exception) {
                _messages.tryEmit("Gagal: ${e.message}")
            } finally {
                _busy.value = null
                _progress.value = null
            }
        }
    }

    fun createProject(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createProject(name)) }
    }

    /** Menyiapkan proyek (riwayat undo baru, bersihkan file yatim) lalu membukanya. */
    fun openProject(id: Long, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            repo.prepareProject(id)
            onReady(id)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch { repo.deleteProject(id) }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            if (ProjectNames.clean(name).isEmpty()) {
                _messages.tryEmit("Nama tidak boleh kosong")
            } else repo.renameProject(id, name)
        }
    }

    fun duplicate(id: Long) = busyOp("Menduplikat proyek") {
        manager.duplicate(id) { _progress.value = it }
        _messages.tryEmit("Proyek diduplikat")
    }

    fun backup(id: Long, openOutput: () -> OutputStream?) = busyOp("Membuat cadangan") {
        val out = withContext(Dispatchers.IO) { openOutput() } ?: throw IOException("Tidak dapat menulis file")
        manager.backup(id, out) { _progress.value = it }
        _messages.tryEmit("Cadangan tersimpan")
    }

    fun restore(openInput: () -> InputStream?) = busyOp("Mengimpor cadangan") {
        val input = withContext(Dispatchers.IO) { openInput() } ?: throw IOException("Tidak dapat membuka file")
        input.use { manager.restore(it) { p -> _progress.value = p } }
        _messages.tryEmit("Proyek berhasil diimpor")
    }

    fun showInfo(id: Long) {
        viewModelScope.launch {
            runCatching { manager.info(id) }
                .onSuccess { _info.value = it }
                .onFailure { _messages.tryEmit("Gagal: ${it.message}") }
        }
    }

    fun clearInfo() {
        _info.value = null
    }

    fun loadStorage() {
        viewModelScope.launch { _storage.value = manager.storageStats() }
    }

    fun clearExports() {
        viewModelScope.launch {
            val freed = manager.clearExports()
            _storage.value = manager.storageStats()
            _messages.tryEmit("Hasil ekspor dibersihkan (${ProjectNames.formatBytes(freed)})")
        }
    }
}

class HomeViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(container.repository, container.projectManager) as T
}
