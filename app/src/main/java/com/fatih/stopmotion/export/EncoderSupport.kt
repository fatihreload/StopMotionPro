package com.fatih.stopmotion.export

import android.media.MediaCodecList
import android.media.MediaFormat

/** Memeriksa apakah perangkat punya encoder H.264 untuk ukuran tertentu (2K dan 4K tidak ada di semua perangkat). */
object EncoderSupport {

    /** Bila pengecekan gagal, dianggap didukung agar pengguna tidak terblokir tanpa alasan; encoder tetap akan memberi galat jelas. */
    fun supportsAvc(width: Int, height: Int, fps: Int): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } &&
                runCatching {
                    val v = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                    (v.isSizeSupported(width, height) && v.areSizeAndRateSupported(width, height, fps.toDouble())) ||
                        (v.isSizeSupported(height, width) && v.areSizeAndRateSupported(height, width, fps.toDouble()))
                }.getOrDefault(false)
        }
    } catch (e: Exception) {
        true
    }
}
