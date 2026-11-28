package com.fatih.stopmotion.ui.editor

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.FrameEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Stage4Screen(projectId: Long, container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frames by remember { mutableStateOf<List<FrameEntity>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableIntStateOf(0) }
    var effect by remember { mutableIntStateOf(0) }
    var audioUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var audioLabel by remember { mutableStateOf("Belum ada audio") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var audioPlaying by remember { mutableStateOf(false) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            audioUri = uri
            audioLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio dipilih"
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }
    LaunchedEffect(projectId) { frames = container.repository.observeFrames(projectId).first() }
    val frame = frames.getOrNull(selected)
    val matrix = remember(brightness, contrast, effect) {
        val b = brightness
        val c = contrast
        val offset = 128f * (1f - c)
        val base = floatArrayOf(c,0f,0f,0f,offset, 0f,c,0f,0f,offset, 0f,0f,c,0f,offset, 0f,0f,0f,1f,0f)
        for (i in listOf(0,1,2)) base[i*5] = c * b
        if (effect == 1) { base[0]=c*b*1.12f; base[6]=c*b*0.96f; base[12]=c*b*0.82f }
        if (effect == 2) { base[0]=c*b*0.82f; base[6]=c*b*1.02f; base[12]=c*b*1.15f }
        ColorFilter.colorMatrix(ColorMatrix(base))
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Image Editor • Audio • Efek") }, navigationIcon = { IconButton(onClick=onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription="Kembali") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("IMAGE EDITOR", fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            if (frame != null) {
                Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black, RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) {
                    AsyncImage(model=File(frame.filePath), contentDescription="Pratinjau hasil edit", contentScale=ContentScale.Fit,
                        colorFilter=matrix, modifier=Modifier.fillMaxSize().rotate(rotation.toFloat()))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                    OutlinedButton(onClick={selected=(selected-1).coerceAtLeast(0)}) { Text("Frame −") }
                    Text("${selected+1} / ${frames.size}", modifier=Modifier.weight(1f))
                    OutlinedButton(onClick={selected=(selected+1).coerceAtMost(frames.lastIndex)}) { Text("Frame +") }
                }
            } else Text("Belum ada frame. Ambil foto terlebih dahulu.")
            Text("Brightness", fontSize=14.sp); Slider(value=brightness, onValueChange={brightness=it}, valueRange=0.5f..1.8f)
            Text("Contrast", fontSize=14.sp); Slider(value=contrast, onValueChange={contrast=it}, valueRange=0.5f..1.8f)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={rotation=(rotation+90)%360}) { Text("Putar 90°") }
                OutlinedButton(onClick={effect=0; brightness=1f; contrast=1f; rotation=0}) { Text("Reset") }
            }
            Text("Filter", fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterButton("Original", effect==0) { effect=0 }
                FilterButton("Warm", effect==1) { effect=1 }
                FilterButton("Cool", effect==2) { effect=2 }
            }
            Text("Catatan: pengaturan editor saat ini adalah pratinjau; belum diterapkan permanen ke file frame.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("AUDIO STUDIO", fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            Text(audioLabel, maxLines=2)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={audioPicker.launch(arrayOf("audio/*"))}) { Text("Pilih audio") }
                OutlinedButton(enabled=audioUri!=null, onClick={
                    if (audioPlaying) { player?.pause(); audioPlaying=false }
                    else {
                        runCatching {
                            player?.release()
                            player=MediaPlayer().apply { setDataSource(context, audioUri!!); setOnCompletionListener { audioPlaying=false }; prepare(); start() }
                            audioPlaying=true
                        }.onFailure { audioPlaying=false }
                    }
                }) { Icon(if(audioPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription=null); Text(if(audioPlaying) "Pause" else "Play") }
            }
            Text("Audio dapat dipilih dan diputar untuk pratinjau. Penyatuan audio ke video dan pemotongan audio akan tersedia pada tahap rendering.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("EFEK", fontWeight=FontWeight.Bold, color=MaterialTheme.colorScheme.primary)
            Text("Filter warna dan rotasi tampil pada pratinjau frame yang dipilih. Efek transisi antarframe serta ekspor hasil edit akan ditambahkan pada tahap berikutnya.", style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick=onClick) { Text(label) } else OutlinedButton(onClick=onClick) { Text(label) }
}
