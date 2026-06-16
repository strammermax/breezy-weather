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
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import breezyweather.domain.location.model.Location
import com.google.android.material.card.MaterialCardView
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.ui.main.fragments.HomeFragment
import org.breezyweather.ui.theme.resource.providers.ResourceProvider
import org.breezyweather.wallpaper.WallpaperSnapshot

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
                cardElevation = 0f
                strokeWidth = context.dpToPx(GLASS_STROKE_WIDTH_DP).toInt()
                strokeColor = ContextCompat.getColor(context, R.color.colorGlassCardStroke)
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorGlassCardBackground))
            }
            applyFrostBackground(itemView as MaterialCardView)
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

    /** Adds (or updates) a blurred wallpaper ImageView as the bottom layer of the card. */
    private fun applyFrostBackground(card: MaterialCardView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        // Prefer the live-wallpaper snapshot (sky + photo composite); fall back to the raw photo.
        val bitmap = WallpaperSnapshot.bitmap ?: HomeFragment.wallpaperBitmap ?: return
        val tag = "frost_bg"
        var bg = card.findViewWithTag<ImageView>(tag)
        if (bg == null) {
            bg = ImageView(card.context).apply {
                this.tag = tag
                scaleType = ImageView.ScaleType.MATRIX
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setRenderEffect(
                    RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                )
            }
            card.addView(bg, 0)
        }
        bg.setImageBitmap(bitmap)
        // Update after layout so getLocationOnScreen() returns real coordinates.
        bg.post { updateFrostMatrix(bg, bitmap) }
    }

    companion object {
        private const val GLASS_CORNER_RADIUS_DP = 22f
        private const val GLASS_STROKE_WIDTH_DP = 1f
        private const val GLASS_CARD_SPACING_DP = 6f

        /**
         * Recomputes the wallpaper crop matrix for [bg] based on its current screen position.
         *
         * [bitmap] is a snapshot captured by [WallpaperSnapshot] at 1/4 screen resolution, where
         * bitmap pixel (bx, by) maps to screen pixel (bx * captureScale⁻¹, by * captureScale⁻¹).
         * We scale back up and translate so that the card's top-left shows the wallpaper content
         * that is visually at that exact screen position — the same as CSS `backdrop-filter`.
         */
        fun updateFrostMatrix(bg: ImageView, bitmap: android.graphics.Bitmap) {
            val dm = bg.context.resources.displayMetrics
            val screenW = dm.widthPixels.toFloat()
            // How many screen pixels one snapshot pixel represents (e.g. 4 for a 1/4-res snap).
            val displayScale = screenW / bitmap.width
            val pos = IntArray(2)
            bg.getLocationOnScreen(pos)
            // Desired matrix: bitmap(bx,by) → view(bx*ds - pos[0], by*ds - pos[1])
            // so view(0,0) samples bitmap pixel (pos[0]/ds, pos[1]/ds), which corresponds
            // to wallpaper screen pixel (pos[0], pos[1]) — exactly where the card sits.
            //
            // Using setValues to avoid pre/post-concat confusion:
            //   [[ds,  0,  -pos[0]],
            //    [0,   ds, -pos[1]],
            //    [0,   0,  1      ]]
            val matrix = android.graphics.Matrix()
            matrix.setValues(floatArrayOf(
                displayScale, 0f, -pos[0].toFloat(),
                0f, displayScale, -pos[1].toFloat(),
                0f, 0f, 1f,
            ))
            bg.imageMatrix = matrix
        }
    }

}
