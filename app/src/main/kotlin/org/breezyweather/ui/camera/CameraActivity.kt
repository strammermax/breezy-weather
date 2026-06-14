package org.breezyweather.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import org.breezyweather.BuildConfig
import org.breezyweather.R
import org.breezyweather.databinding.ActivityCameraBinding
import org.breezyweather.wallpaper.photo.RemoveSkyProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
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
    
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val UPLOAD_PATH = "/api/v1/upload"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private val httpClient = OkHttpClient()

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
        
        setupClickListeners()
    }
    
    private fun setupClickListeners() {
        binding.captureButton.setOnClickListener {
            takePhoto()
        }
        
        binding.retakeButton.setOnClickListener {
            showCameraView()
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun showCameraView() {
        binding.cameraPreviewView.visibility = View.VISIBLE
        binding.horizonGuideLine.visibility = View.VISIBLE
        binding.horizonGuideLabel.visibility = View.VISIBLE
        binding.captureButton.visibility = View.VISIBLE
        binding.resultImageView.visibility = View.GONE
        binding.resultTextView.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE
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
        val imageCapture = imageCapture ?: return
        
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
            }
            uploadImage(file, bitmap)
        }
    }

    private fun uploadImage(file: File, bitmap: Bitmap) {
        try {
            // Compress the (correctly oriented) image
            val compressedFile = File.createTempFile("compressed_", ".jpg", cacheDir)
            val outputStream = FileOutputStream(compressedFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.close()

            // Upload to server using RemoveSky service
            val form = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    file.name,
                    compressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val response = httpClient.newCall(
                okhttp3.Request.Builder()
                    .url(RemoveSkyProvider.DEFAULT_BASE_URL + UPLOAD_PATH)
                    .post(form)
                    .header("CF-Access-Client-Id", BuildConfig.CF_ACCESS_CLIENT_ID)
                    .header("CF-Access-Client-Secret", BuildConfig.CF_ACCESS_CLIENT_SECRET)
                    .build()
            ).execute().use { uploadResponse ->
                if (uploadResponse.isSuccessful) {
                    uploadResponse.body?.string() ?: getString(R.string.camera_upload_success)
                } else {
                    val errorBody = uploadResponse.body?.string()?.takeIf { it.isNotBlank() }
                    throw Exception("HTTP ${uploadResponse.code}${errorBody?.let { ": $it" } ?: ""}")
                }
            }

            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                binding.resultTextView.text = response
            }

            // Clean up
            compressedFile.delete()
            file.delete()
        } catch (e: Exception) {
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                resultTextView.text = when (e) {
                    is java.net.SocketTimeoutException -> getString(R.string.camera_error_timeout)
                    is java.net.UnknownHostException -> getString(R.string.camera_error_server_down)
                    else -> getString(R.string.camera_error_general, e.message ?: "Unknown error")
                }
            }
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
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}