/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.liveweatherwallpaperapp.wallpaper

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.wallpaper.photo.CheckForNewPhotosResult
import com.liveweatherwallpaperapp.wallpaper.photo.PlaceQuery
import com.liveweatherwallpaperapp.wallpaper.photo.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.wallpaper.WallpaperPhotoRecord
import livewallpaperweather.domain.location.model.Location
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class WallpaperPhotoManagerActivity : BreezyActivity() {

    @Inject lateinit var wallpaperRepository: WallpaperRepository

    @Inject lateinit var locationRepository: LocationRepository

    private var photos by mutableStateOf<List<WallpaperPhotoRecord>>(emptyList())
    private var currentLocationKey by mutableStateOf<String?>(null)
    private var currentLocationCoords by mutableStateOf<CurrentLocationCoords?>(null)
    private var showAll by mutableStateOf(false)
    private var busyPhotoId by mutableStateOf<String?>(null)
    private var checkingNewPhotos by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                Screen()
            }
        }
        loadPhotos()

        // Reload if the catalog changes from something other than this screen's own actions
        // (an FCM push purging a photo while this screen is already open) -- managedPhotos()
        // is a one-time snapshot, not an observed Flow, so without this the list goes stale.
        lifecycleScope.launch {
            wallpaperRepository.catalogChanged.collect { loadPhotos() }
        }
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val location = locationRepository.getFirstLocation(withParameters = false)
                val place = location?.let {
                    PlaceQuery(
                        city = it.city.ifBlank { null },
                        municipality = it.admin2,
                        state = it.admin1,
                        country = it.country.ifBlank { null }
                    )
                }
                val locationKey = location?.formattedId
                val coords = if (location != null && place != null) {
                    CurrentLocationCoords(location.latitude, location.longitude, place, location)
                } else {
                    null
                }
                Triple(locationKey, coords, wallpaperRepository.managedPhotos())
            }
            currentLocationKey = result.first
            currentLocationCoords = result.second
            photos = result.third
        }
    }

    private fun checkForNewPhotos() {
        val coords = currentLocationCoords ?: return
        lifecycleScope.launch {
            checkingNewPhotos = true
            val result = withContext(Dispatchers.IO) {
                wallpaperRepository.checkForNewPhotos(coords.latitude, coords.longitude, coords.place, coords.location)
            }
            if (result == CheckForNewPhotosResult.FOUND) {
                photos = withContext(Dispatchers.IO) { wallpaperRepository.managedPhotos() }
            }
            checkingNewPhotos = false
            Toast.makeText(
                this@WallpaperPhotoManagerActivity,
                getString(
                    when (result) {
                        CheckForNewPhotosResult.FOUND -> R.string.wallpaper_photo_check_new_found
                        CheckForNewPhotosResult.NONE_FOUND -> R.string.wallpaper_photo_check_new_none
                        CheckForNewPhotosResult.REQUEST_FAILED -> R.string.wallpaper_photo_check_new_failed
                    }
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateDisabled(photo: WallpaperPhotoRecord) {
        lifecycleScope.launch {
            busyPhotoId = photo.id
            withContext(Dispatchers.IO) {
                wallpaperRepository.setPhotoDisabled(photo.id, !photo.disabled)
            }
            photos = withContext(Dispatchers.IO) { wallpaperRepository.managedPhotos() }
            busyPhotoId = null
        }
    }

    @Composable
    private fun Screen() {
        Material3Scaffold(
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.wallpaper_photo_manager_title),
                    onBackPressed = { finish() }
                )
            }
        ) { paddings ->
            val visiblePhotos = if (showAll || currentLocationKey == null) {
                photos
            } else {
                photos.filter { it.locationKey == currentLocationKey }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddings)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showAll) {
                                stringResource(R.string.wallpaper_photo_filter_all)
                            } else {
                                stringResource(R.string.wallpaper_photo_filter_current)
                            }
                        )
                        Switch(
                            checked = showAll,
                            onCheckedChange = { showAll = it }
                        )
                    }
                }
                if (!showAll && currentLocationCoords != null) {
                    item {
                        if (checkingNewPhotos) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.wallpaper_photo_check_new))
                            }
                        } else {
                            OutlinedButton(onClick = { checkForNewPhotos() }) {
                                Text(stringResource(R.string.wallpaper_photo_check_new))
                            }
                        }
                    }
                }
                if (visiblePhotos.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.wallpaper_photo_manager_empty),
                            modifier = Modifier.padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(visiblePhotos, key = { it.id }) { photo ->
                    PhotoCard(photo)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    /** "Nederland · winter · dag" style caption from RemoveSky's country/season/day_period, or null if none known. */
    @Composable
    private fun photoMetaLine(photo: WallpaperPhotoRecord): String? {
        val seasonLabel = when (photo.season) {
            "winter" -> stringResource(R.string.wallpaper_photo_meta_season_winter)
            "spring" -> stringResource(R.string.wallpaper_photo_meta_season_spring)
            "summer" -> stringResource(R.string.wallpaper_photo_meta_season_summer)
            "autumn" -> stringResource(R.string.wallpaper_photo_meta_season_autumn)
            else -> null
        }
        val dayPeriodLabel = when (photo.dayPeriod) {
            "day" -> stringResource(R.string.wallpaper_photo_meta_day)
            "night" -> stringResource(R.string.wallpaper_photo_meta_night)
            else -> null
        }
        val parts = listOfNotNull(photo.country?.takeIf { it.isNotBlank() }, seasonLabel, dayPeriodLabel)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    @Composable
    private fun PhotoCard(photo: WallpaperPhotoRecord) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                PhotoPreview(photo)
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(photo.locationName, fontWeight = FontWeight.Bold)
                            val metaLine = photoMetaLine(photo)
                            if (metaLine != null) {
                                Text(
                                    metaLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                stringResource(R.string.wallpaper_photo_views, photo.viewCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (busyPhotoId == photo.id) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (photo.disabled) {
                        Button(
                            onClick = { updateDisabled(photo) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = busyPhotoId == null
                        ) {
                            Text(stringResource(R.string.wallpaper_photo_enable))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { updateDisabled(photo) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = busyPhotoId == null
                        ) {
                            Text(stringResource(R.string.wallpaper_photo_disable))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PhotoPreview(photo: WallpaperPhotoRecord) {
        var bitmap by remember(photo.filePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
        val density = LocalDensity.current
        LaunchedEffect(photo.filePath) {
            val targetHeightPx = with(density) { PREVIEW_HEIGHT.roundToPx() }
            bitmap = withContext(Dispatchers.IO) {
                photo.filePath?.takeIf { File(it).isFile }?.let { decodeSampledBitmap(it, targetHeightPx) }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = photo.locationName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (photo.disabled) 0.35f else 1f
                )
            } else {
                Text(
                    text = if (photo.disabled) {
                        stringResource(R.string.wallpaper_photo_disabled)
                    } else {
                        stringResource(R.string.wallpaper_photo_file_missing)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    companion object {
        private val PREVIEW_HEIGHT = 180.dp

        /** Decodes [path] downsampled so its height is close to [targetHeightPx], avoiding full-res loads for thumbnails. */
        private fun decodeSampledBitmap(path: String, targetHeightPx: Int): android.graphics.Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outHeight <= 0 || targetHeightPx <= 0) return BitmapFactory.decodeFile(path)
            var sampleSize = 1
            while (bounds.outHeight / (sampleSize * 2) >= targetHeightPx) {
                sampleSize *= 2
            }
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        }
    }

    private data class CurrentLocationCoords(
        val latitude: Double,
        val longitude: Double,
        val place: PlaceQuery,
        val location: Location,
    )
}
