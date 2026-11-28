package com.fatih.stopmotion.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.fx.ChromaBg
import com.fatih.stopmotion.fx.ChromaMode
import com.fatih.stopmotion.fx.ChromaState
import com.fatih.stopmotion.ui.theme.FatihColors

/**
 * Panel hapus latar (chroma key) yang ringkas, diletakkan di atas pratinjau (bukan lembar penuh) agar hasilnya
 * tetap terlihat. Saat slider disentuh, panel menjadi hampir transparan sehingga efeknya terlihat langsung.
 * Dipakai di kamera dan di Studio Animasi.
 */
@Composable
fun ChromaPanel(
    chroma: ChromaState,
    onChange: (ChromaState) -> Unit,
    onCommit: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 = layar, 1 = latar pengganti
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(if (dragging) 0.15f else 0.92f, label = "chromaPanelAlpha")

    Column(
        modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FatihColors.Surface.copy(alpha = bgAlpha))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (collapsed) {
                Text(
                    "Hapus latar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniChip("Layar", null, tab == 0) { tab = 0 }
                    MiniChip("Latar", null, tab == 1) { tab = 1 }
                }
            }
            IconButton(onClick = { collapsed = !collapsed }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (collapsed) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (collapsed) "Buka panel" else "Ciutkan panel"
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup")
            }
        }

        if (!collapsed) {
            if (tab == 0) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniChip("Mati", Color(0xFF55555F), chroma.mode == ChromaMode.OFF) {
                        onChange(chroma.copy(mode = ChromaMode.OFF)); onCommit()
                    }
                    MiniChip("Hijau", Color(0xFF1DB954), chroma.mode == ChromaMode.GREEN) {
                        onChange(chroma.copy(mode = ChromaMode.GREEN)); onCommit()
                    }
                    MiniChip("Biru", Color(0xFF2F6FED), chroma.mode == ChromaMode.BLUE) {
                        onChange(chroma.copy(mode = ChromaMode.BLUE)); onCommit()
                    }
                }
                if (chroma.active) {
                    PanelSlider(
                        "Toleransi", chroma.tolerance,
                        onDrag = { dragging = it },
                        onChange = { onChange(chroma.copy(tolerance = it)) },
                        onFinish = onCommit
                    )
                    PanelSlider(
                        "Tepi halus", chroma.softness,
                        onDrag = { dragging = it },
                        onChange = { onChange(chroma.copy(softness = it)) },
                        onFinish = onCommit
                    )
                } else {
                    Text(
                        "Pilih Hijau atau Biru sesuai warna layar di belakang objek.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            } else {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChromaBg.names.forEachIndexed { i, name ->
                        val swatch = if (i == ChromaBg.IMAGE) FatihColors.Purple else Color(ChromaBg.color(i))
                        MiniChip(name, swatch, chroma.bgMode == i) {
                            onChange(chroma.copy(bgMode = i)); onCommit()
                        }
                    }
                }
                if (chroma.bgMode == ChromaBg.IMAGE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onPickBackground, modifier = Modifier.height(36.dp)) {
                            Text(if (chroma.bgPath == null) "Pilih gambar" else "Ganti gambar", fontSize = 12.sp)
                        }
                        if (chroma.bgPath != null) {
                            TextButton(onClick = onClearBackground) {
                                Text("Hapus", fontSize = 12.sp, color = FatihColors.ShutterRed)
                            }
                        }
                    }
                } else {
                    Text(
                        "Dipakai juga di area yang dihapus dengan Erase.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun MiniChip(label: String, swatch: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (swatch != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(4.dp))
        }
        // Satu baris saja: pada panel sempit teks tidak boleh turun huruf demi huruf.
        Text(
            label,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PanelSlider(
    label: String,
    value: Float,
    onDrag: (Boolean) -> Unit,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(70.dp).padding(start = 4.dp))
        Slider(
            value = value,
            onValueChange = { onDrag(true); onChange(it) },
            onValueChangeFinished = { onDrag(false); onFinish() },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(
            "${(value * 100).toInt()}%",
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.width(40.dp).padding(start = 4.dp)
        )
    }
}
