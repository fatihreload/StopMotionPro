package com.fatih.stopmotion.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.ui.theme.FatihColors

/** Panel kualitas foto (SD sampai 4K) dan suara shutter. */
@Composable
fun CapturePanel(
    qualityIndex: Int,
    shutterOn: Boolean,
    enabled: Boolean,
    onQualityChange: (Int) -> Unit,
    onShutterChange: (Boolean) -> Unit,
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
        Text("Kualitas foto", fontWeight = FontWeight.Bold)
        Text(
            "Makin tinggi, foto makin tajam tetapi file dan ekspor makin berat.",
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).alpha(if (enabled) 1f else 0.5f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CaptureQuality.entries.forEachIndexed { i, q ->
                val selected = i == qualityIndex
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
                        .clickable(enabled = enabled) { onQualityChange(i) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(q.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                    Text(q.detail, fontSize = 10.sp, color = if (selected) FatihColors.OnAccent else FatihColors.TextSecondary)
                }
            }
        }
        Text(
            "Bila kamera tidak punya ukuran persis itu, dipakai ukuran terdekat yang tersedia.",
            fontSize = 11.sp,
            color = FatihColors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Suara shutter", fontWeight = FontWeight.Medium)
                Text("Bunyi klik kamera setiap foto diambil", fontSize = 11.sp, color = FatihColors.TextSecondary)
            }
            Switch(checked = shutterOn, onCheckedChange = onShutterChange)
        }
    }
}
