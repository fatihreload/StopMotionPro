package com.fatih.stopmotion.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.AudioRepository
import com.fatih.stopmotion.data.AudioTrackEntity
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.data.LayerRepository
import com.fatih.stopmotion.data.ProjectEntity
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import com.fatih.stopmotion.export.EncoderSupport
import com.fatih.stopmotion.export.ExportFormat
import com.fatih.stopmotion.export.ExportSettings
import com.fatih.stopmotion.export.FrameComposer
import com.fatih.stopmotion.export.GifExporter
import com.fatih.stopmotion.export.Mp4Exporter
import com.fatih.stopmotion.export.ZipExporter
import com.fatih.stopmotion.export.computeOutputSize
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.FxState
import com.fatih.stopmotion.fx.ImageFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val label: String) : ExportState
    data class Done(val file: File, val name: String, val mime: String, val sizeBytes: Long) : ExportState
    data class Error(val message: String) : ExportState
}

class ExportViewModel(
    private val repo: ProjectRepository,
    private val audioRepo: AudioRepository,
    private val layerRepo: LayerRepository,
    private val storage: ProjectStorage,
    private val projectId: Long
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val frames: StateFlow<List<FrameEntity>> = repo.observeFrames(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<AudioTrackEntity>> = audioRepo.observe(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private var job: Job? = null

    fun dismissResult() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }

    fun cancel() {
        job?.cancel()
    }

    fun start(settings: ExportSettings) {
        if (_state.value is ExportState.Running) return
        job = viewModelScope.launch {
            _state.value = ExportState.Running(0f, "Menyiapkan")
            var out: File? = null
            var composer: FrameComposer? = null
            try {
                val p = repo.observeProject(projectId).first() ?: throw IOException("Proyek tidak ditemukan")
                val frameList = repo.getFrames(projectId)
                if (frameList.isEmpty()) throw IOException("Belum ada frame untuk diekspor")
                if (!storage.hasEnoughSpace(200L * 1024 * 1024)) throw IOException("Penyimpanan hampir penuh")
                val tracksNow = audioRepo.observe(projectId).first().filter { !it.muted }

                val dir = storage.exportsDir(projectId)
                dir.listFiles()?.forEach { it.delete() } // hasil ekspor lama dibuang
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val safeName = p.name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "animasi" }
                val name = "${safeName}_$stamp.${settings.format.ext}"
                val file = File(dir, name)
                out = file
                val fps = p.fps.coerceIn(1, 60)
                val fx = FxState(p.fxFilter, p.fxIntensity, p.fxVignette, p.fxFadeIn, p.fxFadeOut, p.fxFadeWhite)

                withContext(Dispatchers.IO) {
                    val cancelled = { !isActive }
                    val onProgress: (Float, String) -> Unit = { f, label ->
                        _state.value = ExportState.Running(f.coerceIn(0f, 1f), label)
                    }
                    if (settings.format == ExportFormat.ZIP) {
                        ZipExporter.export(frameList, file, cancelled, onProgress)
                    } else {
                        val first = ImageFx.decode(frameList.first().filePath, 64)
                            ?: throw IOException("Foto pertama tidak dapat dibaca")
                        val align = if (settings.format == ExportFormat.MP4) 16 else 2
                        val (w, h) = computeOutputSize(first.width, first.height, settings.shortSide, align)
                        first.recycle()
                        if (settings.format == ExportFormat.MP4 && !EncoderSupport.supportsAvc(w, h, fps)) {
                            throw IOException("Perangkat ini tidak mendukung video ${w}x$h. Pilih ukuran yang lebih kecil.")
                        }
                        val chroma = ChromaState(
                            p.chromaMode, p.chromaTolerance, p.chromaSoftness, p.chromaBgMode, p.chromaBgPath
                        )
                        val layerList = layerRepo.observe(projectId).first()
                        val c = FrameComposer(frameList, fx, w, h, chroma, layerList, fps)
                        composer = c
                        if (settings.format == ExportFormat.MP4) {
                            Mp4Exporter.export(
                                c, fps, settings.quality,
                                if (settings.includeAudio) tracksNow else emptyList(),
                                file, File(dir, "tmp"), cancelled, onProgress
                            )
                        } else {
                            GifExporter.export(c, fps, settings.loop, file, cancelled, onProgress)
                        }
                    }
                }
                File(dir, "tmp").deleteRecursively()
                _state.value = ExportState.Done(file, name, settings.format.mime, file.length())
            } catch (e: CancellationException) {
                out?.delete()
                runCatching { File(storage.exportsDir(projectId), "tmp").deleteRecursively() }
                _state.value = ExportState.Idle
                throw e
            } catch (e: OutOfMemoryError) {
                out?.delete()
                _state.value = ExportState.Error("Memori tidak cukup. Coba resolusi yang lebih kecil.")
            } catch (e: Exception) {
                out?.delete()
                runCatching { File(storage.exportsDir(projectId), "tmp").deleteRecursively() }
                _state.value = ExportState.Error(e.message ?: "Ekspor gagal")
            } finally {
                composer?.release()
            }
        }
    }
}

class ExportViewModelFactory(
    private val container: AppContainer,
    private val projectId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExportViewModel(container.repository, container.audioRepository, container.layerRepository, container.storage, projectId) as T
}
