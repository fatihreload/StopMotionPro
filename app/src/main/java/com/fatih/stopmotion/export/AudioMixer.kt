package com.fatih.stopmotion.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.fatih.stopmotion.audio.trackGain
import com.fatih.stopmotion.data.AudioTrackEntity
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/**
 * Mencampur semua track audio sesuai posisi, potongan, volume, dan fade di timeline,
 * lalu menyandikannya ke AAC (M4A). Semua track diubah dulu ke PCM 44,1 kHz stereo di file sementara.
 */
object AudioMixer {
    private const val RATE = 44100
    private const val BLOCK = 1024

    /** Penyampel ulang linear bergaya streaming; keluaran selalu stereo 16-bit little-endian. */
    private class Resampler(private val srcRate: Int, private val channels: Int, private val out: OutputStream) {
        private val step = srcRate.toDouble() / RATE
        private var pos = 1.0
        private var prevL = 0f
        private var prevR = 0f
        private var buf = ByteArray(0)

        fun feed(s: ShortArray, count: Int) {
            val ch = channels.coerceAtLeast(1)
            val frames = count / ch
            if (frames <= 0) return
            val estimate = ((frames / step) + 4).toInt() * 4
            if (buf.size < estimate) buf = ByteArray(estimate)
            var bi = 0
            while (pos < frames) {
                val i = pos.toInt()
                val f = (pos - i).toFloat()
                val l0 = if (i == 0) prevL else s[(i - 1) * ch].toFloat()
                val r0 = if (i == 0) prevR else s[(i - 1) * ch + (if (ch > 1) 1 else 0)].toFloat()
                val l1 = s[i * ch].toFloat()
                val r1 = s[i * ch + (if (ch > 1) 1 else 0)].toFloat()
                val l = (l0 + (l1 - l0) * f).toInt().coerceIn(-32768, 32767)
                val r = (r0 + (r1 - r0) * f).toInt().coerceIn(-32768, 32767)
                if (bi + 4 > buf.size) { out.write(buf, 0, bi); bi = 0 }
                buf[bi++] = (l and 0xFF).toByte(); buf[bi++] = ((l shr 8) and 0xFF).toByte()
                buf[bi++] = (r and 0xFF).toByte(); buf[bi++] = ((r shr 8) and 0xFF).toByte()
                pos += step
            }
            out.write(buf, 0, bi)
            pos -= frames
            prevL = s[(frames - 1) * ch].toFloat()
            prevR = s[(frames - 1) * ch + (if (ch > 1) 1 else 0)].toFloat()
        }
    }

    /** Decode file audio apa pun yang didukung perangkat ke PCM mentah. Mengembalikan false bila tidak ada jalur audio. */
    private fun decodeToPcm(src: File, dest: File, cancelled: () -> Boolean): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(src.absolutePath)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { track = i; break }
            }
            if (track < 0) return false
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(format, null, null, 0)
            dec.start()

            var rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else RATE
            var ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            BufferedOutputStream(dest.outputStream(), 1 shl 16).use { os ->
                var resampler = Resampler(rate, ch, os)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var idle = 0
                var temp = ShortArray(0)
                while (!outputDone) {
                    if (cancelled()) throw CancellationException("Dibatalkan")
                    if (!inputDone) {
                        val inIdx = dec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = dec.getInputBuffer(inIdx)!!
                            val n = extractor.readSampleData(inBuf, 0)
                            if (n < 0) {
                                dec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = dec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (inputDone && ++idle > 500) throw IOException("Decoder audio tidak merespons")
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = dec.outputFormat
                            val r = if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) of.getInteger(MediaFormat.KEY_SAMPLE_RATE) else rate
                            val c = if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else ch
                            if (r != rate || c != ch) {
                                rate = r; ch = c
                                resampler = Resampler(rate, ch, os)
                            }
                        }
                        outIdx >= 0 -> {
                            idle = 0
                            val ob = dec.getOutputBuffer(outIdx)
                            if (ob != null && info.size > 0) {
                                ob.position(info.offset)
                                ob.limit(info.offset + info.size)
                                val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                                val count = sb.remaining()
                                if (temp.size < count) temp = ShortArray(count)
                                sb.get(temp, 0, count)
                                resampler.feed(temp, count)
                            }
                            dec.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        }
                    }
                }
            }
            return true
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private class PcmTrack(val track: AudioTrackEntity, val file: File) {
        val raf = RandomAccessFile(file, "r")
        val startF = track.startMs * RATE / 1000
        val lenF = (track.trimEndMs - track.trimStartMs) * RATE / 1000
        val trimF = track.trimStartMs * RATE / 1000
    }

    private fun mixBlock(tracks: List<PcmTrack>, framePos: Long, n: Int, out: ByteArray) {
        val acc = IntArray(n * 2)
        for (t in tracks) {
            val oStart = maxOf(framePos, t.startF)
            val oEnd = minOf(framePos + n, t.startF + t.lenF)
            if (oEnd <= oStart) continue
            val count = (oEnd - oStart).toInt()
            val src = ByteArray(count * 4)
            val fileOffset = (t.trimF + (oStart - t.startF)) * 4
            try {
                if (fileOffset < t.raf.length()) {
                    t.raf.seek(fileOffset)
                    var read = 0
                    while (read < src.size) {
                        val r = t.raf.read(src, read, src.size - read)
                        if (r <= 0) break
                        read += r
                    }
                }
            } catch (e: IOException) {
                continue
            }
            val g0 = trackGain(t.track, (oStart - t.startF) * 1000 / RATE)
            val g1 = trackGain(t.track, (oEnd - t.startF) * 1000 / RATE)
            val base = (oStart - framePos).toInt()
            for (i in 0 until count) {
                val g = g0 + (g1 - g0) * i / count
                val l = ((src[i * 4].toInt() and 0xFF) or (src[i * 4 + 1].toInt() shl 8)).toShort().toInt()
                val r = ((src[i * 4 + 2].toInt() and 0xFF) or (src[i * 4 + 3].toInt() shl 8)).toShort().toInt()
                acc[(base + i) * 2] += (l * g).toInt()
                acc[(base + i) * 2 + 1] += (r * g).toInt()
            }
        }
        for (i in 0 until n * 2) {
            val v = acc[i].coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
    }

    /** Menulis hasil campuran sepanjang totalMs ke file M4A. Mengembalikan false bila tidak ada track yang bisa dipakai. */
    fun mixToM4a(
        tracks: List<AudioTrackEntity>,
        totalMs: Long,
        dest: File,
        tmpDir: File,
        cancelled: () -> Boolean,
        progress: (Float) -> Unit
    ): Boolean {
        tmpDir.mkdirs()
        val pcm = ArrayList<PcmTrack>()
        val tmpFiles = ArrayList<File>()
        try {
            tracks.forEachIndexed { i, t ->
                if (cancelled()) throw CancellationException("Dibatalkan")
                val f = File(tmpDir, "mix_${t.id}.pcm")
                tmpFiles.add(f)
                val ok = try {
                    decodeToPcm(File(t.filePath), f, cancelled)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false // track rusak dilewati, ekspor tetap jalan
                }
                if (ok && f.length() > 0) pcm.add(PcmTrack(t, f))
                progress(0.4f * (i + 1) / tracks.size)
            }
            if (pcm.isEmpty()) return false

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            var sink: EncoderSink? = null
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                sink = EncoderSink(codec, MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))

                val totalFrames = totalMs * RATE / 1000
                val bytes = ByteArray(BLOCK * 4)
                var framePos = 0L
                while (framePos < totalFrames) {
                    if (cancelled()) throw CancellationException("Dibatalkan")
                    val n = minOf(BLOCK.toLong(), totalFrames - framePos).toInt()
                    mixBlock(pcm, framePos, n, bytes)
                    var inIdx = -1
                    var spins = 0
                    while (inIdx < 0) {
                        sink.drain(false)
                        inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx < 0 && ++spins > 1000) throw IOException("Encoder audio macet")
                    }
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    inBuf.clear()
                    inBuf.put(bytes, 0, n * 4)
                    codec.queueInputBuffer(inIdx, 0, n * 4, framePos * 1_000_000L / RATE, 0)
                    framePos += n
                    if ((framePos / BLOCK) % 64L == 0L) progress(0.4f + 0.6f * framePos / totalFrames)
                }
                var eosIdx = -1
                var spins = 0
                while (eosIdx < 0) {
                    sink.drain(false)
                    eosIdx = codec.dequeueInputBuffer(10_000)
                    if (eosIdx < 0 && ++spins > 1000) throw IOException("Encoder audio macet")
                }
                codec.queueInputBuffer(eosIdx, 0, 0, totalFrames * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                sink.drain(true)
                progress(1f)
                return true
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                sink?.finish()
            }
        } finally {
            pcm.forEach { runCatching { it.raf.close() } }
            tmpFiles.forEach { it.delete() }
        }
    }
}
