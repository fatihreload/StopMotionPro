package com.fatih.stopmotion.export

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.fatih.stopmotion.data.AudioTrackEntity
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

/** Menerima keluaran sebuah encoder dan menulisnya ke muxer satu-jalur. */
internal class EncoderSink(private val codec: MediaCodec, private val muxer: MediaMuxer) {
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    var started = false
        private set

    fun drain(endOfStream: Boolean) {
        var idle = 0
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return
                if (++idle > 500) throw IOException("Encoder tidak merespons")
                continue
            }
            idle = 0
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
                started = true
                continue
            }
            if (idx < 0) continue
            val buf = codec.getOutputBuffer(idx)
            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (buf != null && info.size > 0 && !isConfig && started) {
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                muxer.writeSampleData(track, buf, info)
            }
            codec.releaseOutputBuffer(idx, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
        }
    }

    fun finish() {
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}

object Mp4Exporter {

    /**
     * Frame digambar oleh FrameComposer, diubah ke YUV420 di CPU, lalu disandikan H.264.
     * Jalur CPU dipilih karena berjalan sama di semua perangkat dan waktu tiap frame tepat.
     * Bila ada audio, video dan audio dibuat terpisah lalu digabung (remux) berurutan menurut waktu.
     */
    fun export(
        composer: FrameComposer,
        fps: Int,
        quality: Int,
        audioTracks: List<AudioTrackEntity>,
        dest: File,
        tmpDir: File,
        cancelled: () -> Boolean,
        progress: (Float, String) -> Unit
    ) {
        tmpDir.mkdirs()
        val wantAudio = audioTracks.isNotEmpty()
        val videoFile = if (wantAudio) File(tmpDir, "video_only.mp4") else dest
        val audioFile = File(tmpDir, "audio_only.m4a")
        val videoShare = if (wantAudio) 0.7f else 1f
        try {
            encodeVideo(composer, fps, quality, videoFile, cancelled) { progress(it * videoShare, "Menyandikan video") }
            if (wantAudio) {
                val totalMs = composer.totalTicks * 1000L / fps
                val hasAudio = AudioMixer.mixToM4a(audioTracks, totalMs, audioFile, tmpDir, cancelled) {
                    progress(0.7f + it * 0.25f, "Mencampur audio")
                }
                if (hasAudio) {
                    progress(0.96f, "Menggabungkan video dan audio")
                    remux(videoFile, audioFile, dest)
                } else {
                    videoFile.copyTo(dest, overwrite = true)
                }
            }
            progress(1f, "Selesai")
        } catch (e: Throwable) {
            dest.delete()
            throw e
        } finally {
            if (wantAudio) {
                videoFile.delete()
                audioFile.delete()
            }
        }
    }

    private fun encodeVideo(
        composer: FrameComposer,
        fps: Int,
        quality: Int,
        out: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit
    ) {
        val w = composer.width
        val h = composer.height
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate(w, h, fps, quality))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        // Batasi bitrate ke rentang yang didukung encoder ini (penting untuk 2K/4K).
        runCatching {
            val range = codec.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                .videoCapabilities.bitrateRange
            format.setInteger(MediaFormat.KEY_BIT_RATE, range.clamp(format.getInteger(MediaFormat.KEY_BIT_RATE)))
        }
        var sink: EncoderSink? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            sink = EncoderSink(codec, MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))

            val argb = IntArray(w * h)
            val yPlane = ByteArray(w * h)
            val uPlane = ByteArray(w * h / 4)
            val vPlane = ByteArray(w * h / 4)
            var lastSig: List<Int>? = null
            val total = composer.totalTicks

            for (tick in 0 until total) {
                if (cancelled()) throw CancellationException("Dibatalkan")
                // Gambar dan konversi YUV hanya bila isi tick berbeda dari tick sebelumnya.
                val sig = composer.signature(tick)
                if (sig != lastSig) {
                    val bmp = composer.render(tick)
                    toYuv(bmp, argb, yPlane, uPlane, vPlane)
                }
                lastSig = sig

                var inIdx = -1
                var spins = 0
                while (inIdx < 0) {
                    sink.drain(false)
                    inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx < 0 && ++spins > 1000) throw IOException("Encoder video macet")
                }
                val image = codec.getInputImage(inIdx)
                    ?: throw IOException("Perangkat ini tidak mendukung penyandian video dari gambar")
                fillImage(image, w, h, yPlane, uPlane, vPlane)
                codec.queueInputBuffer(inIdx, 0, w * h * 3 / 2, tick * 1_000_000L / fps, 0)
                onProgress((tick + 1f) / total)
            }

            var eosIdx = -1
            var spins = 0
            while (eosIdx < 0) {
                sink.drain(false)
                eosIdx = codec.dequeueInputBuffer(10_000)
                if (eosIdx < 0 && ++spins > 1000) throw IOException("Encoder video macet")
            }
            codec.queueInputBuffer(eosIdx, 0, 0, total * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            sink.drain(true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            sink?.finish()
        }
    }

    /** ARGB -> YUV420 planar (BT.601, rentang terbatas). Chroma dirata-ratakan dari blok 2x2. */
    private fun toYuv(bmp: Bitmap, argb: IntArray, yP: ByteArray, uP: ByteArray, vP: ByteArray) {
        val w = bmp.width
        val h = bmp.height
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        val cw = w / 2
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                var rs = 0
                var gs = 0
                var bs = 0
                for (dy in 0..1) {
                    for (dx in 0..1) {
                        val p = argb[(y + dy) * w + x + dx]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        yP[(y + dy) * w + x + dx] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte()
                        rs += r; gs += g; bs += b
                    }
                }
                val r = rs shr 2
                val g = gs shr 2
                val b = bs shr 2
                val ci = (y / 2) * cw + x / 2
                uP[ci] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                vP[ci] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                x += 2
            }
            y += 2
        }
    }

    private fun fillImage(img: Image, w: Int, h: Int, y: ByteArray, u: ByteArray, v: ByteArray) {
        val planes = img.planes
        copyPlane(planes[0], w, h, y)
        copyPlane(planes[1], w / 2, h / 2, u)
        copyPlane(planes[2], w / 2, h / 2, v)
    }

    private fun copyPlane(p: Image.Plane, pw: Int, ph: Int, data: ByteArray) {
        val buf: ByteBuffer = p.buffer
        val rowStride = p.rowStride
        val pixelStride = p.pixelStride
        if (pixelStride == 1 && rowStride == pw) {
            buf.position(0)
            buf.put(data, 0, pw * ph)
            return
        }
        for (row in 0 until ph) {
            val base = row * rowStride
            if (pixelStride == 1) {
                buf.position(base)
                buf.put(data, row * pw, pw)
            } else {
                for (col in 0 until pw) buf.put(base + col * pixelStride, data[row * pw + col])
            }
        }
    }

    /** Menggabungkan jalur video dan audio ke satu MP4 dengan urutan sampel menurut waktu. */
    private fun remux(video: File, audio: File, dest: File) {
        val ev = MediaExtractor()
        val ea = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            ev.setDataSource(video.absolutePath)
            ea.setDataSource(audio.absolutePath)
            val vt = firstTrack(ev, "video/") ?: throw IOException("Jalur video tidak ditemukan")
            val at = firstTrack(ea, "audio/") ?: throw IOException("Jalur audio tidak ditemukan")
            ev.selectTrack(vt)
            ea.selectTrack(at)
            muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vi = muxer.addTrack(ev.getTrackFormat(vt))
            val ai = muxer.addTrack(ea.getTrackFormat(at))
            muxer.start()
            started = true

            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val vTime = ev.sampleTime
                val aTime = ea.sampleTime
                if (vTime < 0 && aTime < 0) break
                val useVideo = vTime >= 0 && (aTime < 0 || vTime <= aTime)
                val ex = if (useVideo) ev else ea
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    ex.advance()
                    continue
                }
                info.set(0, size, ex.sampleTime, ex.sampleFlags)
                muxer.writeSampleData(if (useVideo) vi else ai, buf, info)
                ex.advance()
            }
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { ev.release() }
            runCatching { ea.release() }
        }
    }

    private fun firstTrack(ex: MediaExtractor, prefix: String): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) return i
        }
        return null
    }
}
