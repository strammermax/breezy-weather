/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper

import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import breezyweather.data.location.LocationRepository
import breezyweather.data.wallpaper.WallpaperPhotoRecord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.ui.common.widgets.Material3Scaffold
import org.breezyweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import org.breezyweather.ui.theme.compose.BreezyWeatherTheme
import org.breezyweather.wallpaper.photo.PlaceQuery
import org.breezyweather.wallpaper.photo.WallpaperRepository
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class WallpaperPhotoManagerActivity : BreezyActivity() {

    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var locationRepository: LocationRepository

    private var photos by mutableStateOf<List<WallpaperPhotoRecord>>(emptyList())
    private var currentLocationKey by mutableStateOf<String?>(null)
    private var showAll by mutableStateOf(false)
    private var busyPhotoId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreezyWeatherTheme {
                Screen()
            }
        }
        loadPhotos()
    }

    private fun loadPhotos() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val location = locationRepository.getFirstLocation(withParameters = false)
                val locationKey = location?.let {
                    PlaceQuery(
                        city = it.city.ifBlank { null },
                        municipality = it.admin2,
                        state = it.admin1,
                        country = it.country.ifBlank { null },
                    ).cacheFileName().substringBeforeLast('.')
                }
                locationKey to wallpaperRepository.managedPhotos()
            }
            currentLocationKey = result.first
            photos = result.second
        }
    }

    private fun updateRating(photo: WallpaperPhotoRecord, rating: Int) {
        lifecycleScope.launch {
            busyPhotoId = photo.id
            withContext(Dispatchers.IO) { wallpaperRepository.setPhotoRating(photo.id, rating) }
            photos = withContext(Dispatchers.IO) { wallpaperRepository.managedPhotos() }
            busyPhotoId = null
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
                    onBackPressed = { finish() },
                )
            },
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !showAll,
                            onClick = { showAll = false },
                            label = { Text(stringResource(R.string.wallpaper_photo_filter_current)) },
                        )
                        FilterChip(
                            selected = showAll,
                            onClick = { showAll = true },
                            label = { Text(stringResource(R.string.wallpaper_photo_filter_all)) },
                        )
                    }
                }
                if (visiblePhotos.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.wallpaper_photo_manager_empty),
                            modifier = Modifier.padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    @Composable
    private fun PhotoCard(photo: WallpaperPhotoRecord) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                PhotoPreview(photo)
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(photo.locationName, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.wallpaper_photo_views, photo.viewCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (busyPhotoId == photo.id) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(
                                onClick = { updateRating(photo, if (photo.rating == 1) 0 else 1) },
                                enabled = !photo.disabled,
                            ) {
                                Icon(
                                    Icons.Default.ThumbUp,
                                    contentDescription = stringResource(R.string.wallpaper_photo_rate_up),
                                    tint = if (photo.rating == 1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(
                                onClick = { updateRating(photo, if (photo.rating == -1) 0 else -1) },
                                enabled = !photo.disabled,
                            ) {
                                Icon(
                                    Icons.Default.ThumbDown,
                                    contentDescription = stringResource(R.string.wallpaper_photo_rate_down),
                                    tint = if (photo.rating == -1) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (photo.disabled) {
                        Button(
                            onClick = { updateDisabled(photo) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = busyPhotoId == null,
                        ) {
                            Text(stringResource(R.string.wallpaper_photo_enable))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { updateDisabled(photo) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = busyPhotoId == null,
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
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = photo.locationName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = if (photo.disabled) 0.35f else 1f,
                )
            } else {
                Text(
                    text = if (photo.disabled) {
                        stringResource(R.string.wallpaper_photo_disabled)
                    } else {
                        stringResource(R.string.wallpaper_photo_file_missing)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}
