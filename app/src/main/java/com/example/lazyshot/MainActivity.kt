package com.example.lazyshot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lazyshot.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraRepository: CameraRepository

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initializeCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraRepository = CameraRepository(this)

        if (allPermissionsGranted()) {
            initializeCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun initializeCamera() {
        cameraRepository.start()
        
        val surfaceView = binding.viewFinder
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cameraRepository.openCamera(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }

            override fun surfaceDestroyed(holder: SurfaceHolder) { }
        })

        // UI Controls
        binding.isoSlider.addOnChangeListener { _, value, _ ->
            cameraRepository.setIso(value.toInt())
        }
        
        binding.shutterSlider.addOnChangeListener { _, value, _ ->
            // Map 0-100 slider to roughly 1/10000s to 1s
            // Simple linear mapping for demo: 1ms to 100ms
            val ns = (value.toLong() * 1_000_000) 
            cameraRepository.setShutterSpeed(ns)
        }

        binding.focusSlider.addOnChangeListener { _, value, _ ->
            cameraRepository.setFocusDistance(value)
        }
    }
    
    // Changing strategy: I'll put the SurfaceView setup in a helper or directly here.
    // I need to update activity_main.xml to have a SurfaceView instead of TextView.
    
    override fun onDestroy() {
        super.onDestroy()
        cameraRepository.stop()
    }
    
    /**
     * A native method that is implemented by the 'lazyshot' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'lazyshot' library on application startup.
        init {
            System.loadLibrary("lazyshot")
        }
    }
}
