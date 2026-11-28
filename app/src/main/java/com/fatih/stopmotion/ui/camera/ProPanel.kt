package com.fatih.stopmotion.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.ui.theme.FatihColors
import kotlin.math.roundToInt

private enum class ProTab(val label: String) {
    FOCUS("Fokus"), WB("AW"), EV("EV"), ZOOM("Zoom"), SHUTTER("Shutter"), ISO("ISO")
}

/**
 * Panel kamera manual di atas pratinjau: Fokus (4 mode), AW, kompensasi eksposur (EV), Zoom 1x-10x, Shutter, dan ISO.
 * Saat slider disentuh, panel menjadi hampir transparan sehingga efeknya terlihat langsung di pratinjau.
 */
@Composable
fun ProPanel(
    state: ProState,
    caps: ProCaps,
    onChange: (ProState) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val bgAlpha by animateFloatAsState(if (dragging) 0.15f else 0.92f, label = "proPanelAlpha")

    Column(
        modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FatihColors.Surface.copy(alpha = bgAlpha))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProTab.entries.forEachIndexed { i, t -> ProChip(t.label, tab == i, true) { tab = i } }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup")
            }
        }
        val current = ProTab.entries[tab.coerceIn(0, ProTab.entries.size - 1)]
        when (current) {
            ProTab.FOCUS -> FocusTab(state, caps, onChange) { dragging = it }
            ProTab.WB -> WbTab(state, caps, onChange)
            ProTab.EV -> EvTab(state, caps, onChange) { dragging = it }
            ProTab.ZOOM -> ZoomTab(state, caps, onChange) { dragging = it }
            ProTab.SHUTTER -> ShutterTab(state, caps, onChange) { dragging = it }
            ProTab.ISO -> IsoTab(state, caps, onChange) { dragging = it }
        }
    }
}

@Composable
private fun FocusTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit, onDrag: (Boolean) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusMode.entries.forEach { m ->
            ProChip(m.label, state.focus == m, true) { onChange(state.copy(focus = m)) }
        }
    }
    Hint(state.focus.caption)
    if (state.focus == FocusMode.MANUAL) {
        if (caps.hasManualFocus) {
            // kiri = dekat, kanan = jauh (tak hingga)
            val t = 1f - (state.focusDistance / caps.minFocusDiopter).coerceIn(0f, 1f)
            ProSlider(
                "Jarak", t, 0f..1f, 0, "Dekat \u2194 Jauh", onDrag
            ) { onChange(state.copy(focusDistance = (1f - it) * caps.minFocusDiopter)) }
        } else {
            Hint("Kamera ini tidak mendukung fokus manual.")
        }
    }
    if (state.focus == FocusMode.LOCK_MANUAL && !caps.hasManualExposure) {
        Hint("Eksposur manual tidak didukung kamera ini; hanya fokus yang dikunci.")
    }
}

@Composable
private fun WbTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WbMode.entries.forEach { w ->
            ProChip(w.label, state.wb == w, caps.supportsWb(w)) { onChange(state.copy(wb = w)) }
        }
    }
    Hint(if (state.wb == WbMode.LOCKED) "White balance dikunci pada nilai saat ini." else "Pilih sesuai jenis cahaya di ruangan.")
}

@Composable
private fun EvTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit, onDrag: (Boolean) -> Unit) {
    if (!caps.hasEv) {
        Hint("Kompensasi eksposur tidak didukung kamera ini.")
        return
    }
    val active = state.focus == FocusMode.AUTO || state.focus == FocusMode.MANUAL
    ProSlider(
        "EV", state.ev.coerceIn(caps.evMin, caps.evMax).toFloat(), caps.evMin.toFloat()..caps.evMax.toFloat(),
        (caps.evMax - caps.evMin - 1).coerceAtLeast(0), ProMath.evLabel(state.ev, caps.evStep), onDrag, active
    ) { onChange(state.copy(ev = it.roundToInt())) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ProChip("Reset EV", state.ev == 0, active) { onChange(state.copy(ev = 0)) }
    }
    if (!active) Hint("EV tidak aktif saat eksposur dikunci atau manual.")
}

@Composable
private fun ZoomTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit, onDrag: (Boolean) -> Unit) {
    ProSlider(
        "Zoom", state.zoom, ProMath.ZOOM_MIN..ProMath.ZOOM_MAX, 0, ProMath.zoomLabel(state.zoom), onDrag
    ) { onChange(state.copy(zoom = it)) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1f, 2f, 3f, 5f, 10f).forEach { z ->
            ProChip(ProMath.zoomLabel(z), state.zoom == z, z <= caps.zoomMax + 0.01f) { onChange(state.copy(zoom = z)) }
        }
    }
    if (caps.zoomMax < ProMath.ZOOM_MAX) {
        Hint("Kamera ini maksimal ${ProMath.zoomLabel(caps.zoomMax)}; nilai di atasnya dipotong.")
    }
}

@Composable
private fun ShutterTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit, onDrag: (Boolean) -> Unit) {
    if (!caps.hasManualExposure) {
        Hint("Shutter manual tidak didukung kamera ini.")
        return
    }
    if (!state.focus.manualExposure) {
        Hint("Shutter manual aktif pada mode Lock Focus + Manual Exposure.")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            ProChip("Aktifkan mode itu", false, true) { onChange(state.copy(focus = FocusMode.LOCK_MANUAL)) }
        }
        return
    }
    val stops = ProMath.shutterStops(caps.shutterMinNs, caps.shutterMaxNs)
    if (stops.size < 2) {
        Hint("Rentang shutter kamera ini terlalu sempit untuk diatur.")
        return
    }
    val value = if (state.shutterNs > 0L) state.shutterNs else ProMath.defaultShutter(stops)
    val idx = ProMath.nearestIndex(stops, value)
    ProSlider(
        "Shutter", idx.toFloat(), 0f..(stops.size - 1).toFloat(), stops.size - 2,
        ProMath.shutterLabel(stops[idx]), onDrag
    ) { onChange(state.copy(shutterNs = stops[it.roundToInt().coerceIn(0, stops.size - 1)])) }
    Hint("Kiri = cepat (gelap), kanan = lambat (terang).")
}

@Composable
private fun IsoTab(state: ProState, caps: ProCaps, onChange: (ProState) -> Unit, onDrag: (Boolean) -> Unit) {
    if (!caps.hasManualExposure) {
        Hint("ISO manual tidak didukung kamera ini.")
        return
    }
    if (!state.focus.manualExposure) {
        Hint("ISO manual aktif pada mode Lock Focus + Manual Exposure.")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            ProChip("Aktifkan mode itu", false, true) { onChange(state.copy(focus = FocusMode.LOCK_MANUAL)) }
        }
        return
    }
    val stops = ProMath.isoStops(caps.isoMin, caps.isoMax)
    if (stops.size < 2) {
        Hint("Rentang ISO kamera ini terlalu sempit untuk diatur.")
        return
    }
    val value = if (state.iso > 0) state.iso else ProMath.defaultIso(stops)
    val idx = ProMath.nearestIsoIndex(stops, value)
    ProSlider(
        "ISO", idx.toFloat(), 0f..(stops.size - 1).toFloat(), stops.size - 2, "ISO ${stops[idx]}", onDrag
    ) { onChange(state.copy(iso = stops[it.roundToInt().coerceIn(0, stops.size - 1)])) }
    Hint("ISO rendah = gambar bersih; ISO tinggi = lebih terang tapi berbintik.")
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = FatihColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
    )
}

@Composable
private fun ProChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
private fun ProSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onDrag: (Boolean) -> Unit,
    enabled: Boolean = true,
    onValue: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, modifier = Modifier.width(58.dp).padding(start = 4.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onDrag(true); onValue(it) },
            onValueChangeFinished = { onDrag(false) },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f).height(28.dp)
        )
        Text(
            display,
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(78.dp).padding(start = 4.dp)
        )
    }
}
