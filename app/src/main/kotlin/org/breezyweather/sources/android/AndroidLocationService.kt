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

package org.breezyweather.sources.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.delay
import kotlinx.coroutines.rx3.rxObservable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.breezyweather.common.exceptions.LocationAccessOffException
import org.breezyweather.common.exceptions.LocationException
import org.breezyweather.common.extensions.hasPermission
import org.breezyweather.common.source.LocationPositionWrapper
import org.breezyweather.common.source.LocationSource
import org.breezyweather.common.utils.helpers.LogHelper
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class AndroidLocationService @Inject constructor() : LocationSource {

    override val id = "native"
    override val name = "Android"

    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListenerCompat

    private val bestProvider: String
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

    override fun requestLocation(context: Context): Observable<LocationPositionWrapper> {
        if (!hasPermissions(context)) {
            LogHelper.log(msg = "Location permissions missing")
            throw LocationException()
        }

        if (!this::locationManager.isInitialized) {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            LogHelper.log(msg = "Location service not enabled")
            throw LocationAccessOffException()
        }

        return rxObservable {
            locationListener = LocationListenerCompat {
                clearLocationUpdates(locationListener)
                val result = trySend(LocationPositionWrapper(longitude = it.longitude, latitude = it.latitude))
                if (!result.isSuccess) {
                    close(LocationException())
                }
            }

            LocationManagerCompat.requestLocationUpdates(
                locationManager,
                bestProvider,
                LocationRequestCompat.Builder(1000).apply {
                    setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                }.build(),
                locationListener,
                Looper.getMainLooper()
            )
            delay(TIMEOUT_MILLIS)

            // Fall back to the last known location if it failed to find the location in the given time frame
            clearLocationUpdates(locationListener)
            getLastKnownLocation(locationManager)?.let {
                send(LocationPositionWrapper(it.latitude, it.longitude))
            } ?: run {
                // Actually it’s a timeout, but it is more reasonable to say it failed to find location
                throw LocationException()
            }
        }.doOnDispose {
            if (this::locationListener.isInitialized) {
                clearLocationUpdates(locationListener)
            }
        }
    }

    private fun clearLocationUpdates(listener: LocationListenerCompat) {
        locationManager.removeUpdates(listener)
    }

    /**
     * One-shot, network/WiFi-based location fix — never the GPS chip or [LocationManager.FUSED_PROVIDER]
     * (which may use GPS internally). Used by features that need city-level accuracy frequently
     * (e.g. the live wallpaper photo rotation) where draining the GPS chip would be wasteful.
     *
     * Requires [Manifest.permission.ACCESS_BACKGROUND_LOCATION] (Android 10+) when called from a
     * background context such as a [androidx.work.Worker]; returns null rather than throwing when
     * permissions are missing, disabled, or no fix could be obtained in time, so callers can fall
     * back to their last-known location.
     */
    suspend fun requestNetworkLocation(context: Context): LocationPositionWrapper? {
        if (!hasBackgroundCapableLocationPermission(context)) return null

        if (!this::locationManager.isInitialized) {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return null
        if (!locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            return getLastKnownNetworkLocation(locationManager)
        }

        val fresh = withTimeoutOrNull(NETWORK_FIX_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                lateinit var listener: LocationListenerCompat
                listener = LocationListenerCompat { location ->
                    locationManager.removeUpdates(listener)
                    if (continuation.isActive) {
                        continuation.resume(LocationPositionWrapper(location.latitude, location.longitude))
                    }
                }
                continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    LocationManager.NETWORK_PROVIDER,
                    LocationRequestCompat.Builder(NETWORK_FIX_TIMEOUT_MILLIS)
                        .setQuality(LocationRequestCompat.QUALITY_LOW_POWER)
                        .build(),
                    listener,
                    Looper.getMainLooper()
                )
            }
        }
        return fresh ?: getLastKnownNetworkLocation(locationManager)
    }

    /** True when location is grantable from a background worker, i.e. coarse/fine *and* (on Q+) background. */
    private fun hasBackgroundCapableLocationPermission(context: Context): Boolean {
        val foreground = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!foreground) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            true
        }
    }

    override val permissions: Array<String>
        get() = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

    companion object {
        private val TIMEOUT_MILLIS = 15.seconds.inWholeMilliseconds
        private val NETWORK_FIX_TIMEOUT_MILLIS = 10.seconds.inWholeMilliseconds

        private fun getLastKnownLocation(
            locationManager: LocationManager,
        ): Location? {
            val lastKnownFused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            } else {
                null
            }
            return lastKnownFused
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }

        /** Last-known fix from network/passive providers only — deliberately skips GPS/FUSED. */
        private fun getLastKnownNetworkLocation(locationManager: LocationManager): LocationPositionWrapper? {
            val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            return location?.let { LocationPositionWrapper(it.latitude, it.longitude) }
        }
    }
}
