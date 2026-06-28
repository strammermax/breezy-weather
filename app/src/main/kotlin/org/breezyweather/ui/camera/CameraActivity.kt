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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.exifinterface.media.ExifInterface as AndroidXExifInterface
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.breezyweather.R
import org.breezyweather.databinding.ActivityCameraBinding
import org.breezyweather.wallpaper.LiveWallpaperConfigActivity
import org.breezyweather.wallpaper.launchLiveWallpaperPicker
import org.breezyweather.wallpaper.photo.RemoveSkyHttpException
import org.breezyweather.wallpaper.photo.WallpaperRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private lateinit var resultImageView: ImageView
    private lateinit var resultTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var captureButton: View
    private var captureInProgress = false
    private val galleryLog = StringBuilder()

    private val pickGalleryPhotos = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) uploadGalleryPhotos(uris)
    }

    // Without ACCESS_MEDIA_LOCATION, MediaStore silently redacts GPS EXIF from every photo we
    // read back from the picker (see copyUriToTempFile), regardless of what the original file
    // has. The picker is launched either way — without the permission we just fall back to
    // treating the photo as if it had no GPS of its own.
    private val requestMediaLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pickGalleryPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val REQUEST_CODE_LOCATION_PERMISSIONS = 11
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        private const val THUMBNAIL_SIZE_DIP = 96
        private const val THUMBNAIL_MARGIN_DIP = 8
    }

    @Inject
    lateinit var wallpaperRepository: WallpaperRepository

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

        binding.galleryButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestMediaLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            } else {
                pickGalleryPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
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
        binding.uploadedThumbnailsScroll.visibility = View.GONE
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
                        uploadImage(file, location)
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

    private fun uploadImage(file: File, location: Location?) {
        try {
            // Upload the camera's own JPEG bytes as-is (not a Bitmap.compress() re-encode,
            // which carries no EXIF support at all) so every EXIF tag the sensor wrote
            // (datetime, make/model, orientation, ...) survives the upload. CameraX doesn't
            // attach GPS itself, so fill it in from the location fix when one is available.
            location?.let { addGpsToExif(file, it) }

            runBlocking {
                wallpaperRepository.uploadCameraPhoto(
                    file = file,
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
                resultTextView.text = describeUploadError(e)
            }
        } finally {
            file.delete()
        }
    }

    /** Short, user-facing reason for an upload failure — shared by the single-photo and gallery flows. */
    private fun describeUploadError(e: Exception): String = when (e) {
        is RemoveSkyHttpException -> formatUploadResult(false, e.statusCode, e.responseBody)
        is java.net.SocketTimeoutException -> getString(R.string.camera_error_timeout)
        is java.net.UnknownHostException -> getString(R.string.camera_error_server_down)
        else -> getString(R.string.camera_error_general, e.message ?: "Unknown error")
    }

    /** Writes [location] into [file]'s GPS EXIF tags, leaving every other tag untouched. */
    private fun addGpsToExif(file: File, location: Location) {
        val exif = AndroidXExifInterface(file.absolutePath)
        exif.setLatLong(location.latitude, location.longitude)
        exif.saveAttributes()
    }

    private fun hasGpsExif(file: File): Boolean = try {
        AndroidXExifInterface(file.absolutePath).latLong != null
    } catch (e: Exception) {
        false
    }

    /**
     * Uploads several gallery photos one at a time, each keeping its own EXIF as-is — unlike a
     * fresh camera capture, a gallery photo may already carry its own GPS (where it was
     * actually taken), so the current location fix is only added when one isn't already there.
     */
    private fun uploadGalleryPhotos(uris: List<Uri>) {
        binding.resultImageView.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE // doesn't apply to a gallery batch
        resetGalleryLog()
        binding.progressBar.visibility = View.VISIBLE
        showResultView()

        fetchLocation { location ->
            cameraExecutor.execute {
                var successCount = 0
                var failureCount = 0
                val uploadedUris = mutableListOf<Uri>()
                uris.forEachIndexed { index, uri ->
                    val label = "Foto ${index + 1}/${uris.size}"
                    appendGalleryLog("$label: voorbereiden…")
                    val file = copyUriToTempFile(uri)
                    if (file == null) {
                        failureCount++
                        appendGalleryLog("$label: ✗ kon bestand niet lezen")
                        return@forEachIndexed
                    }
                    try {
                        val locationToAdd = location?.takeIf { !hasGpsExif(file) }
                        locationToAdd?.let { addGpsToExif(file, it) }
                        appendGalleryLog("$label: uploaden en verwerken op server…")
                        runBlocking {
                            wallpaperRepository.uploadCameraPhoto(
                                file = file,
                                latitude = locationToAdd?.latitude,
                                longitude = locationToAdd?.longitude,
                            )
                        }
                        successCount++
                        uploadedUris.add(uri)
                        appendGalleryLog("$label: ✓ opgeslagen")
                    } catch (e: Exception) {
                        failureCount++
                        appendGalleryLog("$label: ✗ ${describeUploadError(e)}")
                    } finally {
                        file.delete()
                    }
                }
                appendGalleryLog("\n" + getString(R.string.camera_gallery_result, successCount, failureCount))
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.setLiveWallpaperButton.visibility = if (successCount > 0) View.VISIBLE else View.GONE
                    binding.closeButton.visibility = View.VISIBLE
                }
                showUploadedThumbnails(uploadedUris)
            }
        }
    }

    /** Resets the running upload-progress log shown in [ActivityCameraBinding.resultTextView]. */
    private fun resetGalleryLog() {
        galleryLog.setLength(0)
        binding.resultTextView.text = ""
    }

    /** Appends [line] to the running upload-progress log and scrolls it into view. */
    private fun appendGalleryLog(line: String) {
        runOnUiThread {
            if (galleryLog.isNotEmpty()) galleryLog.append('\n')
            galleryLog.append(line)
            binding.resultTextView.text = galleryLog.toString()
            binding.resultTextScroll.post { binding.resultTextScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** Shows a small thumbnail per successfully uploaded gallery photo. */
    private fun showUploadedThumbnails(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val thumbnailSizePx = (THUMBNAIL_SIZE_DIP * resources.displayMetrics.density).toInt()
        val marginPx = (THUMBNAIL_MARGIN_DIP * resources.displayMetrics.density).toInt()
        val thumbnails = uris.mapNotNull { decodeSampledBitmap(it, thumbnailSizePx) }
        runOnUiThread {
            binding.uploadedThumbnailsContainer.removeAllViews()
            thumbnails.forEach { bitmap ->
                val imageView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(thumbnailSizePx, thumbnailSizePx).apply {
                        marginEnd = marginPx
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }
                binding.uploadedThumbnailsContainer.addView(imageView)
            }
            binding.uploadedThumbnailsScroll.visibility = if (thumbnails.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Decodes [uri] downsampled to roughly [targetSizePx], avoiding a full-resolution load for a thumbnail. */
    private fun decodeSampledBitmap(uri: Uri, targetSizePx: Int): Bitmap? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetSizePx && bounds.outHeight / (sampleSize * 2) >= targetSizePx) {
                sampleSize *= 2
            }
            contentResolver.openInputStream(uri)?.use { secondInput ->
                BitmapFactory.decodeStream(secondInput, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Copies [uri]'s raw bytes into a private temp file, preserving its EXIF exactly. */
    private fun copyUriToTempFile(uri: Uri): File? = try {
        val file = File.createTempFile("gallery_", ".jpg", cacheDir)
        val copied = openOriginalInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (copied) file else { file.delete(); null }
    } catch (e: Exception) {
        null
    }

    /**
     * Opens [uri] via [MediaStore.setRequireOriginal] so the bytes include GPS EXIF — without
     * this, MediaStore silently redacts location (and some other) EXIF tags from anything read
     * through a plain content:// stream on Android 10+, regardless of what the file actually
     * has. Falls back to the plain (possibly redacted) stream when the permission is missing or
     * the original can't be served (e.g. the media has none to give).
     */
    private fun openOriginalInputStream(uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                return contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
            } catch (e: UnsupportedOperationException) {
                // No original available for this URI — fall through to the redacted stream.
            }
        }
        return contentResolver.openInputStream(uri)
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
