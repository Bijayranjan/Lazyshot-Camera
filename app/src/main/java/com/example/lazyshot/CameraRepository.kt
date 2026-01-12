package com.example.lazyshot

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

class CameraRepository(private val context: Context) {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    // Load native library
    init {
        System.loadLibrary("lazyshot")
    }
    
    private external fun createNativeImageReader(width: Int, height: Int, format: Int): Surface?

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    fun start() {
        startBackgroundThread()
    }

    fun stop() {
        closeCamera()
        stopBackgroundThread()
    }

    @SuppressLint("MissingPermission")
    fun openCamera(surfaceHolder: SurfaceHolder) {
        val cameraId = findBackFacingCamera() ?: return
        
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession(camera, surfaceHolder)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
        }
    }

    private fun findBackFacingCamera(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }


    private var previewSurface: Surface? = null
    private var rawSurface: Surface? = null
    private var p010Surface: Surface? = null

    private fun createCameraPreviewSession(camera: CameraDevice, surfaceHolder: SurfaceHolder) {
        try {
            previewSurface = surfaceHolder.surface
            rawSurface = createNativeImageReader(640, 480, 0x20)
            p010Surface = createNativeImageReader(640, 480, 0x36)

            val targets = mutableListOf(previewSurface!!)
            rawSurface?.let { targets.add(it) }
            p010Surface?.let { targets.add(it) }

            camera.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    captureSession = session

                    try {
                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        
                        // Add targets
                        previewSurface?.let { builder.addTarget(it) }
                        rawSurface?.let { builder.addTarget(it) }
                        p010Surface?.let { builder.addTarget(it) }

                        
                        // Use Manual Control Logic
                        applyManualControls(builder)

                        session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting preview", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session", e)
        }
    }

    // Manual Control Storage
    private var manualIso: Int? = null
    private var manualShutterSpeed: Long? = null // Nanoseconds
    private var manualFocusDistance: Float? = null

    fun setIso(iso: Int) {
        manualIso = iso
        updateRepeatingRequest()
    }

    fun setShutterSpeed(speedNs: Long) {
        manualShutterSpeed = speedNs
        updateRepeatingRequest()
    }

    fun setFocusDistance(distance: Float) {
        manualFocusDistance = distance
        updateRepeatingRequest()
    }

    private fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val camera = cameraDevice ?: return
        
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            
            // Re-add targets
            previewSurface?.let { builder.addTarget(it) }
            rawSurface?.let { builder.addTarget(it) }
            p010Surface?.let { builder.addTarget(it) }
            
            applyManualControls(builder)

            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating request", e)
        }
    }
    
    // Helper to apply controls
    private fun applyManualControls(builder: CaptureRequest.Builder) {
        // Toggle Auto-Exposure
        if (manualIso != null || manualShutterSpeed != null) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            manualIso?.let { builder.set(CaptureRequest.SENSOR_SENSITIVITY, it) }
            manualShutterSpeed?.let { builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        } else {
             builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        // Toggle Auto-Focus
        if (manualFocusDistance != null) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualFocusDistance)
        } else {
             builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        
         // Stabilizations
        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    companion object {
        private const val TAG = "CameraRepository"
    }
}
