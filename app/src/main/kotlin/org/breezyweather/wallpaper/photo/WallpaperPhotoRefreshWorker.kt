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

package org.breezyweather.wallpaper.photo

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
import breezyweather.data.location.LocationRepository
import breezyweather.domain.location.model.Location
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.breezyweather.BuildConfig
import org.breezyweather.common.extensions.isOnline
import org.breezyweather.common.extensions.isRunning
import org.breezyweather.common.extensions.workManager
import java.util.concurrent.TimeUnit

/**
 * Periodically fetches a fresh RemoveSky background photo per location and stores it through the
 * existing [WallpaperImageStore] / [WallpaperRepository] cache (ACT-010).
 *
 * This worker is the only place in the app that downloads wallpaper background photos in the
 * background. [org.breezyweather.wallpaper.MaterialLiveWallpaperService] only reads the resulting
 * local cache and never performs network or location requests itself.
 */
@HiltWorker
class WallpaperPhotoRefreshWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRepository: LocationRepository,
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

        val repository = WallpaperRepository(context, store)
        val now = System.currentTimeMillis()
        var refreshedCount = 0
        var skippedCount = 0

        return try {
            val locations = WallpaperPhotoRefreshPlanner.locationsToProcess(
                locationRepository.getXLocations(MAX_LOCATIONS)
            )
            locations.forEach { location ->
                val lastRefreshedAt = store.photoRefreshedAtFor(location.formattedId)
                if (!WallpaperPhotoRefreshPlanner.needsRefresh(lastRefreshedAt, now)) {
                    skippedCount++
                    return@forEach
                }

                val place = location.toWallpaperPlaceQuery()
                val candidate = repository.resolveImage(location.latitude, location.longitude, place)
                if (WallpaperPhotoRefreshPlanner.shouldSkipDownload(candidate?.url, store.cachedPhotoUrl)) {
                    store.setPhotoRefreshedAt(location.formattedId, now)
                    skippedCount++
                    return@forEach
                }

                val file = repository.refreshFor(location.latitude, location.longitude, place)
                if (file != null) {
                    store.setPhotoRefreshedAt(location.formattedId, now)
                    refreshedCount++
                } else {
                    skippedCount++
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
        private const val INTERVAL_HOURS = 24L
        private const val BACKOFF_DELAY_MINUTES = 30L

        /** Cap on the number of locations checked per run, mirroring [breezyweather.data.location.LocationRepository]'s usage in WeatherUpdateJob. */
        private const val MAX_LOCATIONS = 5

        /** Schedules (or re-confirms) the periodic refresh. Safe to call repeatedly. */
        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
                requiresBatteryNotLow = true
            )

            val request = PeriodicWorkRequestBuilder<WallpaperPhotoRefreshWorker>(
                INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .addTag(TAG)
                .addTag(WORK_NAME_AUTO)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                WORK_NAME_AUTO,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Cancels the periodic refresh, e.g. when the photo background is disabled. */
        fun cancel(context: Context) {
            context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
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
    }
}

/** Builds the [PlaceQuery] used to resolve a background photo for [this] location. */
internal fun Location.toWallpaperPlaceQuery(): PlaceQuery = PlaceQuery(
    city = city.ifBlank { null },
    municipality = admin2,
    state = admin1,
    country = country.ifBlank { null }
)
