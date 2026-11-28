package com.fatih.stopmotion.data

import com.fatih.stopmotion.fx.KeyframeCodec
import org.json.JSONArray
import org.json.JSONException
import com.fatih.stopmotion.fx.EraseCodec
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

data class FrameMeta(val file: String, val hold: Int, val createdAt: Long, val erase: String? = null)

data class AudioMeta(
    val file: String,
    val name: String,
    val kind: Int,
    val durationMs: Long,
    val startMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val volume: Float,
    val fadeInMs: Long,
    val fadeOutMs: Long,
    val muted: Boolean,
    val createdAt: Long
)

data class LayerMeta(
    val name: String,
    val kind: Int,
    /** Gambar: nama entri sumber. Video: awalan folder frame ("layers/002/"). */
    val file: String,
    val frameCount: Int,
    val frameRate: Float,
    val srcWidth: Int,
    val srcHeight: Int,
    val zOrder: Int,
    val startMs: Long,
    val durationMs: Long,
    val loopClip: Boolean,
    val posX: Float,
    val posY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val chromaMode: Int,
    val chromaTolerance: Float,
    val chromaSoftness: Float,
    val visible: Boolean,
    val createdAt: Long,
    /** Keyframe animasi yang sudah dinormalisasi, atau null. */
    val keyframes: String? = null
)

data class ParsedBackup(
    val project: ProjectEntity,
    val frames: List<FrameMeta>,
    val audio: List<AudioMeta>,
    /** Nama entri gambar latar di dalam ZIP, atau null bila proyek tidak memakai gambar latar. */
    val backgroundFile: String? = null,
    val layers: List<LayerMeta> = emptyList()
)

/**
 * Isi manifest.json di dalam file cadangan (ZIP). Nama entri file dibuat dari indeks, bukan dari
 * path asli, sehingga cadangan aman dibuka (tidak ada path dari file luar yang dipakai).
 */
object BackupManifest {
    const val FORMAT = "FatihStopMotion"
    const val VERSION = 1
    const val ENTRY = "manifest.json"
    private const val MAX_FRAMES = 20_000
    private const val MAX_TRACKS = 200
    private const val MAX_LAYERS = 40

    fun frameEntry(index: Int): String = String.format(Locale.US, "frames/%05d.jpg", index + 1)

    fun audioEntry(index: Int, source: String): String {
        val ext = File(source).extension.lowercase(Locale.US)
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } } ?: "bin"
        return String.format(Locale.US, "audio/%03d.%s", index + 1, ext)
    }

    /** Sumber layer gambar. Layer video tidak menyimpan video asli, hanya frame JPEG hasil ekstraksi. */
    fun layerImageEntry(index: Int, source: String): String {
        val ext = File(source).extension.lowercase(Locale.US)
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } } ?: "png"
        return String.format(Locale.US, "layers/%03d/src.%s", index + 1, ext)
    }

    fun layerFramesPrefix(index: Int): String = String.format(Locale.US, "layers/%03d/", index + 1)

    fun layerFrameEntry(index: Int, frameNumber: Int): String =
        String.format(Locale.US, "layers/%03d/%05d.jpg", index + 1, frameNumber)

    fun backgroundEntry(source: String): String {
        val ext = File(source).extension.lowercase(Locale.US)
            .takeIf { it.length in 1..5 && it.all { c -> c.isLetterOrDigit() } } ?: "jpg"
        return "background.$ext"
    }

    fun toJson(
        project: ProjectEntity,
        frames: List<FrameEntity>,
        tracks: List<AudioTrackEntity>,
        backgroundEntry: String? = null,
        layers: List<LayerEntity> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("name", project.name)
        root.put("fps", project.fps)
        root.put("onionEnabled", project.onionEnabled)
        root.put("onionOpacity", project.onionOpacity.toDouble())
        root.put("onionFrameCount", project.onionFrameCount)
        root.put("onionColor", project.onionColor)
        root.put("fxFilter", project.fxFilter)
        root.put("fxIntensity", project.fxIntensity.toDouble())
        root.put("fxVignette", project.fxVignette.toDouble())
        root.put("fxFadeIn", project.fxFadeIn)
        root.put("fxFadeOut", project.fxFadeOut)
        root.put("fxFadeWhite", project.fxFadeWhite)
        root.put("chromaMode", project.chromaMode)
        root.put("chromaTolerance", project.chromaTolerance.toDouble())
        root.put("chromaSoftness", project.chromaSoftness.toDouble())
        root.put("chromaBgMode", project.chromaBgMode)
        root.put("chromaBgFile", backgroundEntry ?: "")
        val fa = JSONArray()
        frames.forEachIndexed { i, f ->
            fa.put(
                JSONObject().put("file", frameEntry(i)).put("hold", f.holdCount).put("createdAt", f.createdAt)
                    .also { o -> if (!f.eraseData.isNullOrEmpty()) o.put("erase", f.eraseData) }
            )
        }
        root.put("frames", fa)
        val aa = JSONArray()
        tracks.forEachIndexed { i, t ->
            aa.put(
                JSONObject()
                    .put("file", audioEntry(i, t.filePath))
                    .put("name", t.name)
                    .put("kind", t.kind)
                    .put("durationMs", t.durationMs)
                    .put("startMs", t.startMs)
                    .put("trimStartMs", t.trimStartMs)
                    .put("trimEndMs", t.trimEndMs)
                    .put("volume", t.volume.toDouble())
                    .put("fadeInMs", t.fadeInMs)
                    .put("fadeOutMs", t.fadeOutMs)
                    .put("muted", t.muted)
                    .put("createdAt", t.createdAt)
            )
        }
        root.put("audio", aa)
        val la = JSONArray()
        layers.forEachIndexed { i, l ->
            la.put(
                JSONObject()
                    .put("name", l.name)
                    .put("kind", l.kind)
                    .put("file", if (l.kind == LayerKind.IMAGE) layerImageEntry(i, l.filePath) else layerFramesPrefix(i))
                    .put("frames", l.frameCount)
                    .put("frameRate", l.frameRate.toDouble())
                    .put("w", l.srcWidth)
                    .put("h", l.srcHeight)
                    .put("z", l.zOrder)
                    .put("startMs", l.startMs)
                    .put("durationMs", l.durationMs)
                    .put("loop", l.loopClip)
                    .put("x", l.posX.toDouble())
                    .put("y", l.posY.toDouble())
                    .put("scale", l.scale.toDouble())
                    .put("rotation", l.rotation.toDouble())
                    .put("opacity", l.opacity.toDouble())
                    .put("chromaMode", l.chromaMode)
                    .put("chromaTolerance", l.chromaTolerance.toDouble())
                    .put("chromaSoftness", l.chromaSoftness.toDouble())
                    .put("visible", l.visible)
                    .put("createdAt", l.createdAt)
                    .also { o -> if (!l.keyframes.isNullOrEmpty()) o.put("keyframes", l.keyframes) }
            )
        }
        root.put("layers", la)
        return root.toString()
    }

    fun parse(json: String): ParsedBackup {
        try {
            val o = JSONObject(json)
            if (o.optString("format") != FORMAT) throw IOException("Bukan file cadangan Fatih Stop Motion")
            if (o.optInt("version", 0) !in 1..VERSION) {
                throw IOException("Versi cadangan tidak didukung. Perbarui aplikasi.")
            }
            val fa = o.getJSONArray("frames")
            if (fa.length() > MAX_FRAMES) throw IOException("File cadangan tidak valid")
            val frames = (0 until fa.length()).map {
                val f = fa.getJSONObject(it)
                FrameMeta(
                    f.getString("file"),
                    f.optInt("hold", 1).coerceIn(1, 10),
                    f.optLong("createdAt", 0L),
                    // dinormalkan lewat EraseCodec: data rusak atau berlebihan tidak ikut terbawa
                    EraseCodec.encode(EraseCodec.decode(f.optString("erase", "").takeIf { it.length <= 400_000 }))
                )
            }
            val aa = o.optJSONArray("audio") ?: JSONArray()
            if (aa.length() > MAX_TRACKS) throw IOException("File cadangan tidak valid")
            val audio = (0 until aa.length()).map {
                val a = aa.getJSONObject(it)
                val duration = a.optLong("durationMs", 0L).coerceAtLeast(1L)
                val tin = a.optLong("trimStartMs", 0L).coerceIn(0L, duration - 1)
                val tout = a.optLong("trimEndMs", duration).coerceIn(tin + 1, duration)
                AudioMeta(
                    file = a.getString("file"),
                    name = ProjectNames.clean(a.optString("name", "Audio")).ifEmpty { "Audio" },
                    kind = a.optInt("kind", AudioKind.IMPORT).coerceIn(0, 2),
                    durationMs = duration,
                    startMs = a.optLong("startMs", 0L).coerceAtLeast(0L),
                    trimStartMs = tin,
                    trimEndMs = tout,
                    volume = a.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
                    fadeInMs = a.optLong("fadeInMs", 0L).coerceAtLeast(0L),
                    fadeOutMs = a.optLong("fadeOutMs", 0L).coerceAtLeast(0L),
                    muted = a.optBoolean("muted", false),
                    createdAt = a.optLong("createdAt", 0L)
                )
            }
            val la = o.optJSONArray("layers") ?: JSONArray()
            if (la.length() > MAX_LAYERS) throw IOException("File cadangan tidak valid")
            val layers = (0 until la.length()).map {
                val l = la.getJSONObject(it)
                val kind = l.optInt("kind", LayerKind.IMAGE).coerceIn(0, 1)
                LayerMeta(
                    name = ProjectNames.clean(l.optString("name", "Layer")).ifEmpty { "Layer" },
                    kind = kind,
                    file = l.optString("file", ""),
                    frameCount = if (kind == LayerKind.VIDEO) l.optInt("frames", 1).coerceIn(1, 240) else 1,
                    frameRate = l.optDouble("frameRate", 0.0).toFloat().coerceIn(0f, 60f),
                    srcWidth = l.optInt("w", 1).coerceIn(1, 16384),
                    srcHeight = l.optInt("h", 1).coerceIn(1, 16384),
                    zOrder = l.optInt("z", it).coerceIn(0, 10_000),
                    startMs = l.optLong("startMs", 0L).coerceAtLeast(0L),
                    durationMs = l.optLong("durationMs", 1000L).coerceAtLeast(100L),
                    loopClip = l.optBoolean("loop", false),
                    posX = l.optDouble("x", 0.5).toFloat().coerceIn(-0.5f, 1.5f),
                    posY = l.optDouble("y", 0.5).toFloat().coerceIn(-0.5f, 1.5f),
                    scale = l.optDouble("scale", 0.5).toFloat().coerceIn(0.03f, 4f),
                    rotation = l.optDouble("rotation", 0.0).toFloat().coerceIn(-360f, 360f),
                    opacity = l.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                    chromaMode = l.optInt("chromaMode", 0).coerceIn(0, 2),
                    chromaTolerance = l.optDouble("chromaTolerance", 0.4).toFloat().coerceIn(0f, 1f),
                    chromaSoftness = l.optDouble("chromaSoftness", 0.3).toFloat().coerceIn(0f, 1f),
                    visible = l.optBoolean("visible", true),
                    createdAt = l.optLong("createdAt", 0L),
                    keyframes = KeyframeCodec.encode(KeyframeCodec.decode(l.optString("keyframes", "")))
                )
            }
            val project = ProjectEntity(
                name = ProjectNames.clean(o.optString("name", "Proyek")).ifEmpty { "Proyek" },
                createdAt = 0L,
                updatedAt = 0L,
                fps = o.optInt("fps", 12).coerceIn(1, 60),
                onionEnabled = o.optBoolean("onionEnabled", true),
                onionOpacity = o.optDouble("onionOpacity", 0.5).toFloat().coerceIn(0f, 1f),
                onionFrameCount = o.optInt("onionFrameCount", 1).coerceIn(1, 5),
                onionColor = o.optInt("onionColor", 0).coerceIn(0, 3),
                fxFilter = o.optInt("fxFilter", 0).coerceIn(0, 7),
                fxIntensity = o.optDouble("fxIntensity", 1.0).toFloat().coerceIn(0f, 1f),
                fxVignette = o.optDouble("fxVignette", 0.0).toFloat().coerceIn(0f, 1f),
                fxFadeIn = o.optInt("fxFadeIn", 0).coerceIn(0, 120),
                fxFadeOut = o.optInt("fxFadeOut", 0).coerceIn(0, 120),
                fxFadeWhite = o.optBoolean("fxFadeWhite", false),
                chromaMode = o.optInt("chromaMode", 0).coerceIn(0, 2),
                chromaTolerance = o.optDouble("chromaTolerance", 0.4).toFloat().coerceIn(0f, 1f),
                chromaSoftness = o.optDouble("chromaSoftness", 0.3).toFloat().coerceIn(0f, 1f),
                chromaBgMode = o.optInt("chromaBgMode", 0).coerceIn(0, 4)
            )
            val bgFile = o.optString("chromaBgFile", "").takeIf { it.isNotEmpty() }
            return ParsedBackup(project, frames, audio, bgFile, layers)
        } catch (e: JSONException) {
            throw IOException("File cadangan rusak")
        }
    }
}
