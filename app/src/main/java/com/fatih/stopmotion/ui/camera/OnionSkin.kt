package com.fatih.stopmotion.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fatih.stopmotion.data.FrameEntity
import com.fatih.stopmotion.ui.theme.FatihColors
import java.io.File
import kotlin.math.roundToInt

private fun onionColorFilter(index: Int): ColorFilter? {
    val (r, g, b) = when (index) {
        1 -> Triple(1f, 0.3f, 0.3f)
        2 -> Triple(0.35f, 0.55f, 1f)
        3 -> Triple(0.3f, 1f, 0.4f)
        else -> return null
    }
    // Ubah ke luminansi lalu kalikan warna tint agar detail tetap terlihat.
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
                0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
                0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

/**
 * Overlay onion skin. refFrames diurutkan dari yang TERDEKAT ke yang terjauh.
 * Frame terdekat paling jelas, yang lebih jauh makin transparan.
 * Overlay hanya digambar di layar; foto diambil dari ImageCapture sehingga tidak ikut tersimpan.
 */
@Composable
fun OnionSkinOverlay(
    refFrames: List<FrameEntity>,
    opacity: Float,
    colorIndex: Int,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    if (refFrames.isEmpty() || opacity <= 0f) return
    val context = LocalContext.current
    val colorFilter = remember(colorIndex) { onionColorFilter(colorIndex) }
    val n = refFrames.size
    Box(modifier.graphicsLayer { scaleX = if (mirror) -1f else 1f }) {
        // Gambar yang terjauh lebih dulu, yang terdekat di paling atas.
        for (i in n - 1 downTo 0) {
            val frame = refFrames[i]
            val alpha = opacity * (n - i) / n
            val request = remember(frame.filePath) {
                ImageRequest.Builder(context)
                    .data(File(frame.filePath))
                    .size(720, 960)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = alpha,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun OnionPanel(
    enabled: Boolean,
    opacity: Float,
    frameCount: Int,
    colorIndex: Int,
    onEnabledChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onOpacityFinished: () -> Unit,
    onFrameCountChange: (Int) -> Unit,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FatihColors.Surface.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Onion Skin", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Text(
            "Transparansi ${(opacity * 100).roundToInt()}%",
            fontSize = 12.sp,
            color = FatihColors.TextSecondary
        )
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            onValueChangeFinished = onOpacityFinished,
            valueRange = 0f..1f,
            enabled = enabled
        )
        Text("Jumlah frame sebelumnya", fontSize = 12.sp, color = FatihColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (n in 1..5) {
                SelectChip("$n", frameCount == n, enabled) { onFrameCountChange(n) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Warna overlay", fontSize = 12.sp, color = FatihColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Asli", "Merah", "Biru", "Hijau").forEachIndexed { i, label ->
                SelectChip(label, colorIndex == i, enabled) { onColorChange(i) }
            }
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected && enabled) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
