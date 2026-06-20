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

package org.breezyweather.ui.common.images

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the moon disc at the correct phase shape for the current date.
 *
 * Call [setPhaseAngle] with the value from [breezyweather.domain.weather.model.MoonPhase.angle]
 * (0 = new moon, 90 = first quarter, 180 = full moon, 270 = last quarter).
 */
class MoonDrawable : Drawable() {
    private val mPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val mClearXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    @ColorInt
    private val mCoreColor: Int = Color.rgb(171, 202, 247)
    private var mAlpha: Float = 1f

    private var mBounds: Rect
    private val mDiscRectF = RectF()
    private val mForeRectF = RectF()

    /** 0 = new moon, 90 = first quarter, 180 = full moon, 270 = last quarter. */
    private var mPhaseAngle: Float = 180f

    init {
        mBounds = bounds
        ensurePosition(mBounds)
    }

    fun setPhaseAngle(angleDegrees: Float) {
        mPhaseAngle = ((angleDegrees % 360f) + 360f) % 360f
    }

    private fun ensurePosition(bounds: Rect) {
        val boundSize = min(bounds.width(), bounds.height()).toFloat()
        val radius = ((sin(Math.PI / 4) * boundSize / 2 + boundSize / 2) / 2 - 2).toFloat()
        val cx = bounds.left + bounds.width() / 2f
        val cy = bounds.top + bounds.height() / 2f
        mDiscRectF.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    override fun onBoundsChange(bounds: Rect) {
        mBounds = bounds
        ensurePosition(bounds)
    }

    override fun draw(canvas: Canvas) {
        // The layer only exists so clear() can punch a transparent crescent out of an
        // isolated buffer without affecting the real canvas underneath — it does not
        // reliably carry setAlpha() through to the composited result on every canvas
        // (e.g. the wallpaper's hardware canvas), which is why the moon used to draw
        // fully opaque no matter what setAlpha() was called with. Bake alpha directly
        // into fill()'s colour instead, which always survives.
        val layerId = canvas.saveLayer(
            mBounds.left.toFloat(), mBounds.top.toFloat(),
            mBounds.right.toFloat(), mBounds.bottom.toFloat(),
            null
        )

        val a = mPhaseAngle
        when {
            // New moon — invisible.
            a < 0.5f || a > 359.5f -> Unit

            // Waxing crescent (0°–90°): thin sliver growing on the right.
            a < 89.5f -> {
                fill(canvas, mDiscRectF)
                clear(canvas, mDiscRectF, startAngle = 90f)
                lune(cos(Math.toRadians(a.toDouble())).toFloat())
                clear(canvas, mForeRectF, startAngle = 270f)
            }

            // First quarter (90°): right half lit.
            a <= 90.5f -> fill(canvas, mDiscRectF, startAngle = 270f)

            // Waxing gibbous (90°–180°): right half + growing left lune.
            a < 179.5f -> {
                fill(canvas, mDiscRectF, startAngle = 270f)
                lune(sin(Math.toRadians((a - 90.0))).toFloat())
                fill(canvas, mForeRectF, startAngle = 90f)
            }

            // Full moon (180°): fully lit disc.
            a <= 180.5f -> fill(canvas, mDiscRectF)

            // Waning gibbous (180°–270°): left half + shrinking right lune.
            a < 269.5f -> {
                fill(canvas, mDiscRectF, startAngle = 90f)
                lune(cos(Math.toRadians((a - 180.0))).toFloat())
                fill(canvas, mForeRectF, startAngle = 270f)
            }

            // Last quarter (270°): left half lit.
            a <= 270.5f -> fill(canvas, mDiscRectF, startAngle = 90f)

            // Waning crescent (270°–360°): thin sliver shrinking on the left.
            else -> {
                fill(canvas, mDiscRectF)
                clear(canvas, mDiscRectF, startAngle = 270f)
                lune(cos(Math.toRadians((360.0 - a))).toFloat())
                clear(canvas, mForeRectF, startAngle = 90f)
            }
        }

        mPaint.xfermode = null
        canvas.restoreToCount(layerId)
    }

    /** Fill entire disc or a 180° arc (half disc) with [mCoreColor] at [mAlpha]. */
    private fun fill(canvas: Canvas, rect: RectF, startAngle: Float? = null) {
        mPaint.color = mCoreColor
        mPaint.alpha = (mAlpha * 255).toInt()
        mPaint.xfermode = null
        if (startAngle == null) canvas.drawOval(rect, mPaint)
        else canvas.drawArc(rect, startAngle, 180f, true, mPaint)
    }

    /** Clear (punch transparent) a half of the given [rect]. */
    private fun clear(canvas: Canvas, rect: RectF, startAngle: Float) {
        mPaint.xfermode = mClearXfermode
        canvas.drawArc(rect, startAngle, 180f, true, mPaint)
        mPaint.xfermode = null
    }

    /**
     * Sets [mForeRectF] to a horizontally squished ellipse of the same height as [mDiscRectF],
     * with a semi-minor x-axis = [fraction] × disc-radius, for the terminator curve.
     */
    private fun lune(fraction: Float) {
        val cx = mDiscRectF.centerX()
        val hw = mDiscRectF.width() / 2f * fraction.coerceIn(0f, 1f)
        mForeRectF.set(cx - hw, mDiscRectF.top, cx + hw, mDiscRectF.bottom)
    }

    override fun setAlpha(alpha: Int) {
        mAlpha = alpha / 255f
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = mBounds.width()
    override fun getIntrinsicHeight(): Int = mBounds.height()
}
