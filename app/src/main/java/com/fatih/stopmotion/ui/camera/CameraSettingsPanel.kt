package com.fatih.stopmotion.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.ui.theme.FatihColors

private val focusModes = listOf("Auto Focus", "Manual Focus", "Lock Focus & Exposure", "Lock Focus + Manual Exposure")
private val wbModes = listOf("Auto", "Locked", "Incandescent", "Fluorescent", "Daylight", "Cloudy", "Twilight", "Shade")

@Composable
fun CameraSettingsPanel(
    value: CameraManualSettings,
    onChange: (CameraManualSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().heightIn(max = 420.dp)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
            .background(FatihColors.SurfaceHigh, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Kamera Manual", color = Color.White, fontSize = 18.sp)
            TextButton(onClick = onClose) { Text("Tutup") }
        }
        Text("Focus", color = FatihColors.Blue)
        focusModes.forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(selected = value.focusMode == index, onClick = { onChange(value.copy(focusMode = index)) })
                Text(label, color = Color.White, fontSize = 13.sp)
            }
        }
        if (value.focusMode == 1 || value.focusMode == 2 || value.focusMode == 3) {
            ValueSlider("Manual focus", value.focusDistance, 0f..10f, "%.1f".format(value.focusDistance)) {
                onChange(value.copy(focusDistance = it))
            }
        }
        Text("AWB / White balance", color = FatihColors.Blue)
        varDummyDropdown(value.whiteBalance, wbModes) { onChange(value.copy(whiteBalance = it)) }
        ValueSlider("Exposure compensation", value.exposureCompensation.toFloat(), -4f..4f, "${value.exposureCompensation} EV") {
            onChange(value.copy(exposureCompensation = it.toInt()))
        }
        ValueSlider("Zoom", value.zoom, 1f..10f, "%.1fx".format(value.zoom)) {
            onChange(value.copy(zoom = it))
        }
        Text("ISO ${value.iso}", color = Color.White)
        Slider(value.iso.toFloat(), { onChange(value.copy(iso = it.toInt())) }, valueRange = 50f..6400f)
        Text("Shutter ${shutterLabel(value.shutterSeconds)}", color = Color.White)
        Slider(
            value = shutterToSlider(value.shutterSeconds),
            onValueChange = { onChange(value.copy(shutterSeconds = sliderToShutter(it))) },
            valueRange = 0f..1f
        )
        Text("ISO dan shutter diterapkan pada mode manual exposure; kemampuan aktual mengikuti kamera perangkat.", color = FatihColors.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun varDummyDropdown(selected: Int, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded.value = true }) { Text(options.getOrElse(selected) { "Auto" }) }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(index); expanded.value = false })
            }
        }
    }
}

@Composable
private fun ValueSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, valueLabel: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 13.sp)
            Text(valueLabel, color = FatihColors.Blue, fontSize = 13.sp)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

private fun shutterToSlider(seconds: Double): Float = ((kotlin.math.ln(seconds.coerceIn(1.0 / 8000.0, 1.0)) - kotlin.math.ln(1.0 / 8000.0)) / (0.0 - kotlin.math.ln(1.0 / 8000.0))).toFloat()
private fun sliderToShutter(value: Float): Double = kotlin.math.exp(kotlin.math.ln(1.0 / 8000.0) + value * (0.0 - kotlin.math.ln(1.0 / 8000.0)))
private fun shutterLabel(seconds: Double): String = if (seconds < 1.0) "1/${(1.0 / seconds).toInt()}s" else "${seconds.toInt()}s"
