package com.fatih.stopmotion.ui.editor

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.fx.FilterNames
import com.fatih.stopmotion.fx.FxState
import com.fatih.stopmotion.ui.theme.FatihColors
import java.util.Locale

/**
 * Panel efek proyek. Perubahan langsung terlihat di pratinjau (onChange),
 * dan disimpan ke database saat jari dilepas (onCommit).
 */
@Composable
fun EffectsSheet(
    fx: FxState,
    fps: Int,
    onChange: (FxState) -> Unit,
    onCommit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FatihColors.Surface)
            .padding(horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Efek film", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(
                onClick = { onChange(FxState()); onCommit() },
                enabled = !fx.isDefault
            ) { Text("Reset") }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup efek film")
            }
        }
        // Isi bisa digulir dan dibatasi tingginya supaya pratinjau tetap terlihat.
        Column(
            Modifier
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.30f).dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {

        Label("Filter")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterNames.forEachIndexed { i, name ->
                Chip(name, fx.filter == i) {
                    onChange(fx.copy(filter = i))
                    onCommit()
                }
            }
        }
        if (fx.filter != 0) {
            FxSlider("Kekuatan", fx.intensity, 0f..1f, "${(fx.intensity * 100).toInt()}%",
                onChange = { onChange(fx.copy(intensity = it)) }, onFinish = onCommit)
        }

        Label("Vignette")
        FxSlider("Gelap tepi", fx.vignette, 0f..1f, "${(fx.vignette * 100).toInt()}%",
            onChange = { onChange(fx.copy(vignette = it)) }, onFinish = onCommit)

        Label("Fade")
        FxSlider("Fade in", fx.fadeIn.toFloat(), 0f..60f, ticksLabel(fx.fadeIn, fps),
            onChange = { onChange(fx.copy(fadeIn = it.toInt())) }, onFinish = onCommit)
        FxSlider("Fade out", fx.fadeOut.toFloat(), 0f..60f, ticksLabel(fx.fadeOut, fps),
            onChange = { onChange(fx.copy(fadeOut = it.toInt())) }, onFinish = onCommit)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Chip("Ke hitam", !fx.fadeWhite) { onChange(fx.copy(fadeWhite = false)); onCommit() }
            Chip("Ke putih", fx.fadeWhite) { onChange(fx.copy(fadeWhite = true)); onCommit() }
        }
        Text(
            "Filter, vignette, dan fade berlaku untuk seluruh film dan tidak mengubah foto asli. Untuk koreksi warna, putar, atau potong satu foto, pakai Edit foto.",
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.padding(top = 10.dp)
        )
        }
    }
}

private fun ticksLabel(ticks: Int, fps: Int): String =
    if (ticks == 0) "Mati" else String.format(Locale.US, "%.1fs", ticks / fps.coerceAtLeast(1).toFloat())

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = FatihColors.Blue,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FxSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
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
            valueRange = range,
            modifier = Modifier.weight(1f).height(30.dp)
        )
        Text(display, fontSize = 11.sp, color = FatihColors.TextSecondary, modifier = Modifier.width(44.dp))
    }
}
