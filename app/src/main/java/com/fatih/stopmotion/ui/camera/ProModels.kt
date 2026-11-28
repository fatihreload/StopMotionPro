package com.fatih.stopmotion.ui.camera

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Mode fokus dan eksposur kamera. */
enum class FocusMode(val label: String, val caption: String) {
    AUTO("Auto Focus", "Fokus dan eksposur otomatis."),
    MANUAL("Manual Focus", "Atur fokus sendiri dengan slider; eksposur tetap otomatis."),
    LOCK("Lock Focus & Exposure", "Fokus dan eksposur dikunci dari tengah bingkai, mencegah kedip antar frame."),
    LOCK_MANUAL("Lock Focus + Manual Exposure", "Fokus dikunci; atur Shutter dan ISO sendiri di tab Shutter dan ISO.");

    val manualExposure: Boolean get() = this == LOCK_MANUAL
    val locksFocus: Boolean get() = this == LOCK || this == LOCK_MANUAL
}

/** Auto white balance. awbMode memakai nilai CameraMetadata.CONTROL_AWB_MODE_*. */
enum class WbMode(val label: String, val awbMode: Int, val lock: Boolean) {
    AUTO("Auto", 1, false),
    LOCKED("Locked", 1, true),
    INCANDESCENT("Incandescent", 2, false),
    FLUORESCENT("Fluorescent", 3, false),
    DAYLIGHT("Daylight", 5, false),
    CLOUDY("Cloudy", 6, false),
    TWILIGHT("Twilight", 7, false),
    SHADE("Shade", 8, false)
}

/** Pengaturan kamera yang dipilih pengguna. Disimpan antar sesi. */
data class ProState(
    val focus: FocusMode = FocusMode.AUTO,
    /** Jarak fokus manual dalam dioptri: 0 = tak hingga, makin besar makin dekat. */
    val focusDistance: Float = 0f,
    val wb: WbMode = WbMode.AUTO,
    /** Indeks kompensasi eksposur (satuan langkah kamera). */
    val ev: Int = 0,
    val zoom: Float = 1f,
    /** Lama rana dalam nanodetik. 0 = pakai nilai awal. */
    val shutterNs: Long = 0L,
    /** 0 = pakai nilai awal. */
    val iso: Int = 0
) {
    val isDefault: Boolean get() = this == ProState()
}

object ProStateCodec {
    fun encode(s: ProState): String = String.format(
        Locale.US, "%d,%s,%d,%d,%s,%d,%d",
        s.focus.ordinal, s.focusDistance.toString(), s.wb.ordinal, s.ev, s.zoom.toString(), s.shutterNs, s.iso
    )

    /** Data rusak atau tidak lengkap menghasilkan pengaturan bawaan. */
    fun decode(text: String?): ProState {
        if (text.isNullOrBlank()) return ProState()
        val f = text.split(',')
        if (f.size != 7) return ProState()
        val focusIdx = f[0].trim().toIntOrNull()
        val dist = f[1].trim().toFloatOrNull()
        val wbIdx = f[2].trim().toIntOrNull()
        val ev = f[3].trim().toIntOrNull()
        val zoom = f[4].trim().toFloatOrNull()
        val shutter = f[5].trim().toLongOrNull()
        val iso = f[6].trim().toIntOrNull()
        if (focusIdx == null || dist == null || wbIdx == null || ev == null || zoom == null || shutter == null || iso == null) {
            return ProState()
        }
        return ProState(
            focus = FocusMode.entries.getOrElse(focusIdx) { FocusMode.AUTO },
            focusDistance = dist.coerceIn(0f, 50f),
            wb = WbMode.entries.getOrElse(wbIdx) { WbMode.AUTO },
            ev = ev.coerceIn(-50, 50),
            zoom = zoom.coerceIn(ProMath.ZOOM_MIN, ProMath.ZOOM_MAX),
            shutterNs = shutter.coerceAtLeast(0L),
            iso = iso.coerceAtLeast(0)
        )
    }
}

/** Kemampuan kamera yang sedang aktif. Nilai bawaan = tidak ada kemampuan khusus. */
data class ProCaps(
    val manualSensor: Boolean = false,
    val isoMin: Int = 0,
    val isoMax: Int = 0,
    val shutterMinNs: Long = 0L,
    val shutterMaxNs: Long = 0L,
    /** Dioptri fokus terdekat; 0 = fokus tetap (tidak bisa manual). */
    val minFocusDiopter: Float = 0f,
    /** Mode AWB yang tersedia. Kosong = tidak diketahui, semua dianggap ada. */
    val awbModes: Set<Int> = emptySet(),
    val evMin: Int = 0,
    val evMax: Int = 0,
    val evStep: Float = 0f,
    val zoomMin: Float = 1f,
    val zoomMax: Float = 10f
) {
    val hasManualExposure: Boolean
        get() = manualSensor && isoMax > 0 && isoMax >= isoMin && shutterMaxNs > shutterMinNs
    val hasManualFocus: Boolean get() = minFocusDiopter > 0f
    val hasEv: Boolean get() = evMax > evMin
    fun supportsWb(w: WbMode): Boolean = awbModes.isEmpty() || w.awbMode in awbModes
}

object ProMath {
    const val ZOOM_MIN = 1f
    const val ZOOM_MAX = 10f

    /** Penyebut 1/n detik, dari yang tercepat. */
    private val SHUTTER_DENOMINATORS = listOf(4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4)
    private val ISO_STOPS = listOf(50, 100, 200, 400, 800, 1600, 3200)

    fun shutterStops(minNs: Long, maxNs: Long): List<Long> {
        if (maxNs <= minNs) return emptyList()
        return SHUTTER_DENOMINATORS.map { 1_000_000_000L / it }.filter { it in minNs..maxNs }
    }

    fun isoStops(min: Int, max: Int): List<Int> {
        if (max <= 0 || max < min) return emptyList()
        val stops = ISO_STOPS.filter { it in min..max }
        return if (stops.isEmpty()) listOf(min.coerceAtLeast(1), max).distinct() else stops
    }

    /** Nilai awal yang masuk akal untuk stop motion: sekitar 1/60 detik. */
    fun defaultShutter(stops: List<Long>): Long =
        stops.minByOrNull { abs(it - 1_000_000_000L / 60) } ?: 0L

    fun defaultIso(stops: List<Int>): Int = stops.minByOrNull { abs(it - 100) } ?: 0

    fun nearestIndex(stops: List<Long>, ns: Long): Int {
        if (stops.isEmpty()) return 0
        var best = 0
        for (i in stops.indices) if (abs(stops[i] - ns) < abs(stops[best] - ns)) best = i
        return best
    }

    fun nearestIsoIndex(stops: List<Int>, iso: Int): Int {
        if (stops.isEmpty()) return 0
        var best = 0
        for (i in stops.indices) if (abs(stops[i] - iso) < abs(stops[best] - iso)) best = i
        return best
    }

    fun shutterLabel(ns: Long): String = when {
        ns <= 0L -> "Otomatis"
        ns >= 500_000_000L -> String.format(Locale.US, "%.1f s", ns / 1e9)
        else -> "1/${(1e9 / ns).roundToInt()}"
    }

    fun evLabel(index: Int, step: Float): String = String.format(Locale.US, "%+.1f EV", index * step)

    fun zoomLabel(z: Float): String = String.format(Locale.US, "%.1fx", z)

    fun clampZoom(z: Float, caps: ProCaps): Float {
        val lo = maxOf(ZOOM_MIN, caps.zoomMin)
        val hi = maxOf(lo, minOf(ZOOM_MAX, caps.zoomMax))
        return z.coerceIn(lo, hi)
    }
}

/**
 * Rencana yang diterapkan ke kamera, dihitung dari pilihan pengguna dan kemampuan kamera.
 * Murni Kotlin supaya bisa diuji; ProCameraController yang menerjemahkannya ke CameraX/Camera2.
 */
data class ProPlan(
    val aeManual: Boolean,
    val aeLock: Boolean,
    val awbMode: Int,
    val awbLock: Boolean,
    val afManual: Boolean,
    val focusDistance: Float,
    val exposureNs: Long,
    val iso: Int,
    val evIndex: Int,
    val zoom: Float,
    /** Fokus dikunci di tengah bingkai (startFocusAndMetering tanpa batal otomatis). */
    val holdFocus: Boolean,
    /** Eksposur ikut diukur dan dikunci bersama fokus. */
    val holdExposure: Boolean
) {
    /** True bila ada yang harus diatur lewat Camera2 (selain zoom dan EV). */
    val needsCamera2: Boolean get() = aeManual || aeLock || awbMode != AWB_AUTO || awbLock || afManual

    companion object {
        const val AWB_AUTO = 1

        fun build(s: ProState, c: ProCaps): ProPlan {
            val manualExp = s.focus.manualExposure && c.hasManualExposure
            val lock = s.focus.locksFocus
            val afManual = s.focus == FocusMode.MANUAL && c.hasManualFocus

            val shutterStops = ProMath.shutterStops(c.shutterMinNs, c.shutterMaxNs)
            val shutter = if (s.shutterNs > 0L && c.shutterMaxNs > c.shutterMinNs) {
                s.shutterNs.coerceIn(c.shutterMinNs, c.shutterMaxNs)
            } else {
                ProMath.defaultShutter(shutterStops)
            }
            val isoStops = ProMath.isoStops(c.isoMin, c.isoMax)
            val iso = if (s.iso > 0 && c.isoMax >= c.isoMin) {
                s.iso.coerceIn(c.isoMin, c.isoMax)
            } else {
                ProMath.defaultIso(isoStops)
            }

            val wb = if (c.supportsWb(s.wb)) s.wb else WbMode.AUTO
            val ev = if (!c.hasEv || manualExp) 0 else s.ev.coerceIn(c.evMin, c.evMax)

            return ProPlan(
                aeManual = manualExp,
                aeLock = s.focus == FocusMode.LOCK,
                awbMode = wb.awbMode,
                awbLock = wb.lock,
                afManual = afManual,
                focusDistance = if (c.hasManualFocus) s.focusDistance.coerceIn(0f, c.minFocusDiopter) else 0f,
                exposureNs = shutter,
                iso = iso,
                evIndex = ev,
                zoom = ProMath.clampZoom(s.zoom, c),
                holdFocus = lock,
                holdExposure = s.focus == FocusMode.LOCK
            )
        }
    }
}
