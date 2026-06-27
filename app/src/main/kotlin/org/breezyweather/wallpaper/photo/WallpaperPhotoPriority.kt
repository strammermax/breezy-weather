/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.wallpaper.photo

import breezyweather.data.wallpaper.WallpaperPhotoRecord

internal fun selectWallpaperPhoto(
    photos: List<WallpaperPhotoRecord>,
    excludedUrls: Set<String>,
): WallpaperPhotoRecord? = photos
    .asSequence()
    .filterNot { it.disabled }
    .filterNot { it.sourceUrl != null && it.sourceUrl in excludedUrls }
    .sortedWith(
        compareByDescending<WallpaperPhotoRecord> { it.rating }
            .thenBy { it.viewCount }
            .thenBy { it.lastShownAt ?: Long.MIN_VALUE }
            .thenBy { it.createdAt }
    )
    .firstOrNull()
