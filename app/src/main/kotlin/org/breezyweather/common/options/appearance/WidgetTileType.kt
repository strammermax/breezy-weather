/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 */

package org.breezyweather.common.options.appearance

enum class WidgetTileType(val id: String) {
    DAY("day"),
    CLOCK_DAY_VERTICAL("clock_day_vertical"),
    CLOCK_DAY_HORIZONTAL("clock_day_horizontal"),
    WEEK("week"),
    ;

    companion object {
        fun fromId(id: String): WidgetTileType =
            entries.firstOrNull { it.id == id } ?: CLOCK_DAY_VERTICAL
    }
}
