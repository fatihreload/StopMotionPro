package com.fatih.stopmotion.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import android.util.Size
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import com.fatih.stopmotion.fx.ChromaState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

/**
 * Live preview CameraX + use case ImageCapture.
 * Mode COMPATIBLE (TextureView) dipakai agar overlay onion skin (Tahap 2)
 * bisa digambar di atas preview dengan transparansi.
 */
@Composable
fun CameraPreview(
    lensFacing: Int,
    flashMode: Int,
    captureQuality: CaptureQuality,
    /** Bila aktif, setiap frame pratinjau diproses chroma key dan dikirim lewat onLiveFrame. */
    chroma: ChromaState,
    onLiveFrame: (android.graphics.Bitmap?) -> Unit,
    onReady: (imageCapture: ImageCapture, hasFlash: Boolean) -> Unit,
    /** Dipanggil setiap kamera dipasang (ulang); dipakai untuk fokus, AW, EV, zoom, shutter, dan ISO manual. */
    onCamera: (androidx.camera.core.Camera) -> Unit = {},
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnCamera by rememberUpdatedState(onCamera)
    val currentOnLive by rememberUpdatedState(onLiveFrame)

    // Pengaturan terbaru dibaca analyzer tanpa memasang ulang kamera setiap slider digeser.
    val chromaRef = remember { AtomicReference(chroma) }
    SideEffect { chromaRef.set(chroma) }
    val live = chroma.active
    val mirror = lensFacing == CameraSelector.LENS_FACING_FRONT
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    // Ukuran foto mengikuti pilihan kualitas; pratinjau tetap memakai pemilih bawaan.
    val captureSelector = remember(captureQuality) {
        val builder = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        if (captureQuality.hasTarget) {
            builder.setResolutionStrategy(
                ResolutionStrategy(
                    Size(captureQuality.width, captureQuality.height),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
        }
        builder.build()
    }
    val imageCapture = remember(captureQuality) {
        ImageCapture.Builder()
            .setResolutionSelector(captureSelector)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    LaunchedEffect(flashMode, imageCapture) { imageCapture.flashMode = flashMode }

    LaunchedEffect(lensFacing, captureQuality, live) {
        try {
            val provider = context.awaitCameraProvider()
            providerRef[0] = provider
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            if (!provider.hasCamera(selector)) {
                currentOnError("Kamera ini tidak tersedia di perangkat.")
                return@LaunchedEffect
            }
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            provider.unbindAll()
            var camera: androidx.camera.core.Camera? = null
            if (live) {
                // Analisis kecil (sekitar 640x480) agar chroma key tetap lancar saat membidik.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                val mainExecutor = ContextCompat.getMainExecutor(context)
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        val state = chromaRef.get()
                        if (state.active) {
                            val bmp = LiveChroma.process(proxy, state, mirror)
                            mainExecutor.execute { currentOnLive(bmp) }
                        }
                    } catch (e: Throwable) {
                        // frame dilewati; pratinjau biasa tetap tampil
                    } finally {
                        proxy.close()
                    }
                }
                camera = try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, analysis)
                } catch (e: IllegalArgumentException) {
                    // Kombinasi use case tidak didukung kamera ini: tetap bisa memotret, tanpa pratinjau chroma.
                    provider.unbindAll()
                    currentOnLive(null)
                    currentOnError("Pratinjau chroma langsung tidak didukung kamera ini. Latar tetap dihapus saat edit dan ekspor.")
                    null
                }
            }
            if (camera == null) camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            currentOnCamera(camera)
            currentOnReady(imageCapture, camera.cameraInfo.hasFlashUnit())
        } catch (e: Exception) {
            currentOnError("Kamera gagal dibuka: ${e.message}")
        }
    }

    // Saat chroma dimatikan, buang frame terakhir agar pratinjau normal kembali tampil.
    LaunchedEffect(live) { if (!live) currentOnLive(null) }

    // Lepas kamera saat meninggalkan halaman (navigasi tidak menghancurkan Activity).
    DisposableEffect(Unit) {
        onDispose { runCatching { providerRef[0]?.unbindAll() } }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
