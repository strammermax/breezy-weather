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

package com.livewallpaperweather.common.source

import android.content.Context
import livewallpaperweather.domain.source.SourceFeature
import com.livewallpaperweather.R
import com.livewallpaperweather.domain.source.resourceName
import com.livewallpaperweather.sources.SourceManager
import com.livewallpaperweather.ui.main.utils.RefreshErrorType

class RefreshError(
    val error: RefreshErrorType,
    val source: String? = null,
    val feature: SourceFeature? = null,
) {
    fun getSourceWithOptionalFeature(context: Context, sourceManager: SourceManager): String? {
        return if (!source.isNullOrEmpty()) {
            val sourceName = sourceManager.getSource(source)?.name ?: source
            if (feature != null) {
                context.getString(R.string.parenthesis, sourceName, context.getString(feature.resourceName))
            } else {
                sourceName
            }
        } else {
            null
        }
    }

    fun getMessage(context: Context, sourceManager: SourceManager): String {
        return if (!source.isNullOrEmpty()) {
            "${getSourceWithOptionalFeature(context, sourceManager)}${context.getString(
                R.string.colon_separator
            )}${context.getString(error.shortMessage)}"
        } else {
            context.getString(error.shortMessage)
        }
    }
}
