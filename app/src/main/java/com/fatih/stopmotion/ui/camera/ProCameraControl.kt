package com.fatih.stopmotion.ui.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory

/** Membaca kemampuan kamera dan menerapkan ProPlan lewat CameraX dan Camera2 interop. */
object ProCameraControl {

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun readCaps(camera: Camera): ProCaps {
        val info = camera.cameraInfo
        val c2 = try { Camera2CameraInfo.from(info) } catch (e: Exception) { null }

        fun <T> chr(key: CameraCharacteristics.Key<T>): T? =
            try { c2?.getCameraCharacteristic(key) } catch (e: Exception) { null }

        val iso = chr(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposure = chr(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        val minFocus = chr(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val awb = chr(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
        val capabilities = chr(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val manualSensor = capabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true

        var evMin = 0
        var evMax = 0
        var evStep = 0f
        try {
            val ev = info.exposureState
            if (ev.isExposureCompensationSupported) {
                evMin = ev.exposureCompensationRange.lower
                evMax = ev.exposureCompensationRange.upper
                evStep = ev.exposureCompensationStep.toFloat()
            }
        } catch (e: Exception) {
            // EV tidak tersedia
        }

        var zoomMin = 1f
        var zoomMax = 1f
        try {
            val z = info.zoomState.value
            if (z != null) {
                zoomMin = z.minZoomRatio
                zoomMax = z.maxZoomRatio
            }
        } catch (e: Exception) {
            // zoom tidak tersedia
        }

        return ProCaps(
            manualSensor = manualSensor,
            isoMin = iso?.lower ?: 0,
            isoMax = iso?.upper ?: 0,
            shutterMinNs = exposure?.lower ?: 0L,
            shutterMaxNs = exposure?.upper ?: 0L,
            minFocusDiopter = minFocus,
            awbModes = awb?.toSet() ?: emptySet(),
            evMin = evMin,
            evMax = evMax,
            evStep = evStep,
            zoomMin = zoomMin,
            zoomMax = zoomMax
        )
    }
}

/**
 * Menerapkan rencana ke satu kamera yang sedang aktif. Simpan satu instance per layar kamera dan panggil reset()
 * setiap kamera dipasang ulang (ganti lensa, kualitas, atau chroma), karena pengaturan kamera ikut hilang.
 */
class ProCameraController {
    private var applied: ProPlan? = null

    fun reset() {
        applied = null
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun apply(camera: Camera, plan: ProPlan) {
        val prev = applied
        if (prev == plan) return
        applied = plan
        val control = camera.cameraControl

        try {
            if (prev == null || prev.zoom != plan.zoom) control.setZoomRatio(plan.zoom)
        } catch (e: Exception) {
            // zoom gagal; dilewati
        }

        try {
            if (!plan.aeManual && (prev == null || prev.evIndex != plan.evIndex || prev.aeManual) &&
                camera.cameraInfo.exposureState.isExposureCompensationSupported
            ) {
                control.setExposureCompensationIndex(plan.evIndex)
            }
        } catch (e: Exception) {
            // EV gagal; dilewati
        }

        try {
            val c2 = Camera2CameraControl.from(control)
            if (!plan.needsCamera2) {
                c2.clearCaptureRequestOptions()
            } else {
                val b = CaptureRequestOptions.Builder()
                if (plan.aeManual) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureNs)
                    b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
                }
                if (plan.aeLock) b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                if (plan.awbMode != ProPlan.AWB_AUTO) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, plan.awbMode)
                if (plan.awbLock) b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                if (plan.afManual) {
                    b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                    b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, plan.focusDistance)
                }
                c2.setCaptureRequestOptions(b.build())
            }
        } catch (e: Exception) {
            // perangkat menolak opsi ini; kamera tetap berjalan otomatis
        }

        try {
            val start = plan.holdFocus && (prev == null || !prev.holdFocus || prev.holdExposure != plan.holdExposure)
            val cancel = !plan.holdFocus && prev?.holdFocus == true
            if (cancel) control.cancelFocusAndMetering()
            if (start) {
                val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                var flags = FocusMeteringAction.FLAG_AF
                if (plan.holdExposure) flags = flags or FocusMeteringAction.FLAG_AE
                control.startFocusAndMetering(FocusMeteringAction.Builder(point, flags).disableAutoCancel().build())
            }
        } catch (e: Exception) {
            // fokus gagal dikunci; dilewati
        }
    }
}
