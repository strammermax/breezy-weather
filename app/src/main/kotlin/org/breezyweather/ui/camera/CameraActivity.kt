package org.breezyweather.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.breezyweather.R
import org.breezyweather.databinding.ActivityCameraBinding
import org.breezyweather.common.utils.DiagnosticLogger
import org.breezyweather.wallpaper.LiveWallpaperConfigActivity
import org.breezyweather.wallpaper.launchLiveWallpaperPicker
import org.breezyweather.wallpaper.photo.RemoveSkyCheckResult
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

    // The privacy-sandboxed Photo Picker (PickMultipleVisualMedia) never honors
    // MediaStore.setRequireOriginal()/ACCESS_MEDIA_LOCATION, even when granted — confirmed
    // "Working As Intended" by Google (issuetracker.google.com/issues/243294058). Real GPS EXIF
    // is only readable from genuine MediaStore URIs, so gallery upload uses the classic
    // ACTION_GET_CONTENT gallery intent instead, which does return real MediaStore URIs.
    private val pickGalleryPhotos = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = buildList {
            data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) add(clipData.getItemAt(i).uri)
            }
            data?.data?.let { if (isEmpty()) add(it) }
        }
        if (uris.isNotEmpty()) uploadGalleryPhotos(uris)
    }

    // READ_MEDIA_IMAGES (33+) / READ_EXTERNAL_STORAGE (<33) so the gallery intent below can
    // resolve real MediaStore URIs at all; ACCESS_MEDIA_LOCATION so those URIs' GPS EXIF isn't
    // redacted. The gallery intent launches regardless of the result — without these, photos
    // just fall back to being treated as if they have no GPS of their own.
    private val requestGalleryPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        launchGalleryPicker()
    }

    private fun launchGalleryPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        pickGalleryPhotos.launch(intent)
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
        // A phone sensor JPEG is commonly 12-50 MP. Keeping that entire bitmap in memory while
        // encoding can exceed Android's heap; this is still ample for a wallpaper/server upload.
        private const val CAMERA_UPLOAD_MAX_DIMENSION = 2560
        private const val CAMERA_UPLOAD_WEBP_QUALITY = 88
        private val EXIF_STRUCTURE_FIELDS = setOf(
            "TAG_IMAGE_WIDTH",
            "TAG_IMAGE_LENGTH",
            "TAG_PIXEL_X_DIMENSION",
            "TAG_PIXEL_Y_DIMENSION",
            "TAG_ORIENTATION",
            "TAG_COMPRESSION",
            "TAG_STRIP_OFFSETS",
            "TAG_ROWS_PER_STRIP",
            "TAG_STRIP_BYTE_COUNTS",
            "TAG_JPEG_INTERCHANGE_FORMAT",
            "TAG_JPEG_INTERCHANGE_FORMAT_LENGTH",
        )
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
            val readMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            val missingPermissions = buildList {
                if (ContextCompat.checkSelfPermission(this@CameraActivity, readMediaPermission) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    add(readMediaPermission)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this@CameraActivity, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }
            }
            if (missingPermissions.isNotEmpty()) {
                requestGalleryPermissions.launch(missingPermissions.toTypedArray())
            } else {
                launchGalleryPicker()
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
        binding.uploadResultCardsScroll.visibility = View.GONE
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
        binding.resultTextScroll.visibility = View.VISIBLE
        binding.resultTextView.visibility = View.VISIBLE
        binding.uploadResultCardsScroll.visibility = View.GONE
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
                Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show()
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
                    DiagnosticLogger.log(this@CameraActivity, "Camera", "Capture saved (${photoFile.length()} bytes)")
                    showCapturedPhotoAndUpload(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    DiagnosticLogger.log(this@CameraActivity, "Camera", "Capture failed", exception)
                    captureInProgress = false
                    binding.captureButton.isEnabled = true
                    Toast.makeText(this@CameraActivity, R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /** Decodes the captured JPEG at an upload-safe size and applies its EXIF rotation. */
    private fun decodeRotatedBitmap(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Captured photo could not be decoded" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > CAMERA_UPLOAD_MAX_DIMENSION) {
            sampleSize *= 2
        }
        val bitmap = requireNotNull(
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ) { "Captured photo could not be decoded" }
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
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    /**
     * Encodes [bitmap] as a compact temporary WebP and copies every EXIF attribute understood
     * by AndroidX from the CameraX JPEG. The pixels have already been rotated, so orientation is
     * normalised to prevent the server from rotating the WebP a second time.
     */
    private fun createUploadWebp(sourceJpeg: File, bitmap: Bitmap): File {
        val webp = File.createTempFile("camera_upload_", ".webp", cacheDir)
        try {
            val written = webp.outputStream().use { output ->
                @Suppress("DEPRECATION")
                bitmap.compress(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                    else Bitmap.CompressFormat.WEBP,
                    CAMERA_UPLOAD_WEBP_QUALITY,
                    output,
                )
            }
            check(written) { "WebP conversion failed" }
            copyExifAttributes(sourceJpeg, webp)
            DiagnosticLogger.log(
                this,
                "Camera",
                "WebP reference=${webp.name} (${bitmap.width}x${bitmap.height}, ${webp.length()} bytes, EXIF copied)",
            )
            return webp
        } catch (e: Exception) {
            webp.delete()
            throw e
        }
    }

    private fun copyExifAttributes(sourceFile: File, destinationFile: File) {
        val source = AndroidXExifInterface(sourceFile.absolutePath)
        val destination = AndroidXExifInterface(destinationFile.absolutePath)
        AndroidXExifInterface::class.java.fields
            .asSequence()
            .filter {
                it.name.startsWith("TAG_") &&
                    it.name !in EXIF_STRUCTURE_FIELDS &&
                    it.type == String::class.java
            }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .distinct()
            .forEach { tag ->
                source.getAttribute(tag)?.let { value ->
                    runCatching { destination.setAttribute(tag, value) }
                }
            }
        destination.setAttribute(
            AndroidXExifInterface.TAG_ORIENTATION,
            AndroidXExifInterface.ORIENTATION_NORMAL.toString(),
        )
        destination.saveAttributes()
    }

    private fun showCapturedPhotoAndUpload(file: File) {
        cameraExecutor.execute {
            try {
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
            } catch (e: Exception) {
                file.delete()
                runOnUiThread {
                    captureInProgress = false
                    binding.captureButton.isEnabled = true
                    Toast.makeText(this, describeUploadError(e), Toast.LENGTH_LONG).show()
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
        var uploadFile: File? = null
        try {
            // CameraX doesn't attach GPS itself. Add it before copying the JPEG metadata into
            // the smaller WebP that is actually sent to the server.
            location?.let { addGpsToExif(file, it) }
            location?.let {
                DiagnosticLogger.log(this, "Camera GPS", "latitude=${it.latitude}, longitude=${it.longitude}")
            }
            uploadFile = createUploadWebp(file, bitmap)
            DiagnosticLogger.log(this, "Camera", "Upload started")

            val result = runBlocking {
                wallpaperRepository.uploadCameraPhoto(
                    file = uploadFile,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                )
            }
            DiagnosticLogger.log(
                this,
                "Camera",
                "Upload and server processing completed; processedUrl=${result.processedUrl}",
            )

            renderResultCards(
                listOf(
                    UploadCardData(
                        thumbnail = bitmap,
                        source = getString(R.string.camera_source_camera),
                        locationName = result.location,
                        processedUrl = result.processedUrl,
                        uploadFailureReason = null,
                    )
                )
            )

        } catch (e: Exception) {
            DiagnosticLogger.log(this, "Camera", "Upload failed", e)
            val rejectionCode = extractRejectionReasonCode(e)
            if (rejectionCode != null || e is RemoveSkyHttpException) {
                binding.closeButton.visibility = View.VISIBLE
                renderResultCards(
                    listOf(
                        UploadCardData(
                            thumbnail = bitmap,
                            source = getString(R.string.camera_source_camera),
                            locationName = null,
                            processedUrl = null,
                            uploadFailureReason = rejectionCode ?: "unknown",
                        )
                    )
                )
            } else {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.setLiveWallpaperButton.visibility = View.GONE
                    binding.closeButton.visibility = View.VISIBLE
                    resultTextView.text = describeUploadError(e)
                }
            }
        } finally {
            uploadFile?.delete()
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

    /**
     * The server's suitability rejection code (e.g. "no_sky_at_top") from a `/upload` 400
     * response's `detail.reason`, or null when [e] isn't that kind of per-photo rejection.
     */
    private fun extractRejectionReasonCode(e: Exception): String? {
        val body = (e as? RemoveSkyHttpException)?.responseBody?.takeIf { it.isNotBlank() } ?: return null
        return try {
            JSONObject(body).optJSONObject("detail")?.optString("reason")?.ifBlank { null }
        } catch (ex: Exception) {
            null
        }
    }

    /** Writes [location] into [file]'s GPS EXIF tags, leaving every other tag untouched. */
    private fun addGpsToExif(file: File, location: Location) {
        val exif = AndroidXExifInterface(file.absolutePath)
        exif.setLatLong(location.latitude, location.longitude)
        exif.saveAttributes()
    }

    /** Reads [lat, lon] from [file]'s EXIF, or null if there's none (or it can't be read). */
    private fun readGpsExif(file: File): DoubleArray? = try {
        AndroidXExifInterface(file.absolutePath).latLong
    } catch (e: Exception) {
        null
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
            appendGalleryLog(
                if (location != null) {
                    getString(R.string.camera_gallery_current_location, location.latitude, location.longitude)
                } else {
                    getString(R.string.camera_gallery_current_location_unknown)
                }
            )
            cameraExecutor.execute {
                var successCount = 0
                var failureCount = 0
                val cardData = mutableListOf<UploadCardData>()
                val thumbnailSizePx = (THUMBNAIL_SIZE_DIP * resources.displayMetrics.density).toInt()
                uris.forEachIndexed { index, uri ->
                    val label = getString(R.string.camera_gallery_photo_label, index + 1, uris.size)
                    appendGalleryLog(getString(R.string.camera_gallery_preparing, label))
                    val file = copyUriToTempFile(uri)
                    if (file == null) {
                        failureCount++
                        appendGalleryLog(getString(R.string.camera_gallery_cannot_read, label))
                        return@forEachIndexed
                    }
                    val exifLatLong = readGpsExif(file)
                    if (exifLatLong == null) {
                        failureCount++
                        appendGalleryLog(getString(R.string.camera_gallery_no_exif_gps, label))
                        file.delete()
                        return@forEachIndexed
                    }
                    appendGalleryLog(getString(R.string.camera_gallery_exif_gps_found, label, exifLatLong[0], exifLatLong[1]))
                    try {
                        appendGalleryLog(getString(R.string.camera_gallery_processing, label))
                        val result = runBlocking {
                            wallpaperRepository.uploadCameraPhoto(
                                file = file,
                                latitude = exifLatLong[0],
                                longitude = exifLatLong[1],
                            )
                        }
                        successCount++
                        cardData.add(
                            UploadCardData(
                                thumbnail = decodeSampledBitmap(uri, thumbnailSizePx),
                                source = getString(R.string.camera_source_gallery),
                                locationName = result.location,
                                processedUrl = result.processedUrl,
                                uploadFailureReason = null,
                            )
                        )
                        appendGalleryLog(getString(R.string.camera_gallery_saved, label))
                    } catch (e: Exception) {
                        failureCount++
                        appendGalleryLog(getString(R.string.camera_gallery_failed, label, describeUploadError(e)))
                        val rejectionCode = extractRejectionReasonCode(e)
                        if (rejectionCode != null) {
                            cardData.add(
                                UploadCardData(
                                    thumbnail = decodeSampledBitmap(uri, thumbnailSizePx),
                                    source = getString(R.string.camera_source_gallery),
                                    locationName = null,
                                    processedUrl = null,
                                    uploadFailureReason = rejectionCode,
                                )
                            )
                        }
                    } finally {
                        file.delete()
                    }
                }
                appendGalleryLog("\n" + getString(R.string.camera_gallery_result, successCount, failureCount))
                runOnUiThread {
                    binding.setLiveWallpaperButton.visibility = if (successCount > 0) View.VISIBLE else View.GONE
                    binding.closeButton.visibility = View.VISIBLE
                }
                renderResultCards(cardData)
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

    private data class UploadCardData(
        val thumbnail: Bitmap?,
        val source: String,
        val locationName: String?,
        /** Set on a successful upload; used to fetch `/check` diagnostics. Null on rejection. */
        val processedUrl: String?,
        /** Server rejection code (e.g. "no_sky_at_top") when the upload itself was rejected. */
        val uploadFailureReason: String?,
    )

    /**
     * Calls RemoveSky's `/check` for each successfully uploaded photo and renders one result
     * card per attempted photo (success or rejected) — replaces the upload-progress log once
     * done. Rejected photos never reached `/check`, so they only show the rejection reason.
     */
    private fun renderResultCards(results: List<UploadCardData>) {
        binding.progressBar.let { runOnUiThread { it.visibility = View.GONE } }
        if (results.isEmpty()) return
        val checked = results.map { data ->
            data to data.processedUrl?.let { runBlocking { wallpaperRepository.checkUploadedPhoto(it) } }
        }
        runOnUiThread {
            binding.resultTextScroll.visibility = View.GONE
            binding.uploadResultCardsContainer.removeAllViews()
            checked.forEach { (data, check) ->
                binding.uploadResultCardsContainer.addView(buildResultCard(data, check))
            }
            binding.uploadResultCardsScroll.visibility = View.VISIBLE
        }
    }

    private fun buildResultCard(data: UploadCardData, check: RemoveSkyCheckResult?): View {
        val card = layoutInflater.inflate(R.layout.item_camera_upload_result, binding.uploadResultCardsContainer, false)
        val thumbnailView = card.findViewById<ImageView>(R.id.cardThumbnail)
        if (data.thumbnail != null) {
            thumbnailView.setImageBitmap(data.thumbnail)
        } else {
            thumbnailView.visibility = View.GONE
        }
        card.findViewById<TextView>(R.id.cardSource).text = getString(R.string.camera_card_source, data.source)

        val locationView = card.findViewById<TextView>(R.id.cardLocation)
        if (data.locationName != null) {
            locationView.visibility = View.VISIBLE
            locationView.text = getString(R.string.camera_card_location, data.locationName)
        }

        val reasonView = card.findViewById<TextView>(R.id.cardReason)
        when {
            check != null && check.ok -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#2E7D32"))
                reasonView.text = "✓ " + getString(R.string.camera_check_ok)
            }
            check != null && !check.ok -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + reasonText(check.reason)
            }
            data.uploadFailureReason != null -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + reasonText(data.uploadFailureReason)
            }
            else -> reasonView.visibility = View.GONE
        }

        val checksGroup = card.findViewById<ChipGroup>(R.id.cardChecksGroup)
        check?.checks?.let { c ->
            addCheckChip(checksGroup, R.string.camera_check_sky, c.hasSkyTop)
            addCheckChip(checksGroup, R.string.camera_check_outdoor, c.isOutdoor)
            addCheckChip(checksGroup, R.string.camera_check_color, c.hasColor)
            addCheckChip(checksGroup, R.string.camera_check_gps, c.hasGps)
            addCheckChip(checksGroup, R.string.camera_check_date, c.hasDate)
        }

        val badgesGroup = card.findViewById<ChipGroup>(R.id.cardBadgesGroup)
        check?.checks?.isNightVisual?.let { isNight ->
            val label = getString(if (isNight) R.string.wallpaper_photo_meta_night else R.string.wallpaper_photo_meta_day)
            addBadgeChip(badgesGroup, label)
        }
        check?.checks?.seasonVisual?.let { season ->
            seasonLabel(season)?.let { addBadgeChip(badgesGroup, it) }
        }

        return card
    }

    private fun addCheckChip(group: ChipGroup, @StringRes labelRes: Int, value: Boolean?) {
        val chip = layoutInflater.inflate(R.layout.item_camera_check_chip, group, false) as Chip
        chip.text = getString(labelRes)
        val color = when (value) {
            true -> "#C8E6C9"
            false -> "#FFCDD2"
            null -> "#E0E0E0"
        }
        chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor(color))
        group.addView(chip)
    }

    private fun addBadgeChip(group: ChipGroup, label: String) {
        val chip = layoutInflater.inflate(R.layout.item_camera_check_chip, group, false) as Chip
        chip.text = label
        chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#BBDEFB"))
        group.addView(chip)
    }

    private fun reasonText(reason: String?): String = when (reason) {
        "no_sky_at_top" -> getString(R.string.camera_result_no_sky_at_top)
        "insufficient_sky_in_top_region" -> getString(R.string.camera_result_too_little_sky)
        "clip_not_landscape" -> getString(R.string.camera_result_not_landscape)
        else -> getString(R.string.camera_result_unknown_reason)
    }

    private fun seasonLabel(season: String): String? = when (season) {
        "winter" -> getString(R.string.wallpaper_photo_meta_season_winter)
        "spring" -> getString(R.string.wallpaper_photo_meta_season_spring)
        "summer" -> getString(R.string.wallpaper_photo_meta_season_summer)
        "autumn" -> getString(R.string.wallpaper_photo_meta_season_autumn)
        else -> null
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
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
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
