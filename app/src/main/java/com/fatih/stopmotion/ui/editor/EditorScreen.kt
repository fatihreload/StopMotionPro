package com.fatih.stopmotion.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.fx.LayerTiming
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.audio.AudioEngine
import com.fatih.stopmotion.data.FrameBitmapCache
import com.fatih.stopmotion.data.KeyedBitmapCache
import com.fatih.stopmotion.fx.ChromaBg
import com.fatih.stopmotion.fx.ChromaKey
import com.fatih.stopmotion.fx.EraseCodec
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.fx.FxState
import com.fatih.stopmotion.fx.ImageFx
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.fatih.stopmotion.fx.LayerFiles
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

object PlayMode {
    const val ONCE = 0
    const val LOOP = 1
    const val PINGPONG = 2
}

private fun formatTime(seconds: Float): String {
    val total = (seconds * 100).roundToInt()
    val m = total / 6000
    val s = (total / 100) % 60
    val c = total % 100
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, c)
}

private val ToolPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onEditImage: (frameId: Long) -> Unit,
    onErase: (frameId: Long) -> Unit,
    onOpenAudio: () -> Unit,
    onExport: () -> Unit
) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel(
        key = "editor_$projectId",
        factory = EditorViewModelFactory(container, projectId)
    )
    val cache = container.bitmapCache

    val project by vm.project.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val selectedRaw by vm.selected.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val audioTracks by vm.audio.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // ---- Urutan tick: tiap frame tampil sebanyak holdCount tick ----
    val tickToFrame = remember(frames) {
        val out = ArrayList<Int>()
        frames.forEachIndexed { i, f -> repeat(f.holdCount.coerceAtLeast(1)) { out.add(i) } }
        out.toIntArray()
    }
    val tickStart = remember(frames) {
        val out = IntArray(frames.size)
        var t = 0
        frames.forEachIndexed { i, f -> out[i] = t; t += f.holdCount.coerceAtLeast(1) }
        out
    }
    val n = tickToFrame.size

    val storedFps = project?.fps ?: 12
    var fps by remember(storedFps) { mutableIntStateOf(storedFps) }
    var speed by remember { mutableFloatStateOf(1f) }
    var playMode by rememberSaveable { mutableIntStateOf(PlayMode.LOOP) }
    var playing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var selectMode by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }

    // ---- Efek proyek (pratinjau langsung, disimpan saat jari dilepas) ----
    val storedFx = remember(
        project?.fxFilter, project?.fxIntensity, project?.fxVignette,
        project?.fxFadeIn, project?.fxFadeOut, project?.fxFadeWhite
    ) {
        FxState(
            filter = project?.fxFilter ?: 0,
            intensity = project?.fxIntensity ?: 1f,
            vignette = project?.fxVignette ?: 0f,
            fadeIn = project?.fxFadeIn ?: 0,
            fadeOut = project?.fxFadeOut ?: 0,
            fadeWhite = project?.fxFadeWhite ?: false
        )
    }
    var fx by remember(storedFx) { mutableStateOf(storedFx) }

    // ---- Penghapus latar (green/blue screen): bisa diatur dari kamera maupun dari sini ----
    val storedChroma = remember(
        project?.chromaMode, project?.chromaTolerance, project?.chromaSoftness,
        project?.chromaBgMode, project?.chromaBgPath
    ) {
        ChromaState(
            mode = project?.chromaMode ?: 0,
            tolerance = project?.chromaTolerance ?: 0.4f,
            softness = project?.chromaSoftness ?: 0.3f,
            bgMode = project?.chromaBgMode ?: 0,
            bgPath = project?.chromaBgPath
        )
    }
    var chroma by remember(storedChroma) { mutableStateOf(storedChroma) }
    var showChroma by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setBackgroundImage(uri)
    }
    val keyedCache = remember { KeyedBitmapCache() }
    DisposableEffect(Unit) { onDispose { keyedCache.clear() } }
    val fxFilter = remember(fx.filter, fx.intensity) {
        ImageFx.colorArray(0f, 1f, 1f, 0f, fx.filter, fx.intensity)
            ?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
    }
    var itemDp by rememberSaveable { mutableFloatStateOf(60f) }

    val safeTick = if (n == 0) 0 else tick.coerceIn(0, n - 1)
    val currentIndex = if (n == 0) -1 else tickToFrame[safeTick]
    val currentFrame = frames.getOrNull(currentIndex)

    val frameIds = remember(frames) { frames.map { it.id }.toSet() }
    val selected = remember(selectedRaw, frameIds) { selectedRaw.intersect(frameIds) }

    val listState = rememberLazyListState()

    // ---- Audio: track diputar mengikuti posisi timeline (tick / fps) ----
    val engine = remember { AudioEngine() }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    LaunchedEffect(audioTracks) { withContext(Dispatchers.IO) { engine.setTracks(audioTracks) } }
    val timeMsNow by rememberUpdatedState((safeTick * 1000L) / fps.coerceAtLeast(1))

    // ---- Layer tambahan (gambar/klip video di atas foto) ----
    val layers by vm.layers.collectAsStateWithLifecycle()
    val layerBusy by vm.layerBusy.collectAsStateWithLifecycle()
    var showLayers by remember { mutableStateOf(false) }
    val panelOpen = showLayers || showFx || showChroma || showAudio // panel bawah terbuka: sembunyikan alat dan timeline agar pratinjau luas
    var showAddLayer by remember { mutableStateOf(false) }
    var selectedLayerId by remember { mutableStateOf<Long?>(null) }
    var liveLayer by remember { mutableStateOf<LayerEntity?>(null) }
    var layerDragging by remember { mutableStateOf(false) }
    var pendingVideoChroma by remember { mutableIntStateOf(0) }
    val layerCache = remember { KeyedBitmapCache() }
    DisposableEffect(Unit) { onDispose { layerCache.clear() } }
    // Nilai "live" (saat slider/gesture berjalan) dibuang setelah database mengirim nilai tersimpan.
    LaunchedEffect(layers) { if (!layerDragging) liveLayer = null }
    LaunchedEffect(layers) {
        if (layers.none { it.id == selectedLayerId }) selectedLayerId = layers.lastOrNull()?.id
    }
    val shownLayers = run {
        val live = liveLayer
        if (live == null) layers else layers.map { if (it.id == live.id) live else it }
    }
    val selectedLayer = shownLayers.firstOrNull { it.id == selectedLayerId }
    val pickLayerImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addImageLayer(uri, timeMsNow, 2000L) { selectedLayerId = it }
    }
    val pickLayerVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addVideoLayer(uri, timeMsNow, fps, pendingVideoChroma) { selectedLayerId = it }
    }
    LaunchedEffect(playing, speed, audioTracks) {
        if (!playing) {
            engine.update(timeMsNow, false, speed)
            return@LaunchedEffect
        }
        while (isActive) {
            engine.update(timeMsNow, true, speed)
            delay(40)
        }
    }
    // Berhenti otomatis (video dan audio) bila aplikasi ke latar belakang.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { playing = false }

    // ---- Mesin playback: waktu diambil dari jam frame layar (vsync), bukan delay berulang ----
    LaunchedEffect(playing, fps, speed, playMode, n) {
        if (!playing || n == 0) return@LaunchedEffect
        val effectiveFps = fps * speed
        val base = tick.coerceIn(0, n - 1).toLong()
        var startNanos = -1L
        while (true) {
            withFrameNanos<Unit> { now ->
                if (startNanos < 0) startNanos = now
                val advanced = ((now - startNanos) / 1_000_000_000.0 * effectiveFps).toLong()
                val t = base + advanced
                when (playMode) {
                    PlayMode.ONCE -> {
                        if (t >= n - 1) {
                            tick = n - 1
                            playing = false
                        } else tick = t.toInt()
                    }
                    PlayMode.LOOP -> tick = (t % n).toInt()
                    else -> {
                        if (n <= 1) tick = 0 else {
                            val period = 2L * (n - 1)
                            val m = (t % period).toInt()
                            tick = if (m < n) m else (period - m).toInt()
                        }
                    }
                }
            }
        }
    }

    // ---- Prefetch ke depan (paralel): foto, hasil chroma/erase, dan frame tiap layer, supaya playback tidak patah ----
    val currentTickNow by rememberUpdatedState(safeTick)
    val latestLayers by rememberUpdatedState(shownLayers)
    val latestChroma by rememberUpdatedState(chroma)
    val latestFps by rememberUpdatedState(fps)
    LaunchedEffect(frames, playing, speed, fps) {
        if (!playing || n == 0) return@LaunchedEffect
        val gate = Semaphore(3)
        val ahead = (fps * speed * 1.2f).roundToInt()
            .coerceIn(8, 36)
            .coerceAtMost((cache.capacityFrames() - 4).coerceAtLeast(4))
        while (isActive) {
            val cur = currentTickNow
            val ch = latestChroma
            val lys = latestLayers
            coroutineScope {
                for (k in 1..ahead) {
                    val t = (cur + k) % n
                    val frame = frames[tickToFrame[t]]
                    val path = frame.filePath
                    if (cache.get(path) == null) launch { gate.withPermit { cache.load(path) } }
                    if (ch.active || !frame.eraseData.isNullOrEmpty()) {
                        val key = "$path|${ch.cacheKey()}|${frame.eraseData?.hashCode() ?: 0}"
                        if (keyedCache.get(key) == null) launch {
                            gate.withPermit {
                                val src = cache.load(path) ?: return@withPermit
                                val out = withContext(Dispatchers.Default) {
                                    ChromaKey.process(src, ch, EraseCodec.decode(frame.eraseData), 960)
                                }
                                keyedCache.put(key, out)
                            }
                        }
                    }
                    if (k <= 20 && lys.isNotEmpty()) {
                        val ms = (t * 1000L) / latestFps.coerceAtLeast(1)
                        for (l in lys) {
                            val idx = LayerTiming.frameIndex(l, ms) ?: continue
                            val lp = LayerFiles.framePath(l, idx) ?: continue
                            val lk = layerCacheKey(l, lp)
                            if (layerCache.get(lk) == null) launch {
                                gate.withPermit {
                                    val bmp = withContext(Dispatchers.Default) { decodeLayerBitmap(l, lp) }
                                    if (bmp != null) layerCache.put(lk, bmp)
                                }
                            }
                        }
                    }
                }
            }
            delay(20)
        }
    }

    // ---- Playhead di timeline mengikuti frame saat ini ----
    LaunchedEffect(currentIndex, playing) {
        if (currentIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val inView = visible.isNotEmpty() &&
            currentIndex >= visible.first().index && currentIndex <= visible.last().index
        if (!inView) {
            if (playing) listState.scrollToItem(currentIndex)
            else listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
    ) {
        // ---------- Top bar ----------
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(project?.name ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Studio Animasi", fontSize = 12.sp, color = FatihColors.Blue)
            }
            IconButton(onClick = { vm.undo() }, enabled = history.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { vm.redo() }, enabled = history.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = { playing = false; onExport() }, enabled = frames.isNotEmpty()) {
                Icon(Icons.Filled.Movie, contentDescription = "Ekspor", tint = FatihColors.Blue)
            }
        }

        // ---------- Preview ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (n == 0) {
                Text("Belum ada frame. Ambil foto di kamera dulu.", color = FatihColors.TextSecondary)
            } else {
                Box(
                    Modifier
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    if (chroma.active || !currentFrame?.eraseData.isNullOrEmpty()) {
                        ChromaBackground(chroma, Modifier.fillMaxSize())
                        KeyedFramePreview(
                            currentFrame?.filePath, currentFrame?.eraseData, cache, keyedCache, chroma,
                            Modifier.fillMaxSize(), fxFilter
                        )
                    } else {
                        FramePreview(currentFrame?.filePath, cache, Modifier.fillMaxSize(), fxFilter)
                    }
                    LayerPreview(
                        layers = shownLayers,
                        timeMs = timeMsNow,
                        selectedId = if (showLayers) selectedLayerId else null,
                        cache = layerCache,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (showLayers && selectedLayer != null) {
                        LayerGestureOverlay(
                            layer = selectedLayer,
                            timeMs = timeMsNow,
                            onChange = { liveLayer = it },
                            onDragging = { layerDragging = it },
                            onCommit = { vm.updateLayer(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (fx.vignette > 0.01f) {
                        val strength = fx.vignette
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                val radius = hypot(size.width, size.height) / 2f
                                drawRect(
                                    Brush.radialGradient(
                                        0.45f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = strength),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = radius
                                    )
                                )
                            }
                        )
                    }
                    val fadeAlpha = run {
                        var a = 0f
                        if (fx.fadeIn > 0 && safeTick < fx.fadeIn) a = maxOf(a, 1f - safeTick / fx.fadeIn.toFloat())
                        val left = n - 1 - safeTick
                        if (fx.fadeOut > 0 && left < fx.fadeOut) a = maxOf(a, 1f - left / fx.fadeOut.toFloat())
                        a.coerceIn(0f, 1f)
                    }
                    if (fadeAlpha > 0f) {
                        Box(
                            Modifier.fillMaxSize().background(
                                (if (fx.fadeWhite) Color.White else Color.Black).copy(alpha = fadeAlpha)
                            )
                        )
                    }
                }
            }
        }

        // ---------- Info + kontrol putar (satu baris ringkas: Stop, Play, slider, mode putar) ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Frame ${if (currentIndex >= 0) currentIndex + 1 else 0} / ${frames.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${formatTime(safeTick / fps.toFloat())} / ${formatTime(n / fps.toFloat())}",
                fontSize = 12.sp,
                color = FatihColors.Blue
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playing = false; tick = 0 }, enabled = n > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (n > 0) FatihColors.ShutterRed else FatihColors.SurfaceHigh)
                    .clickable(enabled = n > 0) {
                        if (playing) playing = false else {
                            if (playMode == PlayMode.ONCE && safeTick >= n - 1) tick = 0
                            playing = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Slider(
                value = safeTick.toFloat(),
                onValueChange = { playing = false; tick = it.roundToInt() },
                valueRange = 0f..(n - 1).coerceAtLeast(1).toFloat(),
                enabled = n > 1,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp).height(28.dp)
            )
            IconButton(
                onClick = {
                    playMode = when (playMode) {
                        PlayMode.ONCE -> PlayMode.LOOP
                        PlayMode.LOOP -> PlayMode.PINGPONG
                        else -> PlayMode.ONCE
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    when (playMode) {
                        PlayMode.ONCE -> Icons.Filled.RepeatOne
                        PlayMode.LOOP -> Icons.Filled.Repeat
                        else -> Icons.Filled.SwapHoriz
                    },
                    contentDescription = when (playMode) {
                        PlayMode.ONCE -> "Mode putar: sekali"
                        PlayMode.LOOP -> "Mode putar: loop"
                        else -> "Mode putar: ping-pong"
                    },
                    tint = FatihColors.Purple
                )
            }
        }

        // ---------- Alat: efek, audio, edit foto (disembunyikan saat panel layer terbuka agar pratinjau lebih luas) ----------
        if (!panelOpen) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = { showChroma = !showChroma; if (showChroma) { showLayers = false; showFx = false; showAudio = false } }, contentPadding = ToolPadding) {
                Icon(
                    Icons.Filled.Wallpaper,
                    contentDescription = null,
                    tint = if (showChroma || chroma.active) FatihColors.Purple else Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text("Chroma")
            }
            TextButton(
                onClick = { showLayers = !showLayers; if (showLayers) { showChroma = false; showFx = false; showAudio = false } },
                contentPadding = ToolPadding
            ) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = null,
                    tint = if (showLayers || layers.isNotEmpty()) FatihColors.Purple else Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text(if (layers.isEmpty()) "Layer" else "Layer (${layers.size})")
            }
            TextButton(onClick = { showFx = true; showLayers = false; showChroma = false; showAudio = false }, contentPadding = ToolPadding) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = if (fx.isDefault) Color.White else FatihColors.Purple)
                Spacer(Modifier.width(4.dp))
                Text("Efek film")
            }
            TextButton(onClick = { playing = false; showAudio = true; showLayers = false; showChroma = false; showFx = false }, contentPadding = ToolPadding) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = if (audioTracks.isEmpty()) Color.White else FatihColors.Purple)
                Spacer(Modifier.width(4.dp))
                Text(if (audioTracks.isEmpty()) "Audio" else "Audio (${audioTracks.size})")
            }
            TextButton(
                onClick = { currentFrame?.let { playing = false; onEditImage(it.id) } },
                enabled = currentFrame != null,
                contentPadding = ToolPadding
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Edit foto")
            }
            TextButton(
                onClick = { currentFrame?.let { playing = false; onErase(it.id) } },
                enabled = currentFrame != null,
                contentPadding = ToolPadding
            ) {
                Icon(
                    Icons.Filled.Brush,
                    contentDescription = null,
                    tint = if (currentFrame?.eraseData.isNullOrEmpty()) Color.White else FatihColors.Purple
                )
                Spacer(Modifier.width(4.dp))
                Text("Erase")
            }
        }

        // ---------- FPS & kecepatan (diganti panel layer saat panel layer terbuka) ----------
        if (showLayers) {
            LayerPanel(
                layers = shownLayers,
                selectedId = selectedLayerId,
                busy = layerBusy,
                filmMs = LayerTiming.filmMs(n, fps),
                currentMs = timeMsNow,
                onSeek = { ms ->
                    if (n > 0) {
                        playing = false
                        tick = Math.round(ms * fps / 1000.0).toInt().coerceIn(0, n - 1)
                    }
                },
                onSelect = { id ->
                    selectedLayerId = id
                    // Bila layer tidak tampil di posisi sekarang, lompat ke awalnya agar bisa diatur di pratinjau.
                    layers.firstOrNull { it.id == id }?.let { l ->
                        if (LayerTiming.frameIndex(l, timeMsNow) == null && n > 0) {
                            playing = false
                            tick = ((l.startMs * fps) / 1000).toInt().coerceIn(0, n - 1)
                        }
                    }
                },
                onChange = { liveLayer = it },
                onCommit = { liveLayer?.let { vm.updateLayer(it) } },
                onAdd = { showAddLayer = true },
                onDelete = { vm.deleteLayer(it) },
                onMove = { id, up -> vm.moveLayer(id, up) },
                onCancelBusy = { vm.cancelLayerJob() },
                onClose = { showLayers = false }
            )
        } else if (showFx) {
            EffectsSheet(
                fx = fx,
                fps = fps,
                onChange = { fx = it },
                onCommit = { vm.setEffects(fx) },
                onClose = { showFx = false }
            )
        } else if (showChroma) {
            ChromaPanel(
                chroma = chroma,
                onChange = { chroma = it },
                onCommit = { vm.setChroma(chroma) },
                onPickBackground = { pickBackground.launch(arrayOf("image/*")) },
                onClearBackground = { vm.clearBackgroundImage() },
                onClose = { showChroma = false }
            )
        } else if (showAudio) {
            com.fatih.stopmotion.ui.audio.AudioScreen(
                projectId = projectId,
                container = container,
                onBack = { showAudio = false },
                inline = true
            )
        } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("FPS $fps", fontSize = 12.sp, modifier = Modifier.width(84.dp))
            Slider(
                value = fps.toFloat(),
                onValueChange = { fps = it.roundToInt() },
                onValueChangeFinished = { vm.setFps(fps) },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.height(28.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kecepatan " + String.format(Locale.US, "%.2fx", speed),
                fontSize = 12.sp,
                modifier = Modifier.width(84.dp)
            )
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.25f..2f,
                modifier = Modifier.height(28.dp)
            )
        }
        }

        // ---------- Timeline (disembunyikan saat panel layer terbuka; waktu diatur lewat jalur di panel layer) ----------
        if (!panelOpen) Column(Modifier.fillMaxWidth().background(FatihColors.Surface)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Timeline", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    selectMode = !selectMode
                    if (!selectMode) vm.clearSelection()
                }) { Text(if (selectMode) "Selesai (${selected.size})" else "Pilih") }
                IconButton(onClick = { vm.duplicateSelected(selected) }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Duplikat")
                }
                IconButton(onClick = { vm.deleteSelected(selected) }, enabled = selected.isNotEmpty()) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                }
            }
            if (frames.isNotEmpty()) {
                Timeline(
                    frames = frames,
                    currentIndex = currentIndex,
                    selected = selected,
                    itemWidth = itemDp.dp,
                    listState = listState,
                    onTap = { idx ->
                        playing = false
                        tick = tickStart[idx]
                        if (selectMode) vm.toggle(frames[idx].id)
                    },
                    onMove = { id, to -> vm.move(id, to) }
                )
            } else {
                Spacer(Modifier.height(80.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                Slider(
                    value = itemDp,
                    onValueChange = { itemDp = it },
                    valueRange = 30f..100f,
                    modifier = Modifier.height(28.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddLayer) {
        AlertDialog(
            onDismissRequest = { showAddLayer = false },
            title = { Text("Tambah layer") },
            text = {
                Column {
                    Text(
                        "Layer tampil di atas foto pada waktu yang Anda atur. Mulai dari posisi film sekarang.",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showAddLayer = false; pickLayerImage.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Gambar atau PNG transparan") }
                    Spacer(Modifier.height(14.dp))
                    Text("Klip video (mis. efek peluru berlatar hijau)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniChip("Tanpa chroma", null, pendingVideoChroma == 0) { pendingVideoChroma = 0 }
                        MiniChip("Hijau", Color(0xFF1DB954), pendingVideoChroma == 1) { pendingVideoChroma = 1 }
                        MiniChip("Biru", Color(0xFF2F6FED), pendingVideoChroma == 2) { pendingVideoChroma = 2 }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showAddLayer = false; pickLayerVideo.launch(arrayOf("video/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pilih klip video") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Klip diubah menjadi frame pada FPS proyek (maks. 240 frame). Suara klip tidak ikut.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddLayer = false }) { Text("Tutup") } }
        )
    }
}

/** Menampilkan frame dari cache; bila belum siap, tetap menampilkan frame sebelumnya (tanpa kedip). */
@Composable
private fun FramePreview(path: String?, cache: FrameBitmapCache, modifier: Modifier, colorFilter: ColorFilter? = null) {
    var last by remember { mutableStateOf<Bitmap?>(null) }
    val cached = path?.let { cache.get(it) }
    LaunchedEffect(path) {
        if (path != null) cache.load(path)?.let { last = it }
    }
    val bmp = cached ?: last
    if (bmp != null) {
        val image = remember(bmp) { bmp.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = "Frame saat ini",
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = modifier
        )
    }
}

/** Pratinjau frame setelah latar dihapus dan coretan erase diterapkan (hasil di-cache; frame sebelumnya tetap tampil selama diproses). */
@Composable
private fun KeyedFramePreview(
    path: String?,
    eraseData: String?,
    cache: FrameBitmapCache,
    keyed: KeyedBitmapCache,
    chroma: ChromaState,
    modifier: Modifier,
    colorFilter: ColorFilter?
) {
    var last by remember { mutableStateOf<Bitmap?>(null) }
    val key = path?.let { "$it|${chroma.cacheKey()}|${eraseData?.hashCode() ?: 0}" }
    val cached = key?.let { keyed.get(it) }
    LaunchedEffect(key) {
        if (path == null || key == null) return@LaunchedEffect
        val hit = keyed.get(key)
        if (hit != null) {
            last = hit
            return@LaunchedEffect
        }
        val src = cache.load(path) ?: return@LaunchedEffect
        val out = withContext(Dispatchers.Default) {
            ChromaKey.process(src, chroma, EraseCodec.decode(eraseData), 960)
        }
        keyed.put(key, out)
        last = out
    }
    val bmp = cached ?: last
    if (bmp != null) {
        val image = remember(bmp) { bmp.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = "Frame tanpa latar",
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = modifier
        )
    }
}

@Composable
fun ChromaBackground(chroma: ChromaState, modifier: Modifier) {
    val path = chroma.bgPath
    val useImage = chroma.bgMode == ChromaBg.IMAGE && path != null
    val bg by produceState<Bitmap?>(initialValue = null, path, useImage) {
        value = if (useImage && path != null) withContext(Dispatchers.IO) { ImageFx.decode(path, 960) } else null
    }
    val b = bg
    if (useImage && b != null) {
        val image = remember(b) { b.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = "Latar pengganti",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier.background(Color(ChromaBg.color(chroma.bgMode))))
    }
}
