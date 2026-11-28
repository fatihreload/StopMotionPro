package com.fatih.stopmotion.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

object FatihColors {
    val Black = Color(0xFF0A0A0C)
    val Surface = Color(0xFF16161A)
    val SurfaceHigh = Color(0xFF212128)
    val ShutterRed = Color(0xFFE53935)
    val Purple = Color(0xFF8E5CFF)
    val Blue = Color(0xFF3D8BFF)
    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFF9A9AA6)

    /** Warna teks/ikon di atas tombol berwarna (merah, ungu, biru). Selalu terang. */
    val OnAccent = Color(0xFFFFFFFF)
}

@Composable
fun FatihTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // Semua pasangan warna ditetapkan eksplisit. Bila ada yang dibiarkan, Material memakai nilai bawaan
        // yang GELAP (mis. onTertiary), sehingga teks di tombol merah hampir hitam dan sulit dibaca.
        colorScheme = darkColorScheme(
            primary = FatihColors.Purple,
            onPrimary = FatihColors.OnAccent,
            primaryContainer = Color(0xFF3A2A6B),
            onPrimaryContainer = FatihColors.TextPrimary,
            inversePrimary = FatihColors.Purple,
            secondary = FatihColors.Blue,
            onSecondary = FatihColors.OnAccent,
            secondaryContainer = Color(0xFF1F3A66),
            onSecondaryContainer = FatihColors.TextPrimary,
            tertiary = FatihColors.ShutterRed,
            onTertiary = FatihColors.OnAccent,
            tertiaryContainer = Color(0xFF5C1F1E),
            onTertiaryContainer = FatihColors.TextPrimary,
            background = FatihColors.Black,
            onBackground = FatihColors.TextPrimary,
            surface = FatihColors.Surface,
            onSurface = FatihColors.TextPrimary,
            surfaceVariant = FatihColors.SurfaceHigh,
            onSurfaceVariant = FatihColors.TextSecondary,
            surfaceTint = FatihColors.Purple,
            inverseSurface = FatihColors.TextPrimary,
            inverseOnSurface = FatihColors.Black,
            error = FatihColors.ShutterRed,
            onError = FatihColors.OnAccent,
            errorContainer = Color(0xFF5C1F1E),
            onErrorContainer = FatihColors.TextPrimary,
            outline = Color(0xFF6B6B78),
            outlineVariant = Color(0xFF3A3A44),
            scrim = Color.Black,
            surfaceBright = Color(0xFF2B2B33),
            surfaceDim = FatihColors.Black,
            surfaceContainerLowest = FatihColors.Black,
            surfaceContainerLow = FatihColors.Surface,
            surfaceContainer = FatihColors.Surface,
            surfaceContainerHigh = FatihColors.SurfaceHigh,
            surfaceContainerHighest = Color(0xFF2B2B33)
        ),
    ) {
        // Tanpa Surface, warna konten bawaan Material adalah HITAM sehingga Text/Icon tidak terlihat di latar gelap.
        // Surface ini menetapkan latar dan warna teks/ikon bawaan untuk seluruh aplikasi.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = FatihColors.Black,
            contentColor = FatihColors.TextPrimary,
            content = content
        )
    }
}
