/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package com.liveweatherwallpaperapp.wallpaper.photo

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.liveweatherwallpaperapp.BuildConfig
import com.liveweatherwallpaperapp.common.extensions.isOnline
import com.liveweatherwallpaperapp.common.extensions.isRunning
import com.liveweatherwallpaperapp.common.extensions.workManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.weather.WeatherRepository
import livewallpaperweather.domain.location.model.Location
import java.util.concurrent.TimeUnit

/**
 * Periodically fetches a fresh RemoveSky background photo per location and stores it through the
 * existing [WallpaperImageStore] / [WallpaperRepository] cache (ACT-010).
 *
 * This worker is the only place in the app that downloads wallpaper background photos in the
 * background. [com.liveweatherwallpaperapp.wallpaper.MaterialLiveWallpaperService] only reads the resulting
 * local cache and never performs network or location requests itself.
 */
@HiltWorker
class WallpaperPhotoRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val wallpaperLocationResolver: WallpaperLocationResolver,
    private val weatherRepository: WeatherRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // Exit early in case there is no network and Android still executes the job.
        if (!context.isOnline()) {
            return Result.retry()
        }

        val store = WallpaperImageStore(context)
        if (!store.photoBackgroundEnabled) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        var refreshedCount = 0
        var skippedCount = 0

        return try {
            val locations = WallpaperPhotoRefreshPlanner.locationsToProcess(
                locationRepository.getXLocations(MAX_LOCATIONS)
            )
            locations.forEachIndexed { index, location ->
                val isActivating = WallpaperPhotoRefreshPlanner.shouldActivateLocation(index)

                // For the location that drives the active wallpaper photo, prefer a fresh
                // network-based fix over the (possibly stale) stored coordinates: this worker
                // runs far more often than the app's regular weather/location refresh, and a
                // missing/denied permission just falls back to the stored location below.
                val fix = if (isActivating) wallpaperLocationResolver.resolve() else null
                val latitude = fix?.latitude ?: location.latitude
                val longitude = fix?.longitude ?: location.longitude
                val place = fix?.place ?: location.toWallpaperPlaceQuery()

                val activeRemoved = wallpaperRepository.pruneDisabledPhotos(
                    latitude = latitude,
                    longitude = longitude,
                    place = place
                )

                val lastRefreshedAt = store.photoRefreshedAtFor(location.formattedId)
                val minIntervalMillis = TimeUnit.MINUTES.toMillis(store.photoRefreshIntervalMinutes.toLong())
                if (!WallpaperPhotoRefreshPlanner.needsRefresh(lastRefreshedAt, now, minIntervalMillis) &&
                    !activeRemoved
                ) {
                    skippedCount++
                    return@forEachIndexed
                }

                // Best-effort: a missing/stale weather fetch just means the showlist's weather
                // tier is skipped (buildShowlist treats null as "don't filter on weather").
                val currentWeather = weatherCodeToRemoveSkyWeather(
                    weatherRepository.getWeatherByLocationId(location.formattedId)?.current?.weatherCode
                )

                val file = wallpaperRepository.refreshFor(
                    latitude = latitude,
                    longitude = longitude,
                    place = place,
                    forceRefresh = true,
                    activate = isActivating || activeRemoved,
                    currentWeather = currentWeather,
                    isCurrentPosition = location.isCurrentPosition
                )
                if (file != null) {
                    store.setPhotoRefreshedAt(location.formattedId, now)
                    store.clearEmptyRetryCount(location.formattedId)
                    refreshedCount++
                    // Warms the cache for this location's *next* pick ahead of time -- best
                    // effort, never blocks the current tick's activation on it.
                    wallpaperRepository.prefetchShowlist(
                        latitude,
                        longitude,
                        place,
                        currentWeather,
                        isCurrentPosition = location.isCurrentPosition
                    )
                } else {
                    skippedCount++
                    // Neither the cache nor the server had anything usable for the location that
                    // drives the active wallpaper photo: don't wait a full rotation interval,
                    // try again soon instead (e.g. a freshly-added location with no photos yet).
                    // The delay escalates with repeated empty results (see RETRY_BACKOFF_MINUTES)
                    // so a genuinely dead location doesn't get polled as aggressively forever.
                    if (isActivating) {
                        val attempt = store.incrementEmptyRetryCount(location.formattedId)
                        val delayMinutes = WallpaperImageStore.RETRY_BACKOFF_MINUTES[
                            (attempt - 1).coerceAtMost(WallpaperImageStore.RETRY_BACKOFF_MINUTES.lastIndex)
                        ]
                        scheduleRetrySoon(context, delayMinutes)
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "refresh done: locations=${locations.size} refreshed=$refreshedCount skipped=$skippedCount"
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "refresh failed: ${e.javaClass.simpleName}")
            }
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WallpaperPhotoRefresh"
        private const val WORK_NAME_AUTO = "WallpaperPhotoRefresh-auto"
        private const val WORK_NAME_MANUAL = "WallpaperPhotoRefresh-manual"
        private const val WORK_NAME_RETRY = "WallpaperPhotoRefresh-retry"
        private const val BACKOFF_DELAY_MINUTES = 30L

        /** Cap on the number of locations checked per run, mirroring [livewallpaperweather.data.location.LocationRepository]'s usage in WeatherUpdateJob. */
        private const val MAX_LOCATIONS = 5

        /**
         * Schedules (or reschedules) the periodic rotation using the user-configured interval
         * ([WallpaperImageStore.photoRefreshIntervalMinutes]). Safe to call repeatedly; uses
         * [ExistingPeriodicWorkPolicy.UPDATE] so changing the interval takes effect immediately
         * instead of being ignored by an already-enqueued request.
         */
        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
                requiresBatteryNotLow = true
            )
            val intervalMinutes = WallpaperImageStore(context).photoRefreshIntervalMinutes.toLong()

            val request = PeriodicWorkRequestBuilder<WallpaperPhotoRefreshWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .addTag(TAG)
                .addTag(WORK_NAME_AUTO)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                WORK_NAME_AUTO,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Cancels the periodic rotation, e.g. when the photo background is disabled. */
        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            context.workManager.cancelUniqueWork(WORK_NAME_RETRY)
        }

        /** Triggers an immediate one-off refresh. Returns false if one is already running. */
        fun startNow(context: Context): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                return false
            }

            val request = OneTimeWorkRequestBuilder<WallpaperPhotoRefreshWorker>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)
            return true
        }

        /**
         * One-off retry shortly after a rotation found nothing for the active location, instead
         * of waiting for the next full [WallpaperImageStore.photoRefreshIntervalMinutes] tick.
         * Replaces any already-scheduled retry so repeated empty results don't pile up.
         * [delayMinutes] escalates with consecutive empty results -- see RETRY_BACKOFF_MINUTES.
         */
        private fun scheduleRetrySoon(context: Context, delayMinutes: Long) {
            val request = OneTimeWorkRequestBuilder<WallpaperPhotoRefreshWorker>()
                .addTag(TAG)
                .addTag(WORK_NAME_RETRY)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            context.workManager.enqueueUniqueWork(WORK_NAME_RETRY, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

/** Builds the [PlaceQuery] used to resolve a background photo for [this] location. */
internal fun Location.toWallpaperPlaceQuery(): PlaceQuery = PlaceQuery(
    city = city.ifBlank { null },
    municipality = admin2,
    state = admin1,
    country = country.ifBlank { null }
)
