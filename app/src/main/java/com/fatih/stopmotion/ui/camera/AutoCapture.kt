package com.fatih.stopmotion.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlin.math.roundToInt

enum class AutoState { IDLE, RUNNING, PAUSED }

private val Presets = listOf(1, 2, 5, 10)

@Composable
fun AutoCapturePanel(
    state: AutoState,
    intervalSec: Int,
    custom: Boolean,
    total: Int,
    taken: Int,
    onPreset: (Int) -> Unit,
    onCustom: () -> Unit,
    onIntervalChange: (Int) -> Unit,
    onTotalChange: (Int) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editable = state == AutoState.IDLE
    Column(
        modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FatihColors.Surface.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("Auto Capture", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Interval", fontSize = 12.sp, color = FatihColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.forEach { p ->
                AutoChip("${p}s", !custom && intervalSec == p, editable) { onPreset(p) }
            }
            AutoChip("Custom", custom, editable, onCustom)
        }
        if (custom) {
            Text("Custom: $intervalSec detik", fontSize = 12.sp, color = FatihColors.TextSecondary)
            Slider(
                value = intervalSec.toFloat(),
                onValueChange = { onIntervalChange(it.roundToInt()) },
                valueRange = 1f..60f,
                enabled = editable
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("Jumlah foto: $total", fontSize = 12.sp, color = FatihColors.TextSecondary)
        Slider(
            value = total.toFloat(),
            onValueChange = { onTotalChange(it.roundToInt()) },
            valueRange = 1f..500f,
            enabled = editable
        )
        if (state != AutoState.IDLE) {
            Text(
                "Progres: $taken / $total" + if (state == AutoState.PAUSED) "  (dijeda)" else "",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = FatihColors.Blue
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                AutoState.RUNNING -> Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(containerColor = FatihColors.Purple, contentColor = FatihColors.OnAccent)
                ) {
                    Icon(Icons.Filled.Pause, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Pause")
                }
                else -> Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (state == AutoState.PAUSED) "Lanjut" else "Start")
                }
            }
            OutlinedButton(onClick = onStop, enabled = state != AutoState.IDLE) {
                Icon(Icons.Filled.Stop, null)
                Spacer(Modifier.size(6.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun AutoChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
