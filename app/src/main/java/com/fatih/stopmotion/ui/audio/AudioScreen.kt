package com.fatih.stopmotion.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.audio.SoundFx
import com.fatih.stopmotion.audio.VoiceRecorder
import com.fatih.stopmotion.audio.trackGain
import com.fatih.stopmotion.data.AudioKind
import com.fatih.stopmotion.data.AudioTrackEntity
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_RECORD_MS = 5 * 60_000L

private fun fmt(sec: Float): String {
    val t = (sec * 10).roundToInt()
    return String.format(Locale.US, "%d:%02d.%d", t / 600, (t / 10) % 60, t % 10)
}

/** Pratinjau satu track (bagian yang dipotong, lengkap dengan fade) di halaman Studio Audio. */
private class TrackPreviewer {
    var playingId by mutableStateOf<Long?>(null)
    private var player: MediaPlayer? = null
    private var track: AudioTrackEntity? = null

    fun play(t: AudioTrackEntity) {
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(t.filePath)
            p.setOnPreparedListener {
                it.seekTo(t.trimStartMs.toInt())
                it.start()
            }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepareAsync()
            player = p
            track = t
            playingId = t.id
        } catch (e: Exception) {
            stop()
        }
    }

    fun tick() {
        val p = player ?: return
        val t = track ?: return
        try {
            if (!p.isPlaying) return
            val pos = p.currentPosition
            if (pos >= t.trimEndMs) {
                stop()
                return
            }
            val g = trackGain(t, (pos - t.trimStartMs).toLong())
            p.setVolume(g, g)
        } catch (e: IllegalStateException) {
            stop()
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        track = null
        playingId = null
    }
}

@Composable
fun AudioScreen(
    projectId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    inline: Boolean = false
) {
    val context = LocalContext.current
    val vm: AudioViewModel = viewModel(
        key = "audio_$projectId",
        factory = AudioViewModelFactory(container, projectId)
    )
    val project by vm.project.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()

    val fps = (project?.fps ?: 12).coerceAtLeast(1)
    val ticks = frames.sumOf { it.holdCount.coerceAtLeast(1) }
    val filmSec = ticks / fps.toFloat()

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // ---------- Pratinjau ----------
    val previewer = remember { TrackPreviewer() }
    DisposableEffect(Unit) { onDispose { previewer.stop() } }
    LaunchedEffect(previewer.playingId) {
        while (previewer.playingId != null) {
            previewer.tick()
            delay(60)
        }
    }

    // ---------- Rekam suara ----------
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    var recLevel by remember { mutableFloatStateOf(0f) }

    fun finishRecording(save: Boolean) {
        if (!recording) return
        recording = false
        val file = recorder.stop()
        if (file == null) {
            if (save) vm.toast("Rekaman terlalu singkat atau gagal")
        } else if (save) vm.addRecording(file) else file.delete()
    }

    fun startRecording() {
        previewer.stop()
        val dest = vm.newAudioFile("m4a") ?: return
        if (recorder.start(dest)) {
            recMs = 0L
            recording = true
        } else vm.toast("Tidak dapat memulai rekaman")
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else vm.toast("Izin mikrofon diperlukan untuk merekam suara")
    }

    fun onRecordClick() {
        if (recording) {
            finishRecording(true)
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recording) {
        val t0 = SystemClock.elapsedRealtime()
        while (recording) {
            recMs = SystemClock.elapsedRealtime() - t0
            recLevel = (recorder.level / 20000f).coerceIn(0f, 1f)
            if (recMs >= MAX_RECORD_MS) {
                finishRecording(true)
                break
            }
            delay(100)
        }
        recLevel = 0f
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        finishRecording(true)
        previewer.stop()
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    // ---------- Impor & efek suara ----------
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }
    var showSfx by rememberSaveable { mutableStateOf(false) }

    // inline = dipakai sebagai panel di bawah pratinjau Studio Animasi (tanpa pindah layar).
    val rootModifier = if (inline) {
        Modifier
            .fillMaxWidth()
            .background(FatihColors.Surface)
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.40f).dp)
    } else {
        Modifier.fillMaxSize().background(FatihColors.Black).systemBarsPadding()
    }
    Column(rootModifier) {
        val listWeight = if (inline) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { finishRecording(true); previewer.stop(); onBack() },
                modifier = if (inline) Modifier.size(36.dp) else Modifier
            ) {
                if (inline) Icon(Icons.Filled.Close, contentDescription = "Tutup audio")
                else Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                if (inline) {
                    Text("Audio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Text(project?.name ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Studio Audio", fontSize = 12.sp, color = FatihColors.Blue)
                }
            }
        }

        if (!inline) TimelineOverview(filmSec, tracks)

        // ---------- Tombol tambah ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("audio/*")) }, enabled = !recording) {
                Icon(Icons.Filled.LibraryMusic, null)
                Spacer(Modifier.width(6.dp))
                Text("Impor")
            }
            OutlinedButton(onClick = { showSfx = true }, enabled = !recording) {
                Icon(Icons.Filled.GraphicEq, null)
                Spacer(Modifier.width(6.dp))
                Text("Efek suara")
            }
            Button(
                onClick = { onRecordClick() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) FatihColors.ShutterRed else FatihColors.Purple,
                    contentColor = FatihColors.OnAccent
                )
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Selesai merekam" else "Rekam suara"
                )
            }
        }

        if (recording) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Merekam  " + fmt(recMs / 1000f),
                    color = FatihColors.ShutterRed,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = { recLevel },
                    color = FatihColors.ShutterRed,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(listWeight.padding(if (inline) 12.dp else 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada audio. Impor musik, rekam suara, atau tambah efek suara.",
                    color = FatihColors.TextSecondary
                )
            }
        } else {
            LazyColumn(
                listWeight,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackCard(
                        track = track,
                        maxStartSec = max(filmSec, 1f),
                        previewing = previewer.playingId == track.id,
                        onTogglePreview = {
                            if (previewer.playingId == track.id) previewer.stop() else previewer.play(track)
                        },
                        onCommit = { updated ->
                            previewer.stop()
                            vm.update(updated)
                        },
                        onDelete = {
                            previewer.stop()
                            vm.delete(track)
                        }
                    )
                }
            }
        }
    }

    if (showSfx) {
        AlertDialog(
            onDismissRequest = { showSfx = false },
            title = { Text("Efek suara") },
            text = {
                Column {
                    SoundFx.presets.forEach { p ->
                        TextButton(onClick = {
                            showSfx = false
                            vm.addSoundFx(p.id, p.label)
                        }) { Text(p.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSfx = false }) { Text("Tutup") } }
        )
    }
}

/** Ikhtisar: batang film (biru) dan setiap track audio (ungu) pada satu sumbu waktu. */
@Composable
private fun TimelineOverview(filmSec: Float, tracks: List<AudioTrackEntity>) {
    val trackEnd = tracks.maxOfOrNull { (it.startMs + (it.trimEndMs - it.trimStartMs)) / 1000f } ?: 0f
    val total = max(max(filmSec, trackEnd), 1f)
    val rows = tracks.size + 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((rows * 14 + 8).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(FatihColors.Surface)
        ) {
            val rowH = 14.dp.toPx()
            val pad = 4.dp.toPx()
            val barH = 8.dp.toPx()
            val w = size.width - pad * 2
            val corner = CornerRadius(4.dp.toPx())
            drawRoundRect(
                FatihColors.Blue,
                Offset(pad, pad + (rowH - barH) / 2),
                Size(w * (filmSec / total), barH),
                corner
            )
            tracks.forEachIndexed { i, t ->
                val len = (t.trimEndMs - t.trimStartMs) / 1000f
                val x = pad + w * (t.startMs / 1000f / total)
                drawRoundRect(
                    if (t.muted) Color(0xFF55555F) else FatihColors.Purple,
                    Offset(x, pad + rowH * (i + 1) + (rowH - barH) / 2),
                    Size((w * (len / total)).coerceAtLeast(3.dp.toPx()), barH),
                    corner
                )
            }
        }
        Text(
            "Film ${fmt(filmSec)}  ·  Total ${fmt(total)}",
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun TrackCard(
    track: AudioTrackEntity,
    maxStartSec: Float,
    previewing: Boolean,
    onTogglePreview: () -> Unit,
    onCommit: (AudioTrackEntity) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(track.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val durSec = track.durationMs / 1000f
    var vol by remember(track.volume) { mutableFloatStateOf(track.volume) }
    var start by remember(track.startMs) { mutableFloatStateOf(track.startMs / 1000f) }
    var cutIn by remember(track.trimStartMs) { mutableFloatStateOf(track.trimStartMs / 1000f) }
    var cutOut by remember(track.trimEndMs) { mutableFloatStateOf(track.trimEndMs / 1000f) }
    var fadeIn by remember(track.fadeInMs) { mutableFloatStateOf(track.fadeInMs / 1000f) }
    var fadeOut by remember(track.fadeOutMs) { mutableFloatStateOf(track.fadeOutMs / 1000f) }

    val lenSec = (track.trimEndMs - track.trimStartMs) / 1000f
    val canTrim = durSec > 0.4f
    val maxFade = min(3f, max(lenSec / 2f, 0.1f))
    val kindLabel = when (track.kind) {
        AudioKind.RECORD -> "Rekaman"
        AudioKind.SFX -> "Efek suara"
        else -> "Musik / audio"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FatihColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Audiotrack,
                contentDescription = null,
                tint = if (track.muted) FatihColors.TextSecondary else FatihColors.Purple
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                Text(track.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$kindLabel · ${fmt(lenSec)} · mulai ${fmt(track.startMs / 1000f)}",
                    fontSize = 11.sp,
                    color = FatihColors.TextSecondary
                )
            }
            IconButton(onClick = onTogglePreview) {
                Icon(
                    if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (previewing) "Hentikan" else "Putar"
                )
            }
            IconButton(onClick = { onCommit(track.copy(muted = !track.muted)) }) {
                Icon(
                    if (track.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (track.muted) "Nyalakan" else "Bisukan",
                    tint = if (track.muted) FatihColors.ShutterRed else Color.White
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Pengaturan"
                )
            }
        }

        if (expanded) {
            SliderRow("Volume", vol, 0f..1f, "${(vol * 100).roundToInt()}%", true,
                onChange = { vol = it },
                onFinish = { onCommit(track.copy(volume = vol)) })
            SliderRow("Mulai di", start, 0f..maxStartSec, fmt(start), true,
                onChange = { start = it },
                onFinish = { onCommit(track.copy(startMs = (start * 1000).toLong())) })
            SliderRow("Potong awal", cutIn, 0f..max(cutOut - 0.1f, 0.05f), fmt(cutIn), canTrim,
                onChange = { cutIn = it.coerceAtMost(cutOut - 0.1f) },
                onFinish = { onCommit(track.copy(trimStartMs = (cutIn * 1000).toLong())) })
            SliderRow("Potong akhir", cutOut, min(cutIn + 0.1f, durSec)..max(durSec, cutIn + 0.1f), fmt(cutOut), canTrim,
                onChange = { cutOut = it },
                onFinish = { onCommit(track.copy(trimEndMs = (cutOut * 1000).toLong().coerceAtMost(track.durationMs))) })
            SliderRow("Fade in", fadeIn, 0f..maxFade, fmt(fadeIn), true,
                onChange = { fadeIn = it },
                onFinish = { onCommit(track.copy(fadeInMs = (fadeIn * 1000).toLong())) })
            SliderRow("Fade out", fadeOut, 0f..maxFade, fmt(fadeOut), true,
                onChange = { fadeOut = it },
                onFinish = { onCommit(track.copy(fadeOutMs = (fadeOut * 1000).toLong())) })
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, null, tint = FatihColors.ShutterRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hapus track", color = FatihColors.ShutterRed)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus track?") },
            text = { Text("\"${track.name}\" akan dihapus dari proyek. Tindakan ini tidak bisa di-undo.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Hapus", color = FatihColors.ShutterRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = range,
            enabled = enabled && range.endInclusive > range.start,
            modifier = Modifier.weight(1f).height(30.dp)
        )
        Text(display, fontSize = 11.sp, color = FatihColors.TextSecondary, modifier = Modifier.width(48.dp))
    }
}
