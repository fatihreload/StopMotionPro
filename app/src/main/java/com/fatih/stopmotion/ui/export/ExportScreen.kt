package com.fatih.stopmotion.ui.export

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.export.EncoderSupport
import com.fatih.stopmotion.export.ExportFormat
import com.fatih.stopmotion.export.ExportSettings
import com.fatih.stopmotion.export.computeOutputSize
import com.fatih.stopmotion.export.defaultShortSide
import com.fatih.stopmotion.export.isUpscaled
import com.fatih.stopmotion.export.qualityLabel
import com.fatih.stopmotion.export.sizeOptions
import com.fatih.stopmotion.export.videoBitrate
import com.fatih.stopmotion.fx.ImageFx
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
}

private fun uriFor(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

@Composable
fun ExportScreen(projectId: Long, container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ExportViewModel = viewModel(
        key = "export_$projectId",
        factory = ExportViewModelFactory(container, projectId)
    )
    val project by vm.project.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    var formatName by rememberSaveable { mutableStateOf(ExportFormat.MP4.name) }
    var shortSide by rememberSaveable { mutableStateOf(720) }
    var quality by rememberSaveable { mutableStateOf(1) }
    var includeAudio by rememberSaveable { mutableStateOf(true) }
    var loop by rememberSaveable { mutableStateOf(true) }
    var confirmCancel by remember { mutableStateOf(false) }

    val format = ExportFormat.valueOf(formatName)
    val settings = ExportSettings(format, shortSide, quality, includeAudio, loop)
    val running = state is ExportState.Running

    // Layar tetap menyala selama ekspor; keluar halaman saat ekspor berjalan perlu konfirmasi.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = running) { confirmCancel = true }

    val fps = (project?.fps ?: 12).coerceIn(1, 60)
    val ticks = frames.sumOf { it.holdCount.coerceAtLeast(1) }
    val durationSec = ticks / fps.toFloat()
    val activeTracks = tracks.count { !it.muted }

    val firstPath = frames.firstOrNull()?.filePath
    val srcSize by produceState<Pair<Int, Int>?>(initialValue = null, firstPath) {
        value = if (firstPath == null) null else withContext(Dispatchers.Default) {
            ImageFx.decode(firstPath, 64)?.let { b -> (b.width to b.height).also { b.recycle() } }
        }
    }
    val srcFull by produceState<Pair<Int, Int>?>(initialValue = null, firstPath) {
        value = if (firstPath == null) null else withContext(Dispatchers.Default) { ImageFx.bounds(firstPath) }
    }
    // Tidak semua perangkat bisa menyandikan 2K/4K: cek encoder untuk tiap pilihan ukuran.
    val support by produceState<Map<Int, Boolean>>(initialValue = emptyMap(), srcSize, fps, formatName) {
        val s = srcSize
        value = if (s == null || formatName != ExportFormat.MP4.name) emptyMap() else withContext(Dispatchers.Default) {
            sizeOptions(ExportFormat.MP4).associateWith { side ->
                val (w, h) = computeOutputSize(s.first, s.second, side, 16)
                EncoderSupport.supportsAvc(w, h, fps)
            }
        }
    }
    val outSize = srcSize?.let {
        computeOutputSize(it.first, it.second, shortSide, if (format == ExportFormat.MP4) 16 else 2)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (running) confirmCancel = true else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text(project?.name ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("Ekspor", fontSize = 12.sp, color = FatihColors.Blue)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (frames.isEmpty()) {
                Text("Belum ada frame. Ambil foto di kamera dulu.", color = FatihColors.TextSecondary)
                return@Column
            }

            Text(
                "${frames.size} frame · ${ticks} tick · $fps FPS · ${String.format(Locale.US, "%.1f", durationSec)} detik",
                fontSize = 13.sp,
                color = FatihColors.TextSecondary
            )

            Heading("Format")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.entries.forEach { f ->
                    Chip(f.label, f == format, !running) {
                        formatName = f.name
                        shortSide = defaultShortSide(f)
                    }
                }
            }

            if (format != ExportFormat.ZIP) {
                Heading("Ukuran")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sizeOptions(format).forEach { s ->
                        val ok = support[s] != false
                        Chip(qualityLabel(s), shortSide == s, !running && ok) { shortSide = s }
                    }
                }
                if (outSize != null) {
                    Text(
                        "Hasil: ${outSize.first} x ${outSize.second} piksel",
                        fontSize = 12.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (format == ExportFormat.MP4 && support.values.any { !it }) {
                    Text(
                        "Ukuran yang redup tidak didukung encoder video di perangkat ini.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                val full = srcFull
                if (outSize != null && full != null && isUpscaled(full.first, full.second, outSize.first, outSize.second)) {
                    Text(
                        "Foto asli ${full.first} x ${full.second} lebih kecil dari ukuran hasil. Foto akan diperbesar, " +
                            "jadi hasilnya tidak lebih tajam. Naikkan kualitas kamera bila ingin hasil 2K/4K yang tajam.",
                        fontSize = 11.sp,
                        color = FatihColors.ShutterRed,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (shortSide >= 1440 && format == ExportFormat.MP4) {
                    Text(
                        "Ekspor 2K/4K memakai banyak memori dan waktu. Biarkan layar tetap di halaman ini sampai selesai.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (format == ExportFormat.MP4) {
                Heading("Kualitas")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hemat", "Seimbang", "Tinggi").forEachIndexed { i, label ->
                        Chip(label, quality == i, !running) { quality = i }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sertakan audio")
                        Text(
                            if (activeTracks == 0) "Belum ada track audio aktif" else "$activeTracks track akan dicampur",
                            fontSize = 11.sp,
                            color = FatihColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = includeAudio && activeTracks > 0,
                        onCheckedChange = { includeAudio = it },
                        enabled = !running && activeTracks > 0
                    )
                }
                if (outSize != null) {
                    val bits = videoBitrate(outSize.first, outSize.second, fps, quality) +
                        if (includeAudio && activeTracks > 0) 128_000 else 0
                    Text(
                        "Perkiraan ukuran: sekitar ${sizeLabel((bits / 8.0 * durationSec).toLong())}",
                        fontSize = 12.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            if (format == ExportFormat.GIF) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ulangi terus (loop)", Modifier.weight(1f))
                    Switch(checked = loop, onCheckedChange = { loop = it }, enabled = !running)
                }
                Text(
                    "GIF tidak mendukung audio dan maksimal 256 warna. Ukuran file bisa besar untuk animasi panjang.",
                    fontSize = 11.sp,
                    color = FatihColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (format == ExportFormat.ZIP) {
                Text(
                    "Semua foto frame (versi yang sudah diedit) sebagai JPG berurutan, tanpa efek film dan audio.",
                    fontSize = 12.sp,
                    color = FatihColors.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (format != ExportFormat.ZIP && project?.let { it.fxFilter != 0 || it.fxVignette > 0.01f || it.fxFadeIn > 0 || it.fxFadeOut > 0 } == true) {
                Text(
                    "Efek film (filter, vignette, fade) ikut diterapkan.",
                    fontSize = 12.sp,
                    color = FatihColors.Purple,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (format != ExportFormat.ZIP && (project?.chromaMode ?: 0) != 0) {
                Text(
                    "Hapus latar (green/blue screen) ikut diterapkan.",
                    fontSize = 12.sp,
                    color = FatihColors.Purple,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            when (val s = state) {
                is ExportState.Running -> {
                    Text(s.label, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("${(s.progress * 100).roundToInt()}%", fontSize = 12.sp, color = FatihColors.Blue)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { confirmCancel = true }) { Text("Batalkan") }
                }
                is ExportState.Done -> DoneCard(s, vm)
                is ExportState.Error -> {
                    Text("Gagal: ${s.message}", color = FatihColors.ShutterRed)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.start(settings) },
                        colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
                    ) { Text("Coba lagi") }
                }
                ExportState.Idle -> Button(
                    onClick = { vm.start(settings) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
                ) { Text("Mulai ekspor") }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Batalkan ekspor?") },
            text = { Text("Proses yang sedang berjalan akan dihentikan dan hasil sementara dibuang.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    vm.cancel()
                }) { Text("Batalkan ekspor", color = FatihColors.ShutterRed) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Lanjutkan") } }
        )
    }
}

@Composable
private fun DoneCard(done: ExportState.Done, vm: ExportViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(done.mime)) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            done.file.inputStream().use { it.copyTo(out) }
                        } ?: error("Tidak dapat menulis file")
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) "Tersimpan" else "Gagal menyimpan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FatihColors.Surface)
            .padding(16.dp)
    ) {
        Text("Ekspor selesai", fontWeight = FontWeight.Bold, color = FatihColors.Blue)
        Text(done.name, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Text(sizeLabel(done.sizeBytes), fontSize = 12.sp, color = FatihColors.TextSecondary)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { saveLauncher.launch(done.name) }) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(6.dp))
                Text("Simpan")
            }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = done.mime
                    putExtra(Intent.EXTRA_STREAM, uriFor(context, done.file))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Bagikan"))
            }) {
                Icon(Icons.Filled.Share, null)
                Spacer(Modifier.width(6.dp))
                Text("Bagikan")
            }
            if (done.mime != "application/zip") {
                OutlinedButton(onClick = {
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uriFor(context, done.file), done.mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(view)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Buka")
                }
            }
        }
        TextButton(onClick = { vm.dismissResult() }) { Text("Ekspor lagi") }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = FatihColors.Blue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
