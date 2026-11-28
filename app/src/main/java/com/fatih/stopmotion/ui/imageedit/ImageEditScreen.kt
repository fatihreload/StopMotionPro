package com.fatih.stopmotion.ui.imageedit

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.fx.AspectOptions
import com.fatih.stopmotion.fx.EditParams
import com.fatih.stopmotion.fx.FilterNames
import com.fatih.stopmotion.fx.ImageFx
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ImageEditScreen(projectId: Long, frameId: Long, container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: ImageEditViewModel = viewModel(
        key = "imgedit_${projectId}_$frameId",
        factory = ImageEditViewModelFactory(container, projectId, frameId)
    )
    val preview by vm.preview.collectAsStateWithLifecycle()
    val frames by vm.frames.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    var edit by remember { mutableStateOf(EditParams()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var applyAll by rememberSaveable { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) { vm.done.collect { onBack() } }

    val frameNumber = frames.indexOfFirst { it.id == frameId } + 1
    val saving = progress != null

    // Geometri (putar/balik/potong) dihitung pada bitmap pratinjau; warna lewat ColorFilter agar slider mulus.
    val geoBmp by produceState<Bitmap?>(initialValue = null, preview, edit.geo) {
        val src = preview
        value = if (src == null) null else withContext(Dispatchers.Default) {
            ImageFx.applyGeometry(src, edit.geo)
        }
    }
    val colorArray = remember(
        edit.brightness, edit.contrast, edit.saturation, edit.warmth, edit.filter, edit.filterIntensity
    ) { ImageFx.colorArray(edit) }
    val colorFilter = remember(colorArray) { colorArray?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
            }
            Column(Modifier.weight(1f)) {
                Text("Edit Foto", fontWeight = FontWeight.SemiBold)
                Text(
                    if (frameNumber > 0) "Frame $frameNumber" else "",
                    fontSize = 12.sp,
                    color = FatihColors.Blue
                )
            }
            Text(
                "Tahan gambar = lihat asli",
                fontSize = 11.sp,
                color = FatihColors.TextSecondary,
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // ---------- Pratinjau ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            val shown = if (showOriginal) preview else geoBmp
            if (shown == null) {
                CircularProgressIndicator()
            } else {
                val image = remember(shown) { shown.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "Pratinjau foto",
                    contentScale = ContentScale.Fit,
                    colorFilter = if (showOriginal) null else colorFilter,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
                if (showOriginal) {
                    Text(
                        "Asli",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ---------- Tab ----------
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            EditTab(Icons.Filled.Palette, "Filter", tab == 0) { tab = 0 }
            EditTab(Icons.Filled.Tune, "Warna", tab == 1) { tab = 1 }
            EditTab(Icons.Filled.RotateRight, "Putar", tab == 2) { tab = 2 }
            EditTab(Icons.Filled.Crop, "Potong", tab == 3) { tab = 3 }
        }

        // ---------- Panel ----------
        Column(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .background(FatihColors.Surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            when (tab) {
                0 -> FilterPanel(preview, edit) { edit = it }
                1 -> ColorPanel(edit) { edit = it }
                2 -> RotatePanel(edit) { edit = it }
                else -> CropPanel(edit) { edit = it }
            }
        }

        // ---------- Simpan ----------
        Column(Modifier.fillMaxWidth().background(FatihColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (saving) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { edit = EditParams() }, enabled = !saving && !edit.isIdentity) {
                    Text("Reset")
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = applyAll,
                    onCheckedChange = { applyAll = it },
                    enabled = !saving && frames.size > 1
                )
                Spacer(Modifier.width(6.dp))
                Text("Semua frame", fontSize = 13.sp)
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { vm.save(edit, applyAll) },
                    enabled = !saving && !edit.isIdentity && preview != null,
                    colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
                ) { Text(if (saving) "Memproses..." else "Simpan") }
            }
        }
    }
}

@Composable
private fun EditTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) FatihColors.Purple else FatihColors.TextSecondary)
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) FatihColors.Purple else FatihColors.TextSecondary
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(
            display,
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun FilterPanel(preview: Bitmap?, edit: EditParams, onChange: (EditParams) -> Unit) {
    val thumb: ImageBitmap? = remember(preview) { preview?.asImageBitmap() }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterNames.forEachIndexed { i, name ->
            val filter = remember(i) {
                ImageFx.colorArray(0f, 1f, 1f, 0f, i, 1f)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
            }
            val selected = edit.filter == i
            Column(
                Modifier.clickable { onChange(edit.copy(filter = i)) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(56.dp, 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FatihColors.SurfaceHigh)
                        .border(
                            if (selected) 2.dp else 0.dp,
                            if (selected) FatihColors.Purple else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            colorFilter = filter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Text(
                    name,
                    fontSize = 10.sp,
                    color = if (selected) FatihColors.Purple else FatihColors.TextSecondary
                )
            }
        }
    }
    if (edit.filter != 0) {
        LabeledSlider(
            "Kekuatan", edit.filterIntensity, 0f..1f,
            "${(edit.filterIntensity * 100).toInt()}%"
        ) { onChange(edit.copy(filterIntensity = it)) }
    }
}

@Composable
private fun ColorPanel(edit: EditParams, onChange: (EditParams) -> Unit) {
    LabeledSlider("Kecerahan", edit.brightness, -1f..1f, fmt(edit.brightness)) { onChange(edit.copy(brightness = it)) }
    LabeledSlider("Kontras", edit.contrast, 0.5f..1.8f, fmt(edit.contrast - 1f)) { onChange(edit.copy(contrast = it)) }
    LabeledSlider("Saturasi", edit.saturation, 0f..2f, fmt(edit.saturation - 1f)) { onChange(edit.copy(saturation = it)) }
    LabeledSlider("Suhu warna", edit.warmth, -1f..1f, fmt(edit.warmth)) { onChange(edit.copy(warmth = it)) }
}

@Composable
private fun RotatePanel(edit: EditParams, onChange: (EditParams) -> Unit) {
    val g = edit.geo
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChange(edit.copy(geo = g.copy(quarterTurns = (g.quarterTurns + 3) % 4))) }) {
            Icon(Icons.Filled.RotateLeft, null)
            Spacer(Modifier.width(4.dp))
            Text("Kiri")
        }
        OutlinedButton(onClick = { onChange(edit.copy(geo = g.copy(quarterTurns = (g.quarterTurns + 1) % 4))) }) {
            Icon(Icons.Filled.RotateRight, null)
            Spacer(Modifier.width(4.dp))
            Text("Kanan")
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChange(edit.copy(geo = g.copy(flipH = !g.flipH))) }) {
            Icon(Icons.Filled.Flip, null)
            Spacer(Modifier.width(4.dp))
            Text(if (g.flipH) "Balik H (aktif)" else "Balik H")
        }
        OutlinedButton(onClick = { onChange(edit.copy(geo = g.copy(flipV = !g.flipV))) }) {
            Icon(Icons.Filled.Flip, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (g.flipV) "Balik V (aktif)" else "Balik V")
        }
    }
}

@Composable
private fun CropPanel(edit: EditParams, onChange: (EditParams) -> Unit) {
    val g = edit.geo
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectOptions.forEachIndexed { i, opt ->
            val selected = g.aspect == i
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
                    .clickable { onChange(edit.copy(geo = g.copy(aspect = i, offX = 0f, offY = 0f))) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(opt.label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    LabeledSlider("Zoom", g.zoom, 1f..3f, String.format(Locale.US, "%.1fx", g.zoom)) {
        onChange(edit.copy(geo = g.copy(zoom = it)))
    }
    LabeledSlider("Geser X", g.offX, -1f..1f, fmt(g.offX)) { onChange(edit.copy(geo = g.copy(offX = it))) }
    LabeledSlider("Geser Y", g.offY, -1f..1f, fmt(g.offY)) { onChange(edit.copy(geo = g.copy(offY = it))) }
}

private fun fmt(v: Float): String = String.format(Locale.US, "%+.2f", v)
