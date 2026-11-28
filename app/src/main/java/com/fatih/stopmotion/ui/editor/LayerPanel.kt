package com.fatih.stopmotion.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.data.LayerEntity
import com.fatih.stopmotion.data.LayerKind
import com.fatih.stopmotion.fx.KeyframeEase
import com.fatih.stopmotion.fx.KeyframeMath
import com.fatih.stopmotion.fx.LayerTransform
import com.fatih.stopmotion.ui.theme.FatihColors
import java.util.Locale
import kotlin.math.max

private fun sec(ms: Long) = String.format(Locale.US, "%.1fs", ms / 1000f)

/**
 * Panel layer yang menempel di bawah pratinjau (menggantikan baris FPS dan kecepatan selama terbuka),
 * sehingga pratinjau tetap utuh untuk menggeser layer. Pengaturan dibagi dalam tab agar ringkas.
 */
@Composable
fun LayerPanel(
    layers: List<LayerEntity>,
    selectedId: Long?,
    busy: LayerBusy?,
    filmMs: Long,
    currentMs: Long,
    onSeek: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onChange: (LayerEntity) -> Unit,
    onCommit: () -> Unit,
    onAdd: () -> Unit,
    onDelete: (LayerEntity) -> Unit,
    onMove: (Long, Boolean) -> Unit,
    onCancelBusy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = layers.firstOrNull { it.id == selectedId }
    val ordered = remember(layers) { layers.sortedByDescending { it.zOrder } }

    Column(
        modifier
            .fillMaxWidth()
            .background(FatihColors.Surface)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ordered.forEach { l ->
                    val label = l.name.take(14) + if (l.visible) "" else " (sembunyi)"
                    MiniChip(label, null, l.id == selectedId) { onSelect(l.id) }
                }
                MiniChip("+ Layer", null, false) { onAdd() }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup panel layer")
            }
        }

        if (busy != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    busy.label + (busy.progress?.let { " ${(it * 100).toInt()}%" } ?: ""),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (busy.progress != null) {
                    LinearProgressIndicator(progress = { busy.progress }, modifier = Modifier.weight(1f))
                } else {
                    LinearProgressIndicator(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onCancelBusy) { Text("Batal", fontSize = 12.sp) }
            }
        }

        if (selected == null) {
            Text(
                if (layers.isEmpty())
                    "Ketuk \"+ Layer\" untuk menambah gambar atau klip video (mis. efek peluru berlatar hijau)."
                else "Pilih layer di atas untuk mengaturnya.",
                fontSize = 12.sp,
                color = FatihColors.TextSecondary,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
            )
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiniChip("Waktu", null, tab == 0) { tab = 0 }
                MiniChip("Tampilan", null, tab == 1) { tab = 1 }
                MiniChip("Chroma", null, tab == 2) { tab = 2 }
                MiniChip("Atur", null, tab == 3) { tab = 3 }
                MiniChip(if (KeyframeMath.hasKeyframes(selected)) "\u25c6 Animasi" else "Animasi", null, tab == 4) { tab = 4 }
            }
            Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                when (tab) {
                    0 -> {
                        val filmSec = max(filmMs / 1000f, 1f)
                        LSlider("Mulai", selected.startMs / 1000f, 0f..filmSec, sec(selected.startMs),
                            { onChange(selected.copy(startMs = (it * 1000).toLong())) }, onCommit)
                        val maxDur = max(max(filmSec, selected.durationMs / 1000f), 2f)
                        LSlider("Lama", selected.durationMs / 1000f, 0.1f..maxDur, sec(selected.durationMs),
                            { onChange(selected.copy(durationMs = (it * 1000).toLong().coerceAtLeast(100))) }, onCommit)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onChange(selected.copy(startMs = currentMs)); onCommit() },
                                modifier = Modifier.height(34.dp)
                            ) { Text("Mulai di sini", fontSize = 12.sp) }
                            if (selected.kind == LayerKind.VIDEO) {
                                Spacer(Modifier.width(10.dp))
                                Text("Ulangi", fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = selected.loopClip,
                                    onCheckedChange = { onChange(selected.copy(loopClip = it)); onCommit() }
                                )
                                val clipMs = (selected.frameCount * 1000f / selected.frameRate.coerceAtLeast(1f)).toLong()
                                Text(
                                    "  Klip ${sec(clipMs)}",
                                    fontSize = 11.sp,
                                    color = FatihColors.TextSecondary
                                )
                            }
                        }
                    }
                    1 -> {
                        // Nilai yang tampil adalah pose pada waktu sekarang; dengan keyframe, mengubahnya membuat/memperbarui
                        // keyframe di waktu ini.
                        val tf = KeyframeMath.sample(selected, currentMs)
                        fun edit(t: LayerTransform) = KeyframeMath.applyEdit(selected, currentMs, t)
                        LSlider("Ukuran", tf.scale, 0.05f..2f, "${(tf.scale * 100).toInt()}%",
                            { onChange(edit(tf.copy(scale = it))) }, onCommit)
                        LSlider("Putar", tf.rotation.coerceIn(-180f, 180f), -180f..180f, "${tf.rotation.toInt()}\u00b0",
                            { onChange(edit(tf.copy(rotation = it))) }, onCommit)
                        LSlider("Opasitas", tf.opacity, 0f..1f, "${(tf.opacity * 100).toInt()}%",
                            { onChange(edit(tf.copy(opacity = it))) }, onCommit)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onChange(edit(tf.copy(posX = 0.5f, posY = 0.5f))); onCommit() },
                                modifier = Modifier.height(34.dp)
                            ) { Text("Tengah", fontSize = 12.sp) }
                            Text(
                                "  Geser/cubit langsung di pratinjau.",
                                fontSize = 11.sp,
                                color = FatihColors.TextSecondary
                            )
                        }
                    }
                    2 -> {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            MiniChip("Mati", Color(0xFF55555F), selected.chromaMode == 0) {
                                onChange(selected.copy(chromaMode = 0)); onCommit()
                            }
                            MiniChip("Hijau", Color(0xFF1DB954), selected.chromaMode == 1) {
                                onChange(selected.copy(chromaMode = 1)); onCommit()
                            }
                            MiniChip("Biru", Color(0xFF2F6FED), selected.chromaMode == 2) {
                                onChange(selected.copy(chromaMode = 2)); onCommit()
                            }
                        }
                        if (selected.chromaMode != 0) {
                            LSlider("Toleransi", selected.chromaTolerance, 0f..1f, "${(selected.chromaTolerance * 100).toInt()}%",
                                { onChange(selected.copy(chromaTolerance = it)) }, onCommit)
                            LSlider("Tepi halus", selected.chromaSoftness, 0f..1f, "${(selected.chromaSoftness * 100).toInt()}%",
                                { onChange(selected.copy(chromaSoftness = it)) }, onCommit)
                        } else {
                            Text(
                                "Untuk klip atau gambar berlatar hijau/biru (mis. efek peluru): pilih warna layarnya.",
                                fontSize = 11.sp,
                                color = FatihColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    4 -> {
                        // ---------- Animasi (keyframe) ----------
                        val keys = KeyframeMath.keys(selected)
                        val local = (currentMs - selected.startMs).coerceIn(0L, selected.durationMs.coerceAtLeast(0L))
                        val atIdx = KeyframeMath.indexAt(keys, local)
                        if (keys.isEmpty()) {
                            Text(
                                "Belum ada animasi. Ketuk Mulai animasi, lalu geser waktu film dan ubah posisi, ukuran, putaran, " +
                                    "atau opasitas layer. Gerak di antara keyframe dihitung otomatis.",
                                fontSize = 11.sp,
                                color = FatihColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        } else {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                keys.forEachIndexed { i, k ->
                                    MiniChip("\u25c6 " + sec(k.timeMs), null, i == atIdx) { onSeek(selected.startMs + k.timeMs) }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onChange(KeyframeMath.addKeyframe(selected, currentMs)); onCommit() },
                                enabled = atIdx < 0 && keys.size < KeyframeMath.MAX_KEYFRAMES
                            ) {
                                Text(if (keys.isEmpty()) "Mulai animasi di sini" else "+ Keyframe di sini", fontSize = 12.sp)
                            }
                            if (atIdx >= 0) {
                                TextButton(onClick = { onChange(KeyframeMath.removeKeyframeAt(selected, currentMs)); onCommit() }) {
                                    Text("Hapus keyframe", fontSize = 12.sp, color = FatihColors.ShutterRed)
                                }
                            }
                            if (keys.isNotEmpty()) {
                                TextButton(onClick = { onChange(KeyframeMath.clearAnimation(selected, currentMs)); onCommit() }) {
                                    Text("Hapus animasi", fontSize = 12.sp, color = FatihColors.ShutterRed)
                                }
                            }
                        }
                        if (atIdx >= 0) {
                            val ease = keys[atIdx].ease
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Gerak ke keyframe berikutnya:", fontSize = 11.sp, color = FatihColors.TextSecondary)
                                MiniChip("Lurus", null, ease == KeyframeEase.LINEAR) {
                                    onChange(KeyframeMath.setEaseAt(selected, currentMs, KeyframeEase.LINEAR)); onCommit()
                                }
                                MiniChip("Halus", null, ease == KeyframeEase.SMOOTH) {
                                    onChange(KeyframeMath.setEaseAt(selected, currentMs, KeyframeEase.SMOOTH)); onCommit()
                                }
                                MiniChip("Tahan", null, ease == KeyframeEase.HOLD) {
                                    onChange(KeyframeMath.setEaseAt(selected, currentMs, KeyframeEase.HOLD)); onCommit()
                                }
                            }
                        } else if (keys.isNotEmpty()) {
                            Text(
                                "Ubah layer di pratinjau pada waktu lain untuk membuat keyframe baru.",
                                fontSize = 11.sp,
                                color = FatihColors.TextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    else -> {
                        val index = ordered.indexOfFirst { it.id == selected.id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onMove(selected.id, true) }, enabled = index > 0) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Ke depan")
                            }
                            IconButton(onClick = { onMove(selected.id, false) }, enabled = index in 0 until ordered.size - 1) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Ke belakang")
                            }
                            IconButton(onClick = { onChange(selected.copy(visible = !selected.visible)); onCommit() }) {
                                Icon(
                                    if (selected.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = if (selected.visible) "Sembunyikan" else "Tampilkan"
                                )
                            }
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus layer", tint = FatihColors.ShutterRed)
                            }
                            Text(
                                "Urutan ${index + 1} dari ${ordered.size} (1 = paling depan)",
                                fontSize = 11.sp,
                                color = FatihColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && selected != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus layer?") },
            text = { Text("\"${selected.name}\" dan berkasnya akan dihapus dari proyek.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(selected) }) {
                    Text("Hapus", color = FatihColors.ShutterRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun LSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(72.dp).padding(start = 4.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = range,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(
            display,
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.width(52.dp).padding(start = 4.dp)
        )
    }
}
