package com.fatih.stopmotion.ui.erase

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.fx.EraseCodec
import com.fatih.stopmotion.fx.EraseMask
import com.fatih.stopmotion.fx.EraseStroke
import com.fatih.stopmotion.ui.editor.ChromaBackground
import com.fatih.stopmotion.ui.theme.FatihColors
import java.io.File
import kotlin.math.hypot

/**
 * Sesi erase. Gambar dasar tidak pernah diubah; coretan disimpan sebagai daftar dan digambar ulang oleh Compose setiap
 * perubahan (BlendMode.Clear pada lapisan terpisah), sehingga tampilan pasti ter-update. Koordinat dinormalisasi.
 */
/** Batas zoom kanvas (cubit dua jari). */
private const val MAX_ZOOM = 6f

private class EraseSession(val base: Bitmap) {
    val w = base.width
    val h = base.height

    val strokes = mutableStateListOf<EraseStroke>()
    private val redoStack = ArrayList<EraseStroke>()
    var redoCount by mutableIntStateOf(0)
    val count: Int get() = strokes.size

    /** Coretan yang sedang digambar (x0,y0,x1,y1,...). */
    val live = mutableStateListOf<Float>()
    var liveRestore by mutableStateOf(false)
    var liveRadius by mutableFloatStateOf(0.04f)

    fun setStrokes(list: List<EraseStroke>) {
        strokes.clear(); strokes.addAll(list)
        redoStack.clear(); redoCount = 0
        live.clear()
    }

    fun begin(restore: Boolean, radius: Float, x: Float, y: Float) {
        liveRestore = restore
        liveRadius = radius
        live.clear()
        live.add(x); live.add(y)
    }

    fun extend(x: Float, y: Float) {
        if (live.size < 2) return
        val lx = live[live.size - 2]
        val ly = live[live.size - 1]
        // titik terlalu rapat dilewati agar data kecil
        if (hypot((x - lx) * w, (y - ly) * h) < 2f) return
        live.add(x); live.add(y)
    }

    /** Membuang coretan yang sedang digambar tanpa menyimpannya (mis. saat jari kedua menyentuh untuk zoom). */
    fun cancel() {
        live.clear()
    }

    fun end() {
        if (live.isEmpty()) return
        strokes.add(EraseStroke(liveRestore, liveRadius, live.toFloatArray()))
        live.clear()
        redoStack.clear(); redoCount = 0
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        redoCount = redoStack.size
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        redoCount = redoStack.size
    }

    fun clear() {
        strokes.clear(); redoStack.clear(); redoCount = 0
    }
}

/** Satu coretan pada lapisan mask: Clear = bagian dibuang (transparan), Src = dipulihkan (kembali terlihat). */
private fun DrawScope.drawEraseStroke(s: EraseStroke) {
    val n = s.pointCount
    if (n == 0) return
    val mode = if (s.restore) BlendMode.Src else BlendMode.Clear
    val radius = s.radius * size.width
    if (n == 1) {
        drawCircle(
            Color.Black, radius = radius,
            center = Offset(s.points[0] * size.width, s.points[1] * size.height), blendMode = mode
        )
    } else {
        val path = Path()
        path.moveTo(s.points[0] * size.width, s.points[1] * size.height)
        for (i in 1 until n) path.lineTo(s.points[i * 2] * size.width, s.points[i * 2 + 1] * size.height)
        drawPath(
            path, Color.Black,
            style = Stroke(width = 2f * radius, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = mode
        )
    }
}

@Composable
fun EraseScreen(
    projectId: Long,
    frameId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onCapturePlate: () -> Unit
) {
    val context = LocalContext.current
    val vm: EraseViewModel = viewModel(
        key = "erase_${projectId}_$frameId",
        factory = EraseViewModelFactory(container, projectId, frameId)
    )
    val load by vm.load.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val chromaNow by vm.chroma.collectAsStateWithLifecycle()
    val allFrames by vm.frames.collectAsStateWithLifecycle()
    val pickPlate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setBackgroundImage(uri)
    }
    var showPlate by remember { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(Unit) { vm.done.collect { onBack() } }

    var restoreTool by rememberSaveable { mutableStateOf(false) }
    var brush by rememberSaveable { mutableFloatStateOf(0.05f) }
    var applyAll by rememberSaveable { mutableStateOf(false) }
    var ghost by rememberSaveable { mutableStateOf(false) }
    var checker by rememberSaveable { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }

    val data = load
    val session = remember(data) { data?.let { EraseSession(it.base).also { s -> s.setStrokes(EraseCodec.decode(vm.draft ?: it.initialData)) } } }

    if (showPlate) {
        PlateDialog(
            frames = allFrames,
            onCapture = {
                showPlate = false
                vm.draft = EraseCodec.encode(session?.strokes ?: emptyList()) ?: ""
                onCapturePlate()
            },
            onPickFrame = { showPlate = false; vm.useFrameAsBackground(it) },
            onPickGallery = { showPlate = false; pickPlate.launch(arrayOf("image/*")) },
            hasPlate = chromaNow.bgPath != null,
            onClear = { showPlate = false; vm.clearPlate() },
            onDismiss = { showPlate = false }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Cara memakai Erase") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("\u2022 Hapus: coret bagian yang ingin dibuang (tangan, kawat, penyangga).", fontSize = 13.sp)
                    Text("\u2022 Pulihkan: coret untuk mengembalikan bagian yang terlanjur terhapus.", fontSize = 13.sp)
                    Text("\u2022 Area yang terhapus diganti Latar bersih (foto tanpa karakter) atau warna latar.", fontSize = 13.sp)
                    Text("\u2022 Kotak-kotak: tampilan bagian transparan. Matikan untuk melihat hasil akhir dengan latar.", fontSize = 13.sp)
                    Text("\u2022 Bayangan: menampilkan foto asli samar di bawah hasil.", fontSize = 13.sp)
                    Text("\u2022 Semua frame: coretan yang sama dipakai di semua frame (cocok untuk penyangga yang diam).", fontSize = 13.sp)
                    Text("\u2022 1 jari menggambar, 2 jari cubit untuk zoom dan geser.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Mengerti") } }
        )
    }

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
                Text("Erase", fontWeight = FontWeight.SemiBold)
                Text(
                    if (data != null) "Frame ${data.frameNumber} dari ${data.frameCount}" else "",
                    fontSize = 12.sp, color = FatihColors.Blue
                )
            }
            IconButton(onClick = { session?.undo() }, enabled = session != null && session.count > 0) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Urungkan coretan")
            }
            IconButton(onClick = { session?.redo() }, enabled = session != null && session.redoCount > 0) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Ulangi coretan")
            }
            IconButton(onClick = { session?.clear() }, enabled = session != null && session.count > 0) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Bersihkan semua coretan")
            }
            IconButton(onClick = { showHelp = true }) {
                Icon(Icons.Filled.Info, contentDescription = "Bantuan", tint = FatihColors.TextSecondary)
            }
        }

        // ---------- Kanvas ----------
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (session == null || data == null) {
                CircularProgressIndicator()
            } else {
                val baseImage = remember(session) { session.base.asImageBitmap() }
                Box(
                    Modifier
                        .aspectRatio(session.w / session.h.toFloat())
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                        // 1 jari = menghapus/memulihkan. 2 jari = cubit untuk zoom dan geser ke segala arah.
                        .pointerInput(session, restoreTool, brush) {
                            awaitEachGesture {
                                val sw = size.width.toFloat().coerceAtLeast(1f)
                                val sh = size.height.toFloat().coerceAtLeast(1f)
                                // pan dijaga agar gambar selalu memenuhi kotak
                                fun clampPan(p: Offset, z: Float) = Offset(
                                    p.x.coerceIn(sw * (1f - z), 0f),
                                    p.y.coerceIn(sh * (1f - z), 0f)
                                )
                                // posisi layar -> koordinat gambar ternormalisasi (0..1)
                                fun nx(o: Offset) = (((o.x - pan.x) / zoom) / sw).coerceIn(0f, 1f)
                                fun ny(o: Offset) = (((o.y - pan.y) / zoom) / sh).coerceIn(0f, 1f)

                                val down = awaitFirstDown(requireUnconsumed = false)
                                val firstId = down.id
                                var drawing = true
                                var multiTouch = false
                                // ukuran kuas di layar tetap; makin besar zoom, makin halus coretan di gambar
                                session.begin(restoreTool, brush / zoom, nx(down.position), ny(down.position))
                                cursor = down.position
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        if (!multiTouch) {
                                            multiTouch = true
                                            if (drawing) { session.cancel(); drawing = false }
                                            cursor = null
                                        }
                                        val a = pressed[0]
                                        val b = pressed[1]
                                        val oldDist = (a.previousPosition - b.previousPosition).getDistance()
                                        val newDist = (a.position - b.position).getDistance()
                                        val oldC = (a.previousPosition + b.previousPosition) / 2f
                                        val newC = (a.position + b.position) / 2f
                                        if (oldDist > 1f && newDist > 1f) {
                                            val newZoom = (zoom * newDist / oldDist).coerceIn(1f, MAX_ZOOM)
                                            val factor = newZoom / zoom
                                            // titik gambar di bawah jari tetap berada di bawah jari
                                            pan = clampPan(newC - (oldC - pan) * factor, newZoom)
                                            zoom = newZoom
                                        }
                                        pressed.forEach { it.consume() }
                                    } else {
                                        val ch = pressed[0]
                                        if (!multiTouch && ch.id == firstId) {
                                            if (ch.positionChanged()) {
                                                session.extend(nx(ch.position), ny(ch.position))
                                                cursor = ch.position
                                                ch.consume()
                                            }
                                        } else {
                                            ch.consume() // sisa satu jari setelah zoom: abaikan sampai semua jari diangkat
                                        }
                                    }
                                }
                                if (drawing) session.end()
                                cursor = null
                            }
                        }
                ) {
                    // Lapisan bawah (latar, kotak-kotak, bayangan) di-zoom lewat transformasi layer.
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = pan.x
                            translationY = pan.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    ) {
                        ChromaBackground(chromaNow, Modifier.fillMaxSize())
                        if (checker) {
                            Canvas(Modifier.fillMaxSize()) {
                                val cell = 14.dp.toPx()
                                var yy = 0f
                                var row = 0
                                while (yy < size.height) {
                                    var xx = 0f
                                    var col = row % 2
                                    while (xx < size.width) {
                                        drawRect(
                                            if (col % 2 == 0) Color(0xFF3A3A44) else Color(0xFF55555F),
                                            Offset(xx, yy), Size(cell, cell)
                                        )
                                        xx += cell; col++
                                    }
                                    yy += cell; row++
                                }
                            }
                        }
                        if (ghost) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawImage(
                                    baseImage,
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                    alpha = 0.28f
                                )
                            }
                        }
                    }
                    // Lapisan foto: foto dikali mask, digambar dengan transformasi zoom di dalam kanvas (tetap tajam).
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    ) {
                        withTransform({
                            translate(pan.x, pan.y)
                            scale(zoom, zoom, Offset.Zero)
                        }) {
                            drawImage(
                                baseImage,
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                            drawIntoCanvas { canvas ->
                                val layerPaint = ComposePaint().apply { blendMode = BlendMode.DstIn }
                                canvas.saveLayer(ComposeRect(0f, 0f, size.width, size.height), layerPaint)
                                drawRect(Color.Black) // mask: semua dipertahankan
                                for (stroke in session.strokes) drawEraseStroke(stroke)
                                if (session.live.size >= 2) {
                                    drawEraseStroke(
                                        EraseStroke(session.liveRestore, session.liveRadius, session.live.toFloatArray())
                                    )
                                }
                                canvas.restore()
                            }
                        }
                    }
                    // Lapisan atas: lingkaran kuas (ukuran layar, tidak ikut di-zoom).
                    Canvas(Modifier.fillMaxSize()) {
                        cursor?.let {
                            drawCircle(
                                if (restoreTool) Color(0xFF4FA3E0) else Color(0xFFFF6B6B),
                                radius = brush * size.width,
                                center = it,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    if (session.count == 0) {
                        Text(
                            "1 jari: hapus  \u2022  2 jari: zoom",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ---------- Kontrol (ringkas: mode, kuas, opsi, simpan) ----------
        Column(
            Modifier
                .fillMaxWidth()
                .background(FatihColors.Surface)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolChip("Hapus", !restoreTool, Modifier.weight(1f)) { restoreTool = false }
                ToolChip("Pulihkan", restoreTool, Modifier.weight(1f)) { restoreTool = true }
                ToolChip(
                    if (zoom > 1.01f) "Zoom \u00d7${"%.1f".format(zoom)} \u21ba" else "Zoom 1\u00d7",
                    zoom > 1.01f,
                    Modifier.weight(1f)
                ) { zoom = 1f; pan = Offset.Zero }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kuas", fontSize = 12.sp, modifier = Modifier.width(44.dp))
                Slider(
                    value = brush,
                    onValueChange = { brush = it },
                    valueRange = 0.01f..0.15f,
                    modifier = Modifier.weight(1f).height(32.dp)
                )
                Text("${(brush * 200).toInt()}%", fontSize = 11.sp, color = FatihColors.TextSecondary, modifier = Modifier.width(40.dp))
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolChip("Kotak-kotak", checker) { checker = !checker }
                ToolChip("Bayangan", ghost) { ghost = !ghost }
                ToolChip(
                    if (chromaNow.bgPath == null) "Latar bersih" else "Latar bersih \u2713",
                    chromaNow.bgPath != null,
                    enabled = !saving
                ) { showPlate = true }
                ToolChip(
                    "Salin dari sebelumnya", false,
                    enabled = session != null && !data?.previousData.isNullOrEmpty() && !saving
                ) { session?.setStrokes(EraseCodec.decode(data?.previousData)) }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = applyAll, onCheckedChange = { applyAll = it }, enabled = !saving)
                Spacer(Modifier.width(8.dp))
                Text("Semua frame", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { session?.let { vm.save(EraseCodec.encode(it.strokes), applyAll) } },
                    enabled = session != null && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
                ) { Text(if (saving) "Menyimpan..." else "Simpan") }
            }
        }
    }
}

@Composable
private fun PlateDialog(
    frames: List<com.fatih.stopmotion.data.FrameEntity>,
    onCapture: () -> Unit,
    onPickFrame: (Long) -> Unit,
    onPickGallery: () -> Unit,
    hasPlate: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Latar bersih") },
        text = {
            Column {
                Text(
                    "Agar tangan atau kawat benar-benar hilang, area yang dihapus harus diganti foto latar yang sama " +
                        "TANPA tangan dan benda. Jangan menggeser kamera atau meja.",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Foto latar bersih sekarang") }
                Spacer(Modifier.height(10.dp))
                Text("Atau pakai frame yang sudah ada tanpa tangan/benda:", fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(frames, key = { it.id }) { f ->
                        val number = frames.indexOf(f) + 1
                        Column(
                            Modifier.clickable { onPickFrame(f.id) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = File(f.thumbPath),
                                contentDescription = "Frame $number",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp, 86.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text("$number", fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onPickGallery) { Text("Dari galeri") } },
        dismissButton = {
            Row {
                if (hasPlate) TextButton(onClick = onClear) { Text("Lepas latar") }
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
        }
    )
}

@Composable
private fun ToolChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontSize = 12.sp, maxLines = 1, softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
