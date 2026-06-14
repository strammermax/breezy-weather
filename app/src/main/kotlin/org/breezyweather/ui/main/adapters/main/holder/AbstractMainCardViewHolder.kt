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

package org.breezyweather.ui.main.adapters.main.holder

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import breezyweather.domain.location.model.Location
import com.google.android.material.card.MaterialCardView
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

@SuppressLint("ObjectAnimatorBinding")
abstract class AbstractMainCardViewHolder(
    view: View,
) : AbstractMainViewHolder(view) {
    protected var mLocation: Location? = null

    @CallSuper
    open fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
        mLocation = location
        // ACT-013: render the home cards as floating "glass" tiles with a semi-transparent
        // background, a subtle light border and rounded corners, instead of an opaque
        // continuous block. See docs/ACT-013 - Glassmorphic kaartontwerp voor weergegevens.md.
        if (itemView is MaterialCardView) {
            (itemView as MaterialCardView).apply {
                radius = context.dpToPx(GLASS_CORNER_RADIUS_DP)
                cardElevation = context.dpToPx(GLASS_ELEVATION_DP)
                strokeWidth = context.dpToPx(GLASS_STROKE_WIDTH_DP).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.colorGlassCardStroke)
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorGlassCardBackground))
            }
        }
        val params = itemView.layoutParams as MarginLayoutParams
        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.small_margin)
        val verticalMargin = context.dpToPx(GLASS_CARD_SPACING_DP).toInt()
        params.setMargins(sideMargin, verticalMargin, sideMargin, verticalMargin)
        itemView.layoutParams = params
    }

    @CallSuper
    open fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
        selectedTab: String?,
        setSelectedTab: (String?) -> Unit,
    ) {
        onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBindView(
        context: Context,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        throw RuntimeException("Deprecated method.")
    }

    companion object {
        // ACT-013 glass surface style values (dag-variant; zie ACT-013 sectie 9).
        private const val GLASS_CORNER_RADIUS_DP = 22f
        private const val GLASS_STROKE_WIDTH_DP = 1f
        private const val GLASS_ELEVATION_DP = 2f
        private const val GLASS_CARD_SPACING_DP = 6f
    }
}
