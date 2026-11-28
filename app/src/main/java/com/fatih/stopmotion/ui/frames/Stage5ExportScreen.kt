package com.fatih.stopmotion.ui.frames

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.FrameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Stage5ExportScreen(projectId: Long, container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fps by remember { mutableIntStateOf(12) }
    var size by remember { mutableStateOf("HD 720p") }
    var status by remember { mutableStateOf("Siap export") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Export Video") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Video rendering", style = MaterialTheme.typography.titleLarge)
            Text("MP4 • H.264 • Simpan ke galeri", fontSize = 14.sp)
            Text("FPS: $fps")
            Slider(value = fps.toFloat(), onValueChange = { fps = it.toInt().coerceIn(1, 30) }, valueRange = 1f..30f, steps = 28)
            Text("Resolusi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SD 480p", "HD 720p", "FHD 1080p").forEach { option ->
                    FilterChip(selected = size == option, onClick = { size = option }, label = { Text(option) })
                }
            }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                busy = true; progress = 0f; status = "Menyiapkan frame…"
                scope.launch {
                    val frames = withContext(Dispatchers.IO) { container.repository.getFrames(projectId) }
                    if (frames.isEmpty()) { status = "Tambahkan frame terlebih dahulu"; busy = false; return@launch }
                    val result = withContext(Dispatchers.IO) { runCatching { Mp4Renderer.export(context, frames, fps, size) { p -> progress = p } } }
                    status = result.fold({ "Berhasil disimpan: ${it.second}" }, { "Export gagal: ${it.message ?: "error tidak diketahui"}" })
                    busy = false
                }
            }) { Text(if (busy) "Merender…" else "Export MP4") }
            if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Text("GIF akan tersedia pada pembaruan export berikutnya.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private object Mp4Renderer {
    fun export(context: Context, frames: List<FrameEntity>, fps: Int, preset: String, onProgress: (Float) -> Unit): Pair<Uri, String> {
        val (w, h) = when (preset) { "SD 480p" -> 854 to 480; "FHD 1080p" -> 1920 to 1080; else -> 1280 to 720 }
        val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, "FatihStopMotion_${System.currentTimeMillis()}.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FatihStopMotion") }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Tidak dapat membuat file video")
        var codec: MediaCodec? = null; var muxer: MediaMuxer? = null
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply { setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface); setInteger(MediaFormat.KEY_BIT_RATE, (w*h*4).coerceAtMost(12_000_000)); setInteger(MediaFormat.KEY_FRAME_RATE, fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC); codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface(); codec.start()
            val p = MediaMuxer(context.contentResolver.openFileDescriptor(uri, "rw")!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = p
            val info = MediaCodec.BufferInfo(); var track = -1; var started = false
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val canvas = android.graphics.Rect(0, 0, w, h)
            frames.forEachIndexed { index, frame ->
                val bitmap = BitmapFactory.decodeFile(frame.filePath) ?: error("Frame ${index+1} tidak dapat dibaca")
                val c = surface.lockCanvas(null)
                try { c.drawColor(Color.BLACK); val scale = minOf(w.toFloat()/bitmap.width, h.toFloat()/bitmap.height); val dw=(bitmap.width*scale).toInt(); val dh=(bitmap.height*scale).toInt(); val left=(w-dw)/2; val top=(h-dh)/2; c.drawBitmap(bitmap, null, android.graphics.Rect(left,top,left+dw,top+dh), paint) } finally { surface.unlockCanvasAndPost(c); bitmap.recycle() }
                drain(codec, muxer, info, false) { t -> if (!started) { track=muxer.addTrack(t); muxer.start(); started=true } }
                Thread.sleep(1000L / fps.coerceAtLeast(1))
                onProgress((index+1).toFloat()/frames.size)
                repeat((frame.holdCount.coerceIn(1,10)-1)) { val bmp = BitmapFactory.decodeFile(frame.filePath) ?: error("Frame tidak dapat dibaca"); val c2=surface.lockCanvas(null); try { c2.drawColor(Color.BLACK); val sc=minOf(w.toFloat()/bmp.width,h.toFloat()/bmp.height); val dw=(bmp.width*sc).toInt(); val dh=(bmp.height*sc).toInt(); c2.drawBitmap(bmp,null,android.graphics.Rect((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),paint) } finally { surface.unlockCanvasAndPost(c2); bmp.recycle() }; Thread.sleep(1000L / fps.coerceAtLeast(1)); drain(codec,muxer,info,false) { t -> if (!started) { track=muxer.addTrack(t); muxer.start(); started=true } } }
            }
            codec.signalEndOfInputStream()
            var done=false
            while(!done) { val out=codec.dequeueOutputBuffer(info,10_000); when { out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { if (!started) { track=muxer.addTrack(codec.outputFormat); muxer.start(); started=true } }; out>=0 -> { val buffer=codec.getOutputBuffer(out)!!; if(info.size>0 && started) { buffer.position(info.offset); buffer.limit(info.offset+info.size); muxer.writeSampleData(track,buffer,info) }; done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0; codec.releaseOutputBuffer(out,false) } } }
            return uri to "Movies/FatihStopMotion"
        } catch (e: Exception) { context.contentResolver.delete(uri,null,null); throw e }
        finally { runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { muxer?.stop() }; runCatching { muxer?.release() } }
    }
    private fun drain(codec: MediaCodec, muxer: MediaMuxer, info: MediaCodec.BufferInfo, end: Boolean, onFormat: (MediaFormat)->Unit) {
        while(true) { val out=codec.dequeueOutputBuffer(info,0); when { out==MediaCodec.INFO_TRY_AGAIN_LATER -> return; out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat); out>=0 -> { val b=codec.getOutputBuffer(out)!!; if(info.size>0) { b.position(info.offset); b.limit(info.offset+info.size); runCatching { muxer.writeSampleData(0,b,info) } }; codec.releaseOutputBuffer(out,false) } } }
    }
}
