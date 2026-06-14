package org.breezyweather.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.breezyweather.R
import org.breezyweather.databinding.ActivityCameraBinding
import org.breezyweather.wallpaper.LiveWallpaperConfigActivity
import org.breezyweather.wallpaper.launchLiveWallpaperPicker
import org.breezyweather.wallpaper.photo.RemoveSkyHttpException
import org.breezyweather.wallpaper.photo.WallpaperRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private lateinit var resultImageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var captureButton: View
    private var captureInProgress = false
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_LOCATION_PERMISSIONS = 11
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    private val wallpaperRepository by lazy { WallpaperRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        resultImageView = binding.resultImageView
        resultTextView = binding.resultTextView
        progressBar = binding.progressBar
        captureButton = binding.captureButton
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Location is optional (used so the server can geo-tag and sort the saved photo),
        // so it's requested but doesn't block the camera from starting.
        if (LOCATION_PERMISSIONS.none {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_LOCATION_PERMISSIONS)
        }

        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        
        binding.retakeButton.setOnClickListener {
            showCameraView()
        }

        binding.setLiveWallpaperButton.setOnClickListener {
            if (!launchLiveWallpaperPicker(this)) {
                Toast.makeText(this, R.string.camera_live_wallpaper_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        binding.closeButton.setOnClickListener {
            openLiveWallpaperSettings()
        }
        
        binding.toolbar.setNavigationOnClickListener {
            openLiveWallpaperSettings()
        }
    }

    private fun openLiveWallpaperSettings() {
        startActivity(Intent(this, LiveWallpaperConfigActivity::class.java))
        finish()
    }
    
    private fun showCameraView() {
        captureInProgress = false
        binding.captureButton.isEnabled = true
        binding.cameraPreviewView.visibility = View.VISIBLE
        binding.horizonGuideLine.visibility = View.VISIBLE
        binding.horizonGuideLabel.visibility = View.VISIBLE
        binding.captureButton.visibility = View.VISIBLE
        binding.resultImageView.visibility = View.GONE
        binding.resultTextView.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE
        binding.setLiveWallpaperButton.visibility = View.GONE
        binding.closeButton.visibility = View.GONE
        binding.resultContainer.visibility = View.GONE
    }

    private fun showResultView() {
        binding.cameraPreviewView.visibility = View.GONE
        binding.horizonGuideLine.visibility = View.GONE
        binding.horizonGuideLabel.visibility = View.GONE
        binding.captureButton.visibility = View.GONE
        binding.resultImageView.visibility = View.VISIBLE
        binding.resultTextView.visibility = View.VISIBLE
        binding.retakeButton.visibility = View.VISIBLE
        binding.resultContainer.visibility = View.VISIBLE
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreviewView.surfaceProvider
            }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (captureInProgress) return
        val imageCapture = imageCapture ?: return

        captureInProgress = true
        binding.captureButton.isEnabled = false
        
        val photoFile = File(
            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    showCapturedPhotoAndUpload(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    captureInProgress = false
                    binding.captureButton.isEnabled = true
                    Toast.makeText(this@CameraActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /** Decodes the captured JPEG and rotates it according to its EXIF orientation tag. */
    private fun decodeRotatedBitmap(file: File): Bitmap {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val rotationDegrees = when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun showCapturedPhotoAndUpload(file: File) {
        cameraExecutor.execute {
            val bitmap = decodeRotatedBitmap(file)
            runOnUiThread {
                binding.resultImageView.setImageBitmap(bitmap)
                binding.resultTextView.text = getString(R.string.camera_uploading)
                binding.progressBar.visibility = View.VISIBLE
                showResultView()

                fetchLocation { location ->
                    cameraExecutor.execute {
                        uploadImage(file, bitmap, location)
                    }
                }
            }
        }
    }

    /**
     * Best-effort current location, used so the server can geo-tag and sort the saved photo.
     * Returns null (without blocking the upload) if permission is missing, location services
     * are off, or no fix is available.
     */
    @SuppressLint("MissingPermission")
    private fun fetchLocation(callback: (Location?) -> Unit) {
        if (LOCATION_PERMISSIONS.none {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            callback(null)
            return
        }

        val locationManager = getSystemService(LocationManager::class.java)
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            callback(null)
            return
        }

        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            null as android.os.CancellationSignal?,
            ContextCompat.getMainExecutor(this),
            androidx.core.util.Consumer<Location?> { location ->
                callback(location ?: lastKnownLocation(locationManager))
            }
        )
    }

    private fun lastKnownLocation(locationManager: LocationManager): Location? {
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
        } else {
            null
        }
        return fused
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    }

    private fun uploadImage(file: File, bitmap: Bitmap, location: Location?) {
        var compressedFile: File? = null
        try {
            // Compress the (correctly oriented) image
            compressedFile = File.createTempFile("compressed_", ".jpg", cacheDir)
            val outputStream = FileOutputStream(compressedFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.close()

            runBlocking {
                wallpaperRepository.uploadCameraPhoto(
                    file = compressedFile,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                )
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.resultTextView.text = "✓ " + getString(R.string.camera_result_saved)
                binding.setLiveWallpaperButton.visibility = View.VISIBLE
                binding.closeButton.visibility = View.VISIBLE
            }

        } catch (e: Exception) {
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.setLiveWallpaperButton.visibility = View.GONE
                binding.closeButton.visibility = View.VISIBLE
                resultTextView.text = when (e) {
                    is RemoveSkyHttpException -> formatUploadResult(false, e.statusCode, e.responseBody)
                    is java.net.SocketTimeoutException -> getString(R.string.camera_error_timeout)
                    is java.net.UnknownHostException -> getString(R.string.camera_error_server_down)
                    else -> getString(R.string.camera_error_general, e.message ?: "Unknown error")
                }
            }
        } finally {
            compressedFile?.delete()
            file.delete()
        }
    }

    /** Maps the server response to a short, user-facing verdict with a ✓/✗ marker. */
    private fun formatUploadResult(isSuccessful: Boolean, code: Int, body: String?): String {
        if (isSuccessful) {
            return "✓ " + getString(R.string.camera_result_saved)
        }
        val reason = body?.takeIf { it.isNotBlank() }?.let {
            try {
                JSONObject(it).optJSONObject("detail")?.optString("reason")
            } catch (e: Exception) {
                null
            }
        }
        return when (reason) {
            "no_sky_at_top" -> "✗ " + getString(R.string.camera_result_no_sky_at_top)
            "insufficient_sky_in_top_region" -> "✗ " + getString(R.string.camera_result_too_little_sky)
            "clip_not_landscape" -> "✗ " + getString(R.string.camera_result_not_landscape)
            else -> getString(R.string.camera_error_general, "HTTP $code${body?.let { ": $it" } ?: ""}")
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        // No special handling needed for REQUEST_CODE_LOCATION_PERMISSIONS: location is
        // optional and simply omitted from the upload if it wasn't granted.
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
