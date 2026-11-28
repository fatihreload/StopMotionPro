package com.fatih.stopmotion.ui.camera

import android.Manifest
import android.os.SystemClock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.ui.editor.ChromaPanel
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.fatih.stopmotion.ui.editor.ChromaBackground
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.AppSettings
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private fun cameraGranted(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun CameraScreen(
    projectId: Long,
    mode: String,
    target: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFrames: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val vm: CameraViewModel = viewModel(
        key = "camera_${projectId}_${mode}_$target",
        factory = CameraViewModelFactory(container, projectId, mode, target)
    )

    var hasPermission by remember { mutableStateOf(cameraGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasPermission = cameraGranted(context) }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    if (hasPermission) {
        CameraContent(vm, onBack, onOpenFrames, onOpenEditor)
    } else {
        PermissionRequest(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            },
            onBack = onBack
        )
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Izin Kamera Diperlukan", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Fatih Stop Motion Studio memakai kamera untuk mengambil frame animasi. " +
                "Foto hanya disimpan di dalam aplikasi.",
            color = FatihColors.TextSecondary
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
        ) { Text("Izinkan Kamera") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) { Text("Buka Pengaturan Aplikasi") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack) { Text("Kembali") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraContent(
    vm: CameraViewModel,
    onBack: () -> Unit,
    onOpenFrames: () -> Unit,
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val project by vm.project.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val insertPos by vm.insertPosition.collectAsStateWithLifecycle()

    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var showGrid by rememberSaveable { mutableStateOf(false) }
    /** 0 = tidak ada panel, 1 = onion skin, 2 = auto capture, 3 = kualitas foto, 4 = hapus latar, 5 = kamera manual */
    var panel by rememberSaveable { mutableIntStateOf(0) }

    var isBusy by remember { mutableStateOf(false) }
    var flashEffect by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // ---------- Penghapus latar (dipindah dari editor; menempati posisi tombol timer) ----------
    var liveKeyed by remember { mutableStateOf<Bitmap?>(null) }
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
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setBackgroundImage(uri)
    }

    // ---------- Kualitas foto dan suara shutter (berlaku untuk semua proyek) ----------
    val settings = remember { AppSettings(context) }

    // ---------- Kamera manual: fokus, AW, EV, zoom, shutter, ISO ----------
    var pro by remember { mutableStateOf(ProStateCodec.decode(settings.proPrefs)) }
    var proCaps by remember { mutableStateOf(ProCaps()) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    val proController = remember { ProCameraController() }
    // Rencana dihitung ulang dari pilihan + kemampuan kamera, dan diterapkan setiap kamera/pilihan berubah.
    LaunchedEffect(boundCamera, pro, proCaps) {
        boundCamera?.let { proController.apply(it, ProPlan.build(pro, proCaps)) }
    }
    var qualityIdx by remember { mutableIntStateOf(settings.captureQuality) }
    var shutterOn by remember { mutableStateOf(settings.shutterSound) }
    val shutter = remember { ShutterSound() }
    DisposableEffect(Unit) { onDispose { shutter.release() } }

    // ---------- Auto capture ----------
    var autoState by remember { mutableStateOf(AutoState.IDLE) }
    var autoInterval by rememberSaveable { mutableIntStateOf(2) }
    var autoCustom by rememberSaveable { mutableStateOf(false) }
    var autoTotal by rememberSaveable { mutableIntStateOf(20) }
    var autoTaken by remember { mutableIntStateOf(0) }
    var autoCountdownMs by remember { mutableLongStateOf(0L) }
    var autoJob by remember { mutableStateOf<Job?>(null) }

    // Layar tetap menyala selama sesi kamera.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // ---------- Onion skin state (disimpan per proyek) ----------
    val onionEnabled = project?.onionEnabled ?: true
    val storedOpacity = project?.onionOpacity ?: 0.5f
    val onionCount = project?.onionFrameCount ?: 1
    val onionColor = project?.onionColor ?: 0
    var opacity by remember(storedOpacity) { mutableFloatStateOf(storedOpacity) }

    fun saveOnion(
        enabled: Boolean = onionEnabled,
        op: Float = opacity,
        count: Int = onionCount,
        color: Int = onionColor
    ) = vm.setOnion(enabled, op, count, color)

    val refIndex = when (vm.mode) {
        CaptureMode.INSERT -> insertPos.coerceIn(0, frames.size)
        CaptureMode.REPLACE -> frames.indexOfFirst { it.id == vm.target }.coerceAtLeast(0)
        else -> frames.size
    }
    val refFrames = if (onionEnabled && refIndex > 0) {
        frames.subList((refIndex - onionCount).coerceAtLeast(0), refIndex).reversed()
    } else emptyList()

    /**
     * Mengambil satu foto dan menunggu sampai tersimpan. Bagian penyimpanan dilindungi
     * NonCancellable agar foto yang sedang diambil tidak hilang saat Pause/Stop ditekan.
     */
    suspend fun takeOne(onSuccess: () -> Unit = {}): Boolean {
        val ic = imageCapture ?: return false
        val file = vm.newFrameFile() ?: return false
        isBusy = true
        try {
            return withContext(NonCancellable) {
                val saved = suspendCancellableCoroutine<Boolean> { cont ->
                    if (shutterOn) shutter.play()
                    ic.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                cont.resume(true)
                            }

                            override fun onError(exc: ImageCaptureException) {
                                file.delete()
                                vm.reportError("Gagal mengambil foto: ${exc.message}")
                                cont.resume(false)
                            }
                        }
                    )
                }
                if (saved) {
                    flashEffect = true
                    vm.onPhotoSaved(file, onReplaced = onBack)
                    onSuccess()
                }
                saved
            }
        } finally {
            isBusy = false
        }
    }

    fun startAuto() {
        if (autoJob?.isActive == true || imageCapture == null) return
        if (autoState == AutoState.IDLE) autoTaken = 0
        autoState = AutoState.RUNNING
        autoJob = scope.launch {
            val intervalMs = autoInterval * 1000L
            var nextAt = SystemClock.elapsedRealtime() + intervalMs
            while (autoTaken < autoTotal) {
                while (true) { // tunggu sampai jadwal berikutnya (jadwal tetap, tidak bergeser)
                    val left = nextAt - SystemClock.elapsedRealtime()
                    if (left <= 0) break
                    autoCountdownMs = left
                    delay(minOf(left, 100L))
                }
                if (!takeOne { autoTaken++ }) {
                    autoState = AutoState.IDLE
                    return@launch
                }
                nextAt += intervalMs
            }
            autoState = AutoState.IDLE
            autoCountdownMs = 0L
            vm.reportError("Auto capture selesai: $autoTaken frame")
        }
    }

    fun pauseAuto() {
        if (autoState == AutoState.RUNNING) {
            autoJob?.cancel()
            autoState = AutoState.PAUSED
        }
    }

    fun stopAuto() {
        autoJob?.cancel()
        autoState = AutoState.IDLE
        autoTaken = 0
        autoCountdownMs = 0L
    }

    // Auto capture tidak boleh berjalan di latar belakang atau setelah halaman ditinggalkan.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { pauseAuto() }
    DisposableEffect(Unit) {
        onDispose { autoJob?.cancel() }
    }

    fun onShutter() {
        if (autoState == AutoState.RUNNING) return
        if (isBusy || imageCapture == null) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { takeOne() }
    }

    LaunchedEffect(flashEffect) {
        if (flashEffect) {
            delay(120)
            flashEffect = false
        }
    }
    val flashAlpha by animateFloatAsState(if (flashEffect) 0.7f else 0f, tween(100), label = "flash")
    val appendMode = vm.mode == CaptureMode.APPEND

    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
    ) {
        // ---------- Top bar ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(project?.name ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                val subtitle = when (vm.mode) {
                    CaptureMode.INSERT -> "Sisip di posisi ${refIndex + 1}  \u2022  ${frames.size} frame"
                    CaptureMode.REPLACE -> "Ganti foto frame ${refIndex + 1}"
                    CaptureMode.PLATE -> "Foto latar bersih: singkirkan tangan dan benda"
                    else -> "${frames.size} frame"
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = FatihColors.Blue,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }

        // ---------- Baris alat ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.size(40.dp),
                enabled = hasFlash,
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                }
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode != ImageCapture.FLASH_MODE_OFF && hasFlash) FatihColors.Purple else Color.White
                )
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = { panel = if (panel == 4) 0 else 4 }) {
                Icon(
                    Icons.Filled.Wallpaper,
                    contentDescription = "Hapus latar",
                    tint = if (panel == 4 || chroma.active) FatihColors.Purple else Color.White
                )
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = { showGrid = !showGrid }) {
                Icon(
                    if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                    contentDescription = "Grid",
                    tint = if (showGrid) FatihColors.Purple else Color.White
                )
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = { panel = if (panel == 3) 0 else 3 }) {
                // Lencana menampilkan kualitas yang sedang dipilih (SD, HD, HD+, 2K, 4K, MAX), bukan ikon HQ tetap.
                val quality = CaptureQuality.fromIndex(qualityIdx)
                val tint = if (panel == 3) FatihColors.Purple else Color.White
                Box(
                    Modifier
                        .size(width = 34.dp, height = 22.dp)
                        .border(1.5.dp, tint, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (quality == CaptureQuality.MAX) "MAX" else quality.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = { panel = if (panel == 5) 0 else 5 }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Kamera manual: fokus, AW, EV, zoom, shutter, ISO",
                    tint = if (panel == 5 || !pro.isDefault) FatihColors.Purple else Color.White
                )
            }
            IconButton(modifier = Modifier.size(40.dp), onClick = { panel = if (panel == 1) 0 else 1 }) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = "Onion skin",
                    tint = if (panel == 1 || onionEnabled) FatihColors.Purple else Color.White
                )
            }
            if (appendMode) {
                IconButton(modifier = Modifier.size(40.dp), onClick = { panel = if (panel == 2) 0 else 2 }) {
                    Icon(
                        Icons.Filled.Timelapse,
                        contentDescription = "Auto capture",
                        tint = if (panel == 2 || autoState != AutoState.IDLE) FatihColors.Purple else Color.White
                    )
                }
                IconButton(
                modifier = Modifier.size(40.dp),
                    enabled = autoState != AutoState.RUNNING,
                    onClick = onOpenEditor
                ) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = "Studio animasi (playback dan timeline)")
                }
            }
        }

        // ---------- Preview + onion skin ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                // Lapisan 1: live preview kamera
                CameraPreview(
                    lensFacing = lensFacing,
                    flashMode = flashMode,
                    captureQuality = CaptureQuality.fromIndex(qualityIdx),
                    chroma = chroma,
                    onLiveFrame = { liveKeyed = it },
                    onReady = { ic, flash ->
                        imageCapture = ic
                        hasFlash = flash
                        if (!flash) flashMode = ImageCapture.FLASH_MODE_OFF
                    },
                    onCamera = { cam ->
                        // Kamera dipasang ulang membuat pengaturan manual hilang; hitung dan terapkan lagi.
                        proController.reset()
                        proCaps = ProCameraControl.readCaps(cam)
                        boundCamera = cam
                    },
                    onError = { vm.reportError(it) },
                    modifier = Modifier.fillMaxSize()
                )
                // Lapisan 1b: chroma key langsung. Latar dihapus otomatis saat membidik, diganti latar pilihan.
                val live = liveKeyed
                if (chroma.active && live != null) {
                    ChromaBackground(chroma, Modifier.fillMaxSize())
                    val liveImage = remember(live) { live.asImageBitmap() }
                    Image(
                        bitmap = liveImage,
                        contentDescription = "Pratinjau tanpa latar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Lapisan 2: onion skin (di atas preview, di bawah kontrol)
                OnionSkinOverlay(
                    refFrames = refFrames,
                    opacity = opacity,
                    colorIndex = onionColor,
                    mirror = lensFacing == CameraSelector.LENS_FACING_FRONT,
                    modifier = Modifier.fillMaxSize()
                )
                if (showGrid) GridOverlay()
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = flashAlpha))
                )
                // Lapisan 3: indikator dan kontrol
                if (autoState != AutoState.IDLE) {
                    val secLeft = ((autoCountdownMs + 999) / 1000).toInt()
                    Text(
                        if (autoState == AutoState.PAUSED) "AUTO dijeda  $autoTaken/$autoTotal"
                        else "AUTO $autoTaken/$autoTotal  \u2022  ${secLeft}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .background(FatihColors.ShutterRed.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (panel == 1) {
                    OnionPanel(
                        enabled = onionEnabled,
                        opacity = opacity,
                        frameCount = onionCount,
                        colorIndex = onionColor,
                        onEnabledChange = { saveOnion(enabled = it) },
                        onOpacityChange = { opacity = it },
                        onOpacityFinished = { saveOnion() },
                        onFrameCountChange = { saveOnion(count = it) },
                        onColorChange = { saveOnion(color = it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                if (panel == 5) {
                    ProPanel(
                        state = pro,
                        caps = proCaps,
                        onChange = {
                            pro = it
                            settings.proPrefs = ProStateCodec.encode(it)
                        },
                        onClose = { panel = 0 },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                if (panel == 4) {
                    ChromaPanel(
                        chroma = chroma,
                        onChange = { chroma = it },
                        onCommit = { vm.setChroma(chroma) },
                        onPickBackground = { pickBackground.launch(arrayOf("image/*")) },
                        onClearBackground = { vm.clearBackgroundImage() },
                        onClose = { panel = 0 },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                if (panel == 3) {
                    CapturePanel(
                        qualityIndex = qualityIdx,
                        shutterOn = shutterOn,
                        enabled = autoState != AutoState.RUNNING && !isBusy,
                        onQualityChange = {
                            if (it != qualityIdx) {
                                qualityIdx = it
                                settings.captureQuality = it
                                imageCapture = null // kamera dibuka ulang dengan ukuran baru
                            }
                        },
                        onShutterChange = {
                            shutterOn = it
                            settings.shutterSound = it
                            if (it) shutter.play() // contoh bunyi saat dinyalakan
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                if (panel == 2 && appendMode) {
                    AutoCapturePanel(
                        state = autoState,
                        intervalSec = autoInterval,
                        custom = autoCustom,
                        total = autoTotal,
                        taken = autoTaken,
                        onPreset = { autoInterval = it; autoCustom = false },
                        onCustom = { autoCustom = true },
                        onIntervalChange = { autoInterval = it },
                        onTotalChange = { autoTotal = it },
                        onStart = ::startAuto,
                        onPause = ::pauseAuto,
                        onStop = ::stopAuto,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        if (frames.isNotEmpty()) {
            FrameStrip(frames)
        }

        // ---------- Bottom controls ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LastFrameThumb(
                frame = frames.lastOrNull(),
                count = frames.size,
                onClick = if (appendMode) onOpenFrames else onBack
            )
            ShutterButton(
                enabled = imageCapture != null && autoState != AutoState.RUNNING && !isBusy,
                onClick = ::onShutter
            )
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(FatihColors.SurfaceHigh)
                    .clickable(enabled = !isBusy && autoState != AutoState.RUNNING) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Ganti kamera")
            }
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
    Box(
        Modifier
            .size(84.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .background(
                if (enabled) FatihColors.ShutterRed else FatihColors.ShutterRed.copy(alpha = 0.4f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {}
}

/** Thumbnail frame terakhir; ketuk untuk membuka manajemen frame. */
@Composable
private fun LastFrameThumb(frame: FrameEntity?, count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .border(1.5.dp, if (frame != null) FatihColors.Purple else FatihColors.SurfaceHigh, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            AsyncImage(
                model = File(frame.thumbPath),
                contentDescription = "Buka daftar frame",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topStart = 8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Daftar frame", tint = FatihColors.TextSecondary)
        }
    }
}

@Composable
private fun FrameStrip(frames: List<FrameEntity>) {
    val listState = rememberLazyListState()
    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty()) listState.animateScrollToItem(frames.lastIndex)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(frames, key = { it.id }) { frame ->
            val isLast = frame.id == frames.last().id
            AsyncImage(
                model = File(frame.thumbPath),
                contentDescription = "Frame ${frame.position + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 42.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        if (isLast) 2.dp else 0.dp,
                        if (isLast) FatihColors.Purple else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
            )
        }
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.45f)
        val w = size.width
        val h = size.height
        for (i in 1..2) {
            drawLine(c, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeWidth = 1.5f)
            drawLine(c, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1.5f)
        }
    }
}
