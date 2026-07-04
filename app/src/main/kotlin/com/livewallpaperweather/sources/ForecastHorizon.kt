/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package com.livewallpaperweather.sources

import java.util.Date

internal const val MINIMUM_FORECAST_DAYS = 5

internal fun <T> List<T>?.forecastDaysFrom(
    startDate: Date,
    dateOf: (T) -> Date,
): Int = this.orEmpty().count { !dateOf(it).before(startDate) }

internal fun <T> List<T>?.hasMinimumForecastDays(
    startDate: Date,
    dateOf: (T) -> Date,
): Boolean = forecastDaysFrom(startDate, dateOf) >= MINIMUM_FORECAST_DAYS
