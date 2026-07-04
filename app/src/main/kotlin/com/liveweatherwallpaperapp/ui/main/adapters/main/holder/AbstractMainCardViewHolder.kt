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

package com.liveweatherwallpaperapp.ui.main.adapters.main.holder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import livewallpaperweather.domain.location.model.Location
import com.google.android.material.card.MaterialCardView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.dpToPx
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider

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
        val settings = SettingsManager.getInstance(context)
        val tileCardStyle = settings.tileCardStyle
        val tileCardAlpha = settings.tileCardAlpha
        val tileTextColor = settings.tileTextColor

        if (itemView is MaterialCardView) {
            (itemView as MaterialCardView).apply {
                radius = context.dpToPx(GLASS_CORNER_RADIUS_DP)
                cardElevation = 0f
                strokeWidth = context.dpToPx(GLASS_STROKE_WIDTH_DP).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.colorGlassCardStroke)
                val alpha255 = (tileCardAlpha / 100f * 255).toInt()
                when (tileCardStyle) {
                    "light" -> setCardBackgroundColor(
                        ColorUtils.setAlphaComponent(Color.WHITE, alpha255)
                    )
                    "dark" -> setCardBackgroundColor(
                        ColorUtils.setAlphaComponent(Color.BLACK, alpha255)
                    )
                    "auto" -> setCardBackgroundColor(
                        ColorUtils.setAlphaComponent(
                            ContextCompat.getColor(context, R.color.colorGlassCardBackground),
                            alpha255
                        )
                    )
                    else -> setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.colorGlassCardBackground)
                    )
                }
            }
        }
        if (tileTextColor != "auto") {
            val color = if (tileTextColor == "light") Color.WHITE else Color.BLACK
            applyTextColorToAllViews(itemView, color)
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
        private const val GLASS_CARD_SPACING_DP = 6f

        fun applyTextColorToAllViews(view: View, color: Int) {
            if (view is TextView) view.setTextColor(color)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyTextColorToAllViews(view.getChildAt(i), color)
                }
            }
        }
    }
}
