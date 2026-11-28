package com.fatih.stopmotion.ui.editor

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 * Panel penghapus latar green screen / blue screen. Perubahan langsung terlihat di pratinjau (onChange)
 * dan disimpan saat jari dilepas (onCommit). Foto asli tidak diubah.
 */
@Composable
fun ChromaSheet(
    chroma: ChromaState,
    onChange: (ChromaState) -> Unit,
    onCommit: () -> Unit,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
    ) {
        Text("Hapus latar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Untuk foto dengan layar hijau atau biru polos di belakang objek.",
            fontSize = 12.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Label("Warna layar")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Mati", Color(0xFF55555F), chroma.mode == ChromaMode.OFF) {
                onChange(chroma.copy(mode = ChromaMode.OFF)); onCommit()
            }
            ModeChip("Green screen", Color(0xFF1DB954), chroma.mode == ChromaMode.GREEN) {
                onChange(chroma.copy(mode = ChromaMode.GREEN)); onCommit()
            }
            ModeChip("Blue screen", Color(0xFF2F6FED), chroma.mode == ChromaMode.BLUE) {
                onChange(chroma.copy(mode = ChromaMode.BLUE)); onCommit()
            }
        }

        if (chroma.active) {
            Label("Penyesuaian")
            SliderRow("Toleransi", chroma.tolerance, "${(chroma.tolerance * 100).toInt()}%",
                onChange = { onChange(chroma.copy(tolerance = it)) }, onFinish = onCommit)
            SliderRow("Tepi halus", chroma.softness, "${(chroma.softness * 100).toInt()}%",
                onChange = { onChange(chroma.copy(softness = it)) }, onFinish = onCommit)
            Text(
                "Naikkan toleransi bila masih ada sisa warna layar; turunkan bila bagian objek ikut terhapus.",
                fontSize = 11.sp,
                color = FatihColors.TextSecondary
            )
        }

        run {
            Label("Latar pengganti")
            Text(
                "Dipakai di area yang terhapus: layar hijau/biru atau coretan Erase.",
                fontSize = 11.sp,
                color = FatihColors.TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChromaBg.names.forEachIndexed { i, name ->
                    val swatch = if (i == ChromaBg.IMAGE) FatihColors.Purple else Color(ChromaBg.color(i))
                    ModeChip(name, swatch, chroma.bgMode == i) {
                        onChange(chroma.copy(bgMode = i)); onCommit()
                    }
                }
            }
            if (chroma.bgMode == ChromaBg.IMAGE) {
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onPickBackground) {
                        Text(if (chroma.bgPath == null) "Pilih gambar" else "Ganti gambar")
                    }
                    if (chroma.bgPath != null) {
                        TextButton(onClick = onClearBackground) {
                            Text("Hapus gambar", color = FatihColors.ShutterRed)
                        }
                    }
                }
                if (chroma.bgPath == null) {
                    Text(
                        "Belum ada gambar, sementara latar berwarna hitam.",
                        fontSize = 11.sp,
                        color = FatihColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ikut diterapkan saat ekspor MP4 dan GIF. Pada pratinjau, gambar diproses lebih kecil agar cepat, " +
                    "jadi tepi hasil ekspor akan lebih halus.",
                fontSize = 11.sp,
                color = FatihColors.TextSecondary
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = FatihColors.Blue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
private fun ModeChip(label: String, swatch: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(swatch))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    display: String,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(30.dp)
        )
        Text(display, fontSize = 11.sp, color = FatihColors.TextSecondary, modifier = Modifier.width(44.dp))
    }
}
