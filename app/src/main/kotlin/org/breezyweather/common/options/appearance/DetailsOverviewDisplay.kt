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

package org.breezyweather.common.options.appearance

import android.content.Context
import androidx.annotation.StringRes
import org.breezyweather.R
import org.breezyweather.common.options.BaseEnum

enum class DetailsOverviewDisplay(
    override val id: String,
    @StringRes val nameId: Int,
) : BaseEnum {

    TAG_PRECIPITATION_PROBABILITY("precipitation_probability", R.string.precipitation_probability),
    TAG_PRECIPITATION("precipitation", R.string.precipitation),
    TAG_HUMIDITY("humidity", R.string.humidity),
    TAG_UV_INDEX("uv_index", R.string.uv_index),
    TAG_CLOUD_COVER("cloud_cover", R.string.cloud_cover),
    TAG_VISIBILITY("visibility", R.string.visibility),
    TAG_WIND("wind", R.string.wind),
    TAG_WIND_DIRECTION("wind_direction", R.string.wind_direction),
    TAG_FEELS_LIKE("feels_like", R.string.temperature_feels_like),
    TAG_DEW_POINT("dew_point", R.string.dew_point),
    TAG_PRESSURE("pressure", R.string.pressure),
    TAG_OZONE("ozone", R.string.air_quality_o3_voice),
    TAG_SUN("sun", R.string.ephemeris_sun),
    TAG_MOON("moon", R.string.ephemeris_moon),
    TAG_AIR_QUALITY("air_quality", R.string.air_quality),
    TAG_POLLEN("pollen", R.string.pollen),
    ;

    companion object {

        fun toDetailsOverviewDisplayList(
            value: String?,
        ) = if (value.isNullOrEmpty()) {
            entries.toMutableList()
        } else {
            try {
                value.split("&").toTypedArray().mapNotNull { tagId ->
                    entries.firstOrNull { it.id == tagId }
                }
            } catch (e: Exception) {
                entries.toMutableList()
            }
        }

        fun toValue(list: List<DetailsOverviewDisplay>): String {
            return list.joinToString("&") { item ->
                item.id
            }
        }
    }

    override val valueArrayId = 0
    override val nameArrayId = 0

    override fun getName(context: Context) = context.getString(nameId)
}
