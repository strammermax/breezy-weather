package com.liveweatherwallpaperapp.ui.camera

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
import android.widget.CheckBox
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.utils.DiagnosticLogger
import com.liveweatherwallpaperapp.databinding.ActivityCameraBinding
import com.liveweatherwallpaperapp.wallpaper.launchLiveWallpaperPicker
import com.liveweatherwallpaperapp.wallpaper.photo.CameraUploadResult
import com.liveweatherwallpaperapp.wallpaper.photo.RemoveSkyCheckOutcome
import com.liveweatherwallpaperapp.wallpaper.photo.RemoveSkyHttpException
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import androidx.exifinterface.media.ExifInterface as AndroidXExifInterface

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
    private val progressLog = StringBuilder()

    /** The just-captured photo waiting in the pre-upload preview, or null once uploaded/discarded. */
    private var pendingCaptureFile: File? = null
    private var pendingCaptureBitmap: Bitmap? = null

    /** An approved, uploaded-but-not-yet-activated camera photo, set by [processButton]. */
    private var pendingActivation: CameraUploadResult? = null

    /** Whether the current result screen came from the gallery batch flow (vs. a single capture). */
    private var isGalleryFlow = false

    /** Set by [stopUploadButton] — checked between photos so a gallery batch can be stopped early. */
    @Volatile
    private var galleryUploadCancelled = false

    /** Tag on the in-flight single-photo upload request, so [stopUploadButton] can abort it. */
    private var currentUploadCancelTag: Any? = null

    /** Set by [stopUploadButton] for the single-photo flow — suppresses the error UI once cancelled. */
    @Volatile
    private var singleUploadCancelled = false

    /** Checkbox -> upload result for each approved gallery card, read by [saveSelectedButton]. */
    private val gallerySelection = mutableListOf<Pair<CheckBox, CameraUploadResult>>()

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
            "TAG_JPEG_INTERCHANGE_FORMAT_LENGTH"
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
            if (isGalleryFlow) {
                // "Herkansing" for a gallery batch means re-picking photos, not going to the camera.
                launchGalleryPicker()
                return@setOnClickListener
            }
            pendingCaptureFile?.delete()
            pendingCaptureFile = null
            pendingCaptureBitmap = null
            showCameraView()
        }

        binding.previewUploadButton.setOnClickListener {
            val file = pendingCaptureFile ?: return@setOnClickListener
            val bitmap = pendingCaptureBitmap ?: return@setOnClickListener
            pendingCaptureFile = null
            pendingCaptureBitmap = null
            startUpload(file, bitmap)
        }

        binding.stopUploadButton.setOnClickListener {
            when {
                // Already stopped once — the button now closes the screen.
                galleryUploadCancelled || singleUploadCancelled -> finish()
                isGalleryFlow -> {
                    galleryUploadCancelled = true
                    currentUploadCancelTag?.let { wallpaperRepository.cancelCameraUpload(it) }
                    binding.stopUploadButton.text = getString(R.string.camera_close)
                }
                else -> {
                    singleUploadCancelled = true
                    currentUploadCancelTag?.let { wallpaperRepository.cancelCameraUpload(it) }
                    binding.stopUploadButton.text = getString(R.string.camera_close)
                }
            }
        }

        binding.processButton.setOnClickListener {
            val result = pendingActivation ?: return@setOnClickListener
            pendingActivation = null
            binding.processButton.isEnabled = false
            cameraExecutor.execute {
                runBlocking { wallpaperRepository.activateCameraPhoto(result) }
                runOnUiThread {
                    Toast.makeText(this, R.string.camera_photo_processed, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        binding.saveSelectedButton.setOnClickListener {
            val selected = gallerySelection.filter { (checkbox, _) -> checkbox.isChecked }.map { it.second }
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.camera_no_photos_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.saveSelectedButton.isEnabled = false
            cameraExecutor.execute {
                // Only one photo can be "active" at a time; activating each in turn leaves the
                // last selected photo as the live wallpaper background, but every selected photo
                // still gets cached and catalogued along the way.
                runBlocking { selected.forEach { wallpaperRepository.activateCameraPhoto(it) } }
                runOnUiThread {
                    binding.saveSelectedButton.visibility = View.GONE
                    binding.saveSelectedButton.isEnabled = true
                    Toast.makeText(
                        this,
                        getString(R.string.camera_photos_saved, selected.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        binding.setLiveWallpaperButton.setOnClickListener {
            if (!launchLiveWallpaperPicker(this)) {
                Toast.makeText(this, R.string.camera_live_wallpaper_unavailable, Toast.LENGTH_SHORT).show()
            }
        }

        binding.closeButton.setOnClickListener {
            finish()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun showCameraView() {
        pendingActivation = null
        isGalleryFlow = false
        gallerySelection.clear()
        captureInProgress = false
        binding.captureButton.isEnabled = true
        binding.cameraPreviewView.visibility = View.VISIBLE
        binding.horizonGuideLine.visibility = View.VISIBLE
        binding.horizonGuideLabel.visibility = View.VISIBLE
        binding.captureButton.visibility = View.VISIBLE
        binding.resultImageView.visibility = View.GONE
        binding.uploadResultCardsScroll.visibility = View.GONE
        binding.resultTextView.visibility = View.GONE
        binding.stopUploadButton.visibility = View.GONE
        binding.retakeButton.visibility = View.GONE
        binding.previewUploadButton.visibility = View.GONE
        binding.processButton.visibility = View.GONE
        binding.saveSelectedButton.visibility = View.GONE
        binding.setLiveWallpaperButton.visibility = View.GONE
        binding.closeButton.visibility = View.GONE
        binding.resultContainer.visibility = View.GONE
    }

    /** Shows the just-captured photo with a choice to retake or upload it — nothing is sent yet. */
    private fun showCapturePreview(file: File, bitmap: Bitmap) {
        isGalleryFlow = false
        pendingCaptureFile = file
        pendingCaptureBitmap = bitmap
        binding.cameraPreviewView.visibility = View.GONE
        binding.horizonGuideLine.visibility = View.GONE
        binding.horizonGuideLabel.visibility = View.GONE
        binding.captureButton.visibility = View.GONE
        binding.resultImageView.setImageBitmap(bitmap)
        binding.resultImageView.visibility = View.VISIBLE
        binding.resultTextScroll.visibility = View.GONE
        binding.uploadResultCardsScroll.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.stopUploadButton.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE
        binding.previewUploadButton.visibility = View.VISIBLE
        binding.processButton.visibility = View.GONE
        binding.saveSelectedButton.visibility = View.GONE
        binding.setLiveWallpaperButton.visibility = View.GONE
        binding.closeButton.visibility = View.GONE
        binding.resultContainer.visibility = View.VISIBLE
    }

    private fun showResultView() {
        binding.cameraPreviewView.visibility = View.GONE
        binding.horizonGuideLine.visibility = View.GONE
        binding.horizonGuideLabel.visibility = View.GONE
        binding.captureButton.visibility = View.GONE
        // Left GONE until a bitmap is actually set (e.g. per-photo in uploadGalleryPhotos) --
        // making it visible empty painted a black rectangle whenever a photo was skipped
        // before ever being decoded.
        binding.resultImageView.setImageDrawable(null)
        binding.resultImageView.visibility = View.GONE
        binding.resultTextScroll.visibility = View.VISIBLE
        binding.resultTextView.visibility = View.VISIBLE
        binding.uploadResultCardsScroll.visibility = View.GONE
        binding.retakeButton.visibility = View.VISIBLE
        binding.previewUploadButton.visibility = View.GONE
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
                    decodeAndShowPreview(photoFile)
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

    /** Decodes a JPEG at (roughly) [maxDimension] and applies its EXIF rotation. */
    private fun decodeRotatedBitmap(file: File, maxDimension: Int = CAMERA_UPLOAD_MAX_DIMENSION): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Captured photo could not be decoded" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val bitmap = requireNotNull(
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        ) { "Captured photo could not be decoded" }
        val rotationDegrees = when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.WEBP
                    },
                    CAMERA_UPLOAD_WEBP_QUALITY,
                    output
                )
            }
            check(written) { "WebP conversion failed" }
            copyExifAttributes(sourceJpeg, webp)
            DiagnosticLogger.log(
                this,
                "Camera",
                "WebP reference=${webp.name} (${bitmap.width}x${bitmap.height}, ${webp.length()} bytes, EXIF copied)"
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
            AndroidXExifInterface.ORIENTATION_NORMAL.toString()
        )
        destination.saveAttributes()
    }

    /** Decodes the capture off the main thread and shows it in the retake/upload preview. */
    private fun decodeAndShowPreview(file: File) {
        cameraExecutor.execute {
            try {
                val bitmap = decodeRotatedBitmap(file)
                runOnUiThread { showCapturePreview(file, bitmap) }
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

    /** Starts the actual upload once the user confirms the preview; reports progress step by step. */
    private fun startUpload(file: File, bitmap: Bitmap) {
        resetProgressLog()
        singleUploadCancelled = false
        galleryUploadCancelled = false
        currentUploadCancelTag = Any()
        binding.stopUploadButton.text = getString(R.string.camera_stop_upload)
        binding.stopUploadButton.isEnabled = true
        binding.progressBar.visibility = View.VISIBLE
        showResultView()
        binding.resultImageView.setImageBitmap(bitmap)
        binding.resultImageView.visibility = View.VISIBLE
        // "Stoppen" (not "Herkansing") is the relevant action while the upload is in flight —
        // retake would leave the upload running unattended in the background.
        binding.retakeButton.visibility = View.GONE
        binding.stopUploadButton.visibility = View.VISIBLE
        appendProgressLog(getString(R.string.camera_step_location))

        fetchLocation { location ->
            appendProgressLog(
                if (location != null) {
                    getString(R.string.camera_gallery_current_location, location.latitude, location.longitude)
                } else {
                    getString(R.string.camera_gallery_current_location_unknown)
                }
            )
            cameraExecutor.execute {
                uploadImage(file, bitmap, location)
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
            appendProgressLog(getString(R.string.camera_step_uploading))
            DiagnosticLogger.log(this, "Camera", "Upload started")

            val result = runBlocking {
                wallpaperRepository.uploadCameraPhoto(
                    file = uploadFile,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    // The camera flow activates explicitly via the "Verwerk" button once the
                    // user has seen the suitability check, not automatically on upload.
                    activate = false,
                    // Fresh capture: the device's local clock IS the capture time, so it's a
                    // safe fallback for when the photo itself has no EXIF date (some phones
                    // don't write one). Includes the UTC offset so a photo taken abroad (e.g.
                    // in the US) still resolves to the correct local time, not this device's
                    // home timezone. Deliberately NOT sent from the gallery-import flow below,
                    // where "now" has nothing to do with when the photo was actually taken.
                    capturedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    cancelTag = currentUploadCancelTag
                )
            }
            DiagnosticLogger.log(
                this,
                "Camera",
                "Upload and server processing completed; processedUrl=${result.processedUrl}"
            )
            appendProgressLog(getString(R.string.camera_step_uploading_done))
            appendProgressLog(getString(R.string.camera_step_checking))

            renderResultCards(
                listOf(
                    UploadCardData(
                        thumbnail = bitmap,
                        source = getString(R.string.camera_source_camera),
                        locationName = result.location,
                        processedUrl = result.processedUrl,
                        uploadFailureReason = null,
                        activationResult = result
                    )
                )
            )
        } catch (e: Exception) {
            if (singleUploadCancelled) {
                // The "Stoppen" click already switched the button to "Sluiten" — nothing more to show.
                DiagnosticLogger.log(this, "Camera", "Upload cancelled by user")
                return
            }
            DiagnosticLogger.log(
                this,
                "Camera",
                "Upload failed" + (
                    (e as? RemoveSkyHttpException)?.let {
                        " (HTTP ${it.statusCode}, body=${it.responseBody})"
                    } ?: ""
                    ),
                e
            )
            val rejectionCode = extractRejectionReasonCode(e)
            if (rejectionCode != null || e is RemoveSkyHttpException) {
                renderResultCards(
                    listOf(
                        UploadCardData(
                            thumbnail = bitmap,
                            source = getString(R.string.camera_source_camera),
                            locationName = null,
                            processedUrl = null,
                            uploadFailureReason = rejectionCode ?: "unknown"
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
        isGalleryFlow = true
        galleryUploadCancelled = false
        singleUploadCancelled = false
        currentUploadCancelTag = Any()
        gallerySelection.clear()
        binding.stopUploadButton.text = getString(R.string.camera_stop_upload)
        binding.stopUploadButton.isEnabled = true
        binding.stopUploadButton.visibility = View.VISIBLE
        resetProgressLog()
        binding.progressBar.visibility = View.VISIBLE
        showResultView()
        binding.retakeButton.visibility = View.GONE // "Stoppen" is the relevant action while uploading

        fetchLocation { location ->
            appendProgressLog(
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
                for ((index, uri) in uris.withIndex()) {
                    if (galleryUploadCancelled) {
                        appendProgressLog(getString(R.string.camera_gallery_stopped))
                        break
                    }
                    val label = getString(R.string.camera_gallery_photo_label, index + 1, uris.size)
                    appendProgressLog(getString(R.string.camera_gallery_preparing, label))
                    val file = copyUriToTempFile(uri)
                    if (file == null) {
                        failureCount++
                        appendProgressLog(getString(R.string.camera_gallery_cannot_read, label))
                        continue
                    }
                    val exifLatLong = readGpsExif(file)
                    if (exifLatLong == null) {
                        failureCount++
                        appendProgressLog(getString(R.string.camera_gallery_no_exif_gps, label))
                        file.delete()
                        continue
                    }
                    appendProgressLog(
                        getString(R.string.camera_gallery_exif_gps_found, label, exifLatLong[0], exifLatLong[1])
                    )
                    var uploadFile: File? = null
                    try {
                        // Rotated to match its EXIF orientation up front — a gallery photo's raw
                        // sensor pixels are often portrait-rotated, and unlike the single-capture
                        // flow this file wasn't already normalised, so without this step some
                        // photos came out 90/180 degrees off once uploaded.
                        val bitmap = decodeRotatedBitmap(file)
                        // Shown large while this photo uploads, just like the single-capture flow.
                        runOnUiThread {
                            binding.resultImageView.setImageBitmap(bitmap)
                            binding.resultImageView.visibility = View.VISIBLE
                        }
                        uploadFile = createUploadWebp(file, bitmap)
                        appendProgressLog(getString(R.string.camera_gallery_processing, label))
                        val result = runBlocking {
                            wallpaperRepository.uploadCameraPhoto(
                                file = uploadFile,
                                latitude = exifLatLong[0],
                                longitude = exifLatLong[1],
                                // Activation is deferred to "Bewaar", after the user sees which
                                // photos were approved — same as the single-photo camera flow.
                                activate = false,
                                cancelTag = currentUploadCancelTag
                            )
                        }
                        successCount++
                        cardData.add(
                            UploadCardData(
                                thumbnail = decodeRotatedBitmap(file, thumbnailSizePx),
                                source = getString(R.string.camera_source_gallery),
                                locationName = result.location,
                                processedUrl = result.processedUrl,
                                uploadFailureReason = null,
                                activationResult = result
                            )
                        )
                        appendProgressLog(getString(R.string.camera_gallery_saved, label))
                    } catch (e: Exception) {
                        failureCount++
                        appendProgressLog(getString(R.string.camera_gallery_failed, label, describeUploadError(e)))
                        val rejectionCode = extractRejectionReasonCode(e)
                        if (rejectionCode != null) {
                            cardData.add(
                                UploadCardData(
                                    thumbnail = runCatching { decodeRotatedBitmap(file, thumbnailSizePx) }.getOrNull(),
                                    source = getString(R.string.camera_source_gallery),
                                    locationName = null,
                                    processedUrl = null,
                                    uploadFailureReason = rejectionCode
                                )
                            )
                        }
                    } finally {
                        uploadFile?.delete()
                        file.delete()
                    }
                }
                appendProgressLog("\n" + getString(R.string.camera_gallery_result, successCount, failureCount))
                runOnUiThread { binding.stopUploadButton.visibility = View.GONE }
                renderResultCards(cardData)
            }
        }
    }

    /** Resets the running upload-progress log shown in [ActivityCameraBinding.resultTextView]. */
    private fun resetProgressLog() {
        progressLog.setLength(0)
        binding.resultTextView.text = ""
    }

    /** Appends [line] to the running upload-progress log and scrolls it into view. */
    private fun appendProgressLog(line: String) {
        runOnUiThread {
            if (progressLog.isNotEmpty()) progressLog.append('\n')
            progressLog.append(line)
            binding.resultTextView.text = progressLog.toString()
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
        /**
         * Set only for the single-photo camera flow (uploaded with `activate = false`) — lets
         * the "Verwerk" button activate this exact photo once the user approves it. Null for the
         * gallery batch flow, which still activates each photo automatically on upload.
         */
        val activationResult: CameraUploadResult? = null,
    )

    /**
     * Calls RemoveSky's `/check` for each successfully uploaded photo and renders one result
     * card per attempted photo (success or rejected) — replaces the upload-progress log once
     * done. Rejected photos never reached `/check`, so they only show the rejection reason.
     */
    private fun renderResultCards(results: List<UploadCardData>) {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.stopUploadButton.visibility = View.GONE
        }
        if (results.isEmpty()) {
            runOnUiThread {
                binding.retakeButton.visibility = View.VISIBLE
                binding.closeButton.visibility = View.VISIBLE
            }
            return
        }
        val checked = results.map { data ->
            data to data.processedUrl?.let { runBlocking { wallpaperRepository.checkUploadedPhoto(it) } }
        }
        fun checkOk(check: RemoveSkyCheckOutcome?) = (check as? RemoveSkyCheckOutcome.Success)?.result?.ok == true
        val anyApproved = checked.any { (_, check) -> checkOk(check) }
        // The single-photo camera flow shows one "Verwerk" button for its one approved photo;
        // the gallery batch flow instead shows a checkbox per approved photo plus one "Bewaar"
        // button (wired below, per card) that activates every checked one.
        val approvedActivation = if (isGalleryFlow) {
            null
        } else {
            checked.firstOrNull { (data, check) -> checkOk(check) && data.activationResult != null }
                ?.first?.activationResult
        }
        pendingActivation = approvedActivation
        gallerySelection.clear()
        runOnUiThread {
            binding.resultTextScroll.visibility = View.GONE
            binding.resultImageView.visibility = View.GONE
            binding.uploadResultCardsContainer.removeAllViews()
            checked.forEach { (data, check) ->
                binding.uploadResultCardsContainer.addView(buildResultCard(data, check))
            }
            binding.uploadResultCardsScroll.visibility = View.VISIBLE
            binding.retakeButton.visibility = View.VISIBLE
            binding.processButton.visibility = if (approvedActivation != null) View.VISIBLE else View.GONE
            binding.saveSelectedButton.visibility =
                if (isGalleryFlow && gallerySelection.isNotEmpty()) View.VISIBLE else View.GONE
            binding.setLiveWallpaperButton.visibility = if (anyApproved) View.VISIBLE else View.GONE
            binding.closeButton.visibility = View.VISIBLE
        }
    }

    private fun buildResultCard(data: UploadCardData, check: RemoveSkyCheckOutcome?): View {
        val card = layoutInflater.inflate(R.layout.item_camera_upload_result, binding.uploadResultCardsContainer, false)
        val thumbnailView = card.findViewById<ImageView>(R.id.cardThumbnail)
        if (data.thumbnail != null) {
            thumbnailView.setImageBitmap(data.thumbnail)
        } else {
            thumbnailView.visibility = View.GONE
        }

        val checkResult = (check as? RemoveSkyCheckOutcome.Success)?.result

        // Gallery batch only: a checkbox per approved photo, pre-checked, feeding "Bewaar".
        // Rejected/failed photos never got an activationResult, so they get no checkbox at all.
        val selectView = card.findViewById<CheckBox>(R.id.cardSelect)
        if (isGalleryFlow && data.activationResult != null) {
            selectView.visibility = View.VISIBLE
            selectView.isChecked = checkResult?.ok == true
            gallerySelection.add(selectView to data.activationResult)
        } else {
            selectView.visibility = View.GONE
        }

        card.findViewById<TextView>(R.id.cardSource).text = getString(R.string.camera_card_source, data.source)

        val locationView = card.findViewById<TextView>(R.id.cardLocation)
        if (data.locationName != null) {
            locationView.visibility = View.VISIBLE
            locationView.text = getString(R.string.camera_card_location, data.locationName)
        }

        val reasonView = card.findViewById<TextView>(R.id.cardReason)
        when {
            checkResult?.ok == true -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#2E7D32"))
                reasonView.text = "✓ " + getString(R.string.camera_check_ok)
            }
            checkResult != null && !checkResult.ok -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + reasonText(checkResult.reason)
            }
            data.uploadFailureReason != null -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + reasonText(data.uploadFailureReason)
            }
            // Upload succeeded (we have a processedUrl) but /check itself didn't return a real
            // answer -- a timeout (RemoveSky's CPU-only sky/CLIP inference, see
            // RemoveSkyProvider.checkImage's kdoc, taking longer than the client's read timeout)
            // or a genuine server-side error. Previously both silently rendered a blank card.
            check is RemoveSkyCheckOutcome.Timeout -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + getString(R.string.camera_check_timeout)
            }
            check is RemoveSkyCheckOutcome.ServerError -> {
                reasonView.visibility = View.VISIBLE
                reasonView.setTextColor(Color.parseColor("#C62828"))
                reasonView.text = "✗ " + getString(R.string.camera_check_server_error)
            }
            else -> reasonView.visibility = View.GONE
        }

        val checksGroup = card.findViewById<ChipGroup>(R.id.cardChecksGroup)
        checkResult?.checks?.let { c ->
            addCheckChip(checksGroup, R.string.camera_check_sky, c.hasSkyTop)
            addCheckChip(checksGroup, R.string.camera_check_outdoor, c.isOutdoor)
            addCheckChip(checksGroup, R.string.camera_check_color, c.hasColor)
            addCheckChip(checksGroup, R.string.camera_check_gps, c.hasGps)
            addCheckChip(checksGroup, R.string.camera_check_date, c.hasDate)
        }

        val badgesGroup = card.findViewById<ChipGroup>(R.id.cardBadgesGroup)
        checkResult?.checks?.isNightVisual?.let { isNight ->
            val label =
                getString(if (isNight) R.string.wallpaper_photo_meta_night else R.string.wallpaper_photo_meta_day)
            addBadgeChip(badgesGroup, label)
        }
        checkResult?.checks?.seasonVisual?.let { season ->
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
        // The server can add new rejection codes independently of this app (it's a separate,
        // self-hosted service) — show the raw code rather than hiding it behind a generic
        // message, so an unrecognized rejection is still actionable/reportable.
        null -> getString(R.string.camera_result_unknown_reason)
        else -> getString(R.string.camera_result_unknown_reason_with_code, reason)
    }

    private fun seasonLabel(season: String): String? = when (season) {
        "winter" -> getString(R.string.wallpaper_photo_meta_season_winter)
        "spring" -> getString(R.string.wallpaper_photo_meta_season_spring)
        "summer" -> getString(R.string.wallpaper_photo_meta_season_summer)
        "autumn" -> getString(R.string.wallpaper_photo_meta_season_autumn)
        else -> null
    }

    /** Copies [uri]'s raw bytes into a private temp file, preserving its EXIF exactly. */
    private fun copyUriToTempFile(uri: Uri): File? = try {
        val file = File.createTempFile("gallery_", ".jpg", cacheDir)
        val copied = openOriginalInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (copied) {
            file
        } else {
            file.delete()
            null
        }
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
        grantResults: IntArray,
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
