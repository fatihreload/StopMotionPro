package com.fatih.stopmotion.ui.frames

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.ui.camera.CaptureMode
import com.fatih.stopmotion.ui.theme.FatihColors
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FramesScreen(
    projectId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onCapture: (mode: String, target: Long) -> Unit,
    onEditImage: (frameId: Long) -> Unit
) {
    val context = LocalContext.current
    val vm: FramesViewModel = viewModel(
        key = "frames_$projectId",
        factory = FramesViewModelFactory(container, projectId)
    )
    val project by vm.project.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val selectedRaw by vm.selected.collectAsStateWithLifecycle()
    val focusedId by vm.focused.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    val frameIds = remember(frames) { frames.map { it.id }.toSet() }
    val selected = remember(selectedRaw, frameIds) { selectedRaw.intersect(frameIds) }
    val focusedFrame = remember(frames, focusedId) { frames.firstOrNull { it.id == focusedId } }
    val fps = project?.fps ?: 12

    var showDelete by remember { mutableStateOf(false) }
    var showHold by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

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
                Text("Frame", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "${frames.size} frame  •  ${selected.size} dipilih",
                    fontSize = 12.sp,
                    color = FatihColors.Blue
                )
            }
            IconButton(onClick = { vm.undo() }, enabled = history.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { vm.redo() }, enabled = history.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            if (frames.isNotEmpty()) {
                TextButton(onClick = {
                    if (selected.size == frames.size) vm.clearSelection()
                    else vm.selectAll(frames.map { it.id })
                }) { Text(if (selected.size == frames.size) "Batal pilih" else "Pilih semua") }
            }
        }

        if (frames.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada frame", color = FatihColors.TextSecondary)
                    TextButton(onClick = onBack) { Text("Kembali ke kamera") }
                }
            }
        } else {
            // ---------- Preview frame terakhir yang diketuk ----------
            if (focusedFrame != null) {
                val request = remember(focusedFrame.filePath) {
                    ImageRequest.Builder(context)
                        .data(File(focusedFrame.filePath))
                        .size(720, 960)
                        .build()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FatihColors.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = "Pratinjau frame",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Text(
                        "Frame ${focusedFrame.position + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // ---------- Grid frame ----------
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(frames, key = { _, f -> f.id }) { index, frame ->
                    FrameCell(
                        frame = frame,
                        number = index + 1,
                        selected = frame.id in selected,
                        onClick = { vm.toggle(frame.id) }
                    )
                }
            }

            // ---------- Aksi ----------
            val hasSel = selected.isNotEmpty()
            val single = selected.size == 1
            val singleIndex = if (single) frames.indexOfFirst { it.id == selected.first() } else -1
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(FatihColors.Surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ActionButton(Icons.Filled.Delete, "Hapus", hasSel) { showDelete = true }
                ActionButton(Icons.Filled.ContentCopy, "Duplikat", hasSel) { vm.duplicateSelected(selected) }
                ActionButton(Icons.Filled.ChevronLeft, "Geser Kiri", hasSel) { vm.moveSelected(selected, -1) }
                ActionButton(Icons.Filled.ChevronRight, "Geser Kanan", hasSel) { vm.moveSelected(selected, 1) }
                ActionButton(Icons.Filled.Schedule, "Durasi", hasSel) { showHold = true }
                ActionButton(Icons.Filled.CameraAlt, "Ganti Foto", single) {
                    onCapture(CaptureMode.REPLACE, selected.first())
                }
                ActionButton(Icons.Filled.Tune, "Edit Foto", single) {
                    onEditImage(selected.first())
                }
                ActionButton(Icons.Filled.AddAPhoto, "Sisip Sebelum", single) {
                    onCapture(CaptureMode.INSERT, singleIndex.toLong())
                }
                ActionButton(Icons.Filled.AddAPhoto, "Sisip Sesudah", single) {
                    onCapture(CaptureMode.INSERT, (singleIndex + 1).toLong())
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = FatihColors.SurfaceHigh,
            title = { Text("Hapus ${selected.size} frame?") },
            text = { Text("Foto akan dihapus permanen dari proyek ini.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(selected); showDelete = false }) {
                    Text("Hapus", color = FatihColors.ShutterRed)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal") } }
        )
    }

    if (showHold) {
        var hold by remember { mutableFloatStateOf(1f) }
        val holdInt = hold.roundToInt()
        AlertDialog(
            onDismissRequest = { showHold = false },
            containerColor = FatihColors.SurfaceHigh,
            title = { Text("Durasi Frame") },
            text = {
                Column {
                    Text("Ditahan ${holdInt}x  (" + String.format(Locale.US, "%.2f", holdInt / fps.toFloat()) + " detik pada $fps FPS)")
                    Slider(value = hold, onValueChange = { hold = it }, valueRange = 1f..10f, steps = 8)
                    Text(
                        "Berlaku untuk ${selected.size} frame terpilih.",
                        fontSize = 12.sp,
                        color = FatihColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setHold(selected, holdInt); showHold = false }) { Text("Terapkan") }
            },
            dismissButton = { TextButton(onClick = { showHold = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun FrameCell(frame: FrameEntity, number: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(10.dp))
            .background(FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .border(
                if (selected) 3.dp else 0.dp,
                if (selected) FatihColors.Purple else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
    ) {
        AsyncImage(
            model = File(frame.thumbPath),
            contentDescription = "Frame $number",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (selected) {
            Box(Modifier.fillMaxSize().background(FatihColors.Purple.copy(alpha = 0.3f)))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Dipilih",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(FatihColors.Purple, RoundedCornerShape(9.dp))
                    .padding(2.dp)
            )
        }
        Text(
            "$number",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(topEnd = 8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (frame.holdCount > 1) {
            Text(
                "x${frame.holdCount}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = FatihColors.Blue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(bottomStart = 8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = FatihColors.Purple, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp)
    }
}
