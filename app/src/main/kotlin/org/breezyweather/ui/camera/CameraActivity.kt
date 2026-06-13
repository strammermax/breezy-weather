package org.breezyweather.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import org.breezyweather.R
import org.breezyweather.databinding.ActivityCameraBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
        private const val UPLOAD_URL = "http://removesky.vanburik.info:12345/api/v1/upload"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

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
        binding.captureButton.visibility = View.VISIBLE
        binding.resultImageView.visibility = View.GONE
        binding.resultTextView.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE
        binding.resultContainer.visibility = View.GONE
    }
    
    private fun showResultView() {
        binding.cameraPreviewView.visibility = View.GONE
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
                    uploadImage(photoFile)
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Photo capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun uploadImage(file: File) {
        binding.progressBar.visibility = View.VISIBLE
        binding.resultTextView.text = getString(R.string.camera_uploading)
        
        cameraExecutor.execute {
            try {
                // Compress the image
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val compressedFile = File.createTempFile("compressed_", ".jpg", cacheDir)
                val outputStream = FileOutputStream(compressedFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.close()
                
                // Upload to server
                val url = URL(UPLOAD_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                
                val boundary = "ImageBoundary"
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                
                val fileData = compressedFile.readBytes()
                
                val outputStream = connection.outputStream
                outputStream.write("--$boundary\r\n".toByteArray())
                outputStream.write("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n".toByteArray())
                outputStream.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
                outputStream.write(fileData)
                outputStream.write("\r\n--$boundary--\r\n".toByteArray())
                outputStream.flush()
                outputStream.close()
                
                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Upload failed"
                }
                
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.resultTextView.text = response
                    showResultView()
                    
                    // If response contains image URL, try to load it
                    if (response.contains("http")) {
                        // Try to parse the response and load the result image
                        binding.resultImageView.visibility = View.GONE
                    }
                }
                
                // Clean up
                compressedFile.delete()
                file.delete()
                
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    resultTextView.text = "Error: ${e.message}"
                    showResultView()
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