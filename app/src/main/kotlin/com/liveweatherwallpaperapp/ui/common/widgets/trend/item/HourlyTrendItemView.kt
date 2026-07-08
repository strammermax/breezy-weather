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

package com.liveweatherwallpaperapp.ui.common.widgets.trend.item

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.withTranslation
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.dpToPx
import com.liveweatherwallpaperapp.common.extensions.fontScaleToApply
import com.liveweatherwallpaperapp.common.extensions.getTypefaceFromTextAppearance
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.common.widgets.trend.chart.AbsChartItemView
import kotlin.math.roundToInt

/**
 * Hourly trend item view.
 */
class HourlyTrendItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : AbsTrendItemView(context, attrs, defStyleAttr, defStyleRes) {
    private var mChartItem: AbsChartItemView? = null
    private val mHighlightPaint = Paint().apply {
        isAntiAlias = true
        color = Color.WHITE
        alpha = (255 * 0.12f).toInt()
    }
    private var mHighlighted = false
    private val mHighlightRadius: Float
    private val mHourTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val mPrecipitationTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    private val mWindForceTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    private val mUVTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private var mHourText: String? = null
    private var mPrecipitationText: String? = null
    private val mPrecipitationIconDrawable: Drawable? = ContextCompat.getDrawable(
        context,
        R.drawable.ic_water
    )?.mutate()
    private val mPrecipitationIconSize: Int
    private var mWindIconDrawable: Drawable? = null
    private var mWindForceText: String? = null
    private var mUVText: String? = null
    private val mWindIconSize: Int

    @IntDef(INVISIBLE, GONE)
    internal annotation class IconVisibility

    @IconVisibility
    private var mMissingIconVisibility: Int = GONE
    private var mIconDrawable: Drawable? = null

    @ColorInt
    private var mContentColor = 0

    private var mHourTextBaseLine = 0f
    private var mIconLeft = 0f
    private var mIconTop = 0f
    private var mTrendViewTop = 0f
    private var mPrecipitationRowTop = 0f
    private var mPrecipitationRowHeight = 0f
    private var mWindRowTop = 0f
    private var mWindRowHeight = 0f
    private var mUVRowTop = 0f
    private var mUVRowHeight = 0f
    private val mIconSize: Int
    override var chartTop: Int = 0
        private set
    override var chartBottom: Int = 0
        private set

    init {
        setWillNotDraw(false)
        mHourTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.title_text_size).toFloat()
        }
        mPrecipitationTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.subtitle_text_size).toFloat()
        }
        mWindForceTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.subtitle_text_size).toFloat()
        }
        mUVTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.subtitle_text_size).toFloat()
        }
        setTextColor(Color.BLACK)
        setPrecipitationProbabilityColor(Color.GRAY)
        setWindForceTextColor(Color.GRAY)
        mIconSize = getContext().dpToPx(ICON_SIZE_DIP.toFloat()).toInt()
        mPrecipitationIconSize = getContext().dpToPx(PRECIPITATION_ICON_SIZE_DIP.toFloat()).toInt()
        mWindIconSize = getContext().dpToPx(WIND_ICON_SIZE_DIP.toFloat()).toInt()
        mHighlightRadius = getContext().dpToPx(HIGHLIGHT_RADIUS_DIP.toFloat())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = context.resources
            .getDimensionPixelSize(R.dimen.trend_item_width)
            .times(context.fontScaleToApply)
            .roundToInt()
        val height = MeasureSpec.getSize(heightMeasureSpec)
        var y = 0f
        val textMargin = context.dpToPx(TEXT_MARGIN_DIP.toFloat())
        val iconMargin = context.dpToPx(ICON_MARGIN_DIP.toFloat())

        // hour text.
        val fontMetrics = mHourTextPaint.fontMetrics
        y += textMargin
        mHourTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += textMargin

        // hourly icon.
        if (mIconDrawable != null || mMissingIconVisibility == INVISIBLE) {
            y += iconMargin
            mIconLeft = (width - mIconSize) / 2f
            mIconTop = y
            y += mIconSize.toFloat()
            y += iconMargin
        }

        // precipitation probability row (droplet icon + percentage), below the icon.
        // Reserved unconditionally so the chart below stays aligned across all columns.
        val precipFontMetrics = mPrecipitationTextPaint.fontMetrics
        mPrecipitationRowHeight = maxOf(
            mPrecipitationIconSize.toFloat(),
            precipFontMetrics.bottom - precipFontMetrics.top
        )
        mPrecipitationRowTop = y
        y += mPrecipitationRowHeight
        y += textMargin

        // wind direction row (arrow icon), below the precipitation row.
        mWindRowHeight = mWindIconSize.toFloat()
        mWindRowTop = y
        y += mWindRowHeight
        y += textMargin

        // UV index row, below the wind row — extra margin so it reads as its own row instead
        // of crowding the wind row above it.
        y += textMargin
        val uvFontMetrics = mUVTextPaint.fontMetrics
        mUVRowHeight = uvFontMetrics.bottom - uvFontMetrics.top
        mUVRowTop = y
        y += mUVRowHeight
        y += textMargin

        // margin bottom.
        val marginBottom = context.dpToPx(TrendRecyclerView.ITEM_MARGIN_BOTTOM_DIP.toFloat())

        // chartItem item view.
        mChartItem?.measure(
            MeasureSpec.makeMeasureSpec(
                width,
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                (height - marginBottom - y).toInt(),
                MeasureSpec.EXACTLY
            )
        )

        mTrendViewTop = y
        chartTop = (mTrendViewTop + mChartItem!!.marginTop).toInt()
        chartBottom = (mTrendViewTop + mChartItem!!.measuredHeight - mChartItem!!.marginBottom).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        mChartItem?.layout(
            0,
            mTrendViewTop.toInt(),
            mChartItem!!.measuredWidth,
            mTrendViewTop.toInt() + mChartItem!!.measuredHeight
        )
    }

    override fun onDraw(canvas: Canvas) {
        // hour text.
        mHourText?.let {
            mHourTextPaint.color = mContentColor
            canvas.drawText(it, measuredWidth / 2f, mHourTextBaseLine, mHourTextPaint)
        }

        // day icon.
        mIconDrawable?.let {
            canvas.withTranslation(mIconLeft, mIconTop) {
                it.draw(canvas)
            }
        }

        // precipitation probability: droplet icon + percentage, centered as a group.
        mPrecipitationText?.let { text ->
            val icon = mPrecipitationIconDrawable
            val textWidth = mPrecipitationTextPaint.measureText(text)
            val iconWidth = icon?.let { mPrecipitationIconSize + mPrecipitationIconSize / 4f } ?: 0f
            val groupWidth = iconWidth + textWidth
            val groupLeft = (measuredWidth - groupWidth) / 2f
            val rowCenterY = mPrecipitationRowTop + mPrecipitationRowHeight / 2f

            icon?.let {
                it.setBounds(0, 0, mPrecipitationIconSize, mPrecipitationIconSize)
                canvas.withTranslation(groupLeft, rowCenterY - mPrecipitationIconSize / 2f) {
                    it.draw(canvas)
                }
            }

            val textFontMetrics = mPrecipitationTextPaint.fontMetrics
            canvas.drawText(
                text,
                groupLeft + iconWidth,
                rowCenterY - (textFontMetrics.top + textFontMetrics.bottom) / 2f,
                mPrecipitationTextPaint
            )
        }

        // wind direction arrow + Beaufort force, centered below the precipitation row.
        mWindIconDrawable?.let { icon ->
            val forceText = mWindForceText
            val textWidth = forceText?.let { mWindForceTextPaint.measureText(it) } ?: 0f
            val iconTextGap = forceText?.let { mWindIconSize / 4f } ?: 0f
            val groupWidth = mWindIconSize + iconTextGap + textWidth
            val groupLeft = (measuredWidth - groupWidth) / 2f
            val rowCenterY = mWindRowTop + mWindRowHeight / 2f

            icon.setBounds(0, 0, mWindIconSize, mWindIconSize)
            canvas.withTranslation(groupLeft, mWindRowTop) {
                icon.draw(canvas)
            }
            forceText?.let {
                val textFontMetrics = mWindForceTextPaint.fontMetrics
                canvas.drawText(
                    it,
                    groupLeft + mWindIconSize + iconTextGap,
                    rowCenterY - (textFontMetrics.top + textFontMetrics.bottom) / 2f,
                    mWindForceTextPaint
                )
            }
        }

        // UV index, centered below the wind row.
        mUVText?.let { text ->
            val textFontMetrics = mUVTextPaint.fontMetrics
            canvas.drawText(
                text,
                measuredWidth / 2f,
                mUVRowTop - textFontMetrics.top,
                mUVTextPaint
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Drawn after children (the chart) so the current-hour highlight overlays the whole
        // column uniformly, including the chart area below the icon/precipitation/wind rows —
        // drawing it in onDraw() instead left it hidden under the chart's own background fill.
        super.dispatchDraw(canvas)
        if (mHighlighted) {
            canvas.drawRoundRect(
                0f,
                0f,
                measuredWidth.toFloat(),
                measuredHeight.toFloat(),
                mHighlightRadius,
                mHighlightRadius,
                mHighlightPaint
            )
        }
    }

    fun setHourText(hourText: String?) {
        mHourText = hourText
        invalidate()
    }

    fun setTextColor(@ColorInt contentColor: Int) {
        mContentColor = contentColor
        invalidate()
    }

    fun setPrecipitationProbability(text: String?) {
        mPrecipitationText = text
        invalidate()
    }

    fun setPrecipitationProbabilityColor(@ColorInt color: Int) {
        mPrecipitationTextPaint.color = color
        mPrecipitationIconDrawable?.let { DrawableCompat.setTint(it, color) }
        invalidate()
    }

    fun setWindDirection(d: Drawable?, forceText: String? = null) {
        mWindIconDrawable = d
        mWindForceText = forceText
        invalidate()
    }

    fun setWindForceTextColor(@ColorInt color: Int) {
        mWindForceTextPaint.color = color
        invalidate()
    }

    fun setUVIndex(text: String?, @ColorInt color: Int) {
        mUVText = text
        mUVTextPaint.color = color
        invalidate()
    }

    fun setHighlighted(highlighted: Boolean) {
        mHighlighted = highlighted
        invalidate()
    }

    fun setIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mIconDrawable == null
        mIconDrawable = d
        mMissingIconVisibility = missingIconVisibility
        if (d != null) {
            d.setVisible(true, true)
            d.callback = this
            d.setBounds(0, 0, mIconSize, mIconSize)
        }
        if (nullDrawable != (d == null)) {
            requestLayout()
        } else {
            invalidate()
        }
    }

    override var chartItemView: AbsChartItemView?
        get() = mChartItem
        set(t) {
            mChartItem = t
            removeAllViews()
            addView(mChartItem)
            requestLayout()
        }

    companion object {
        private const val ICON_SIZE_DIP = 32
        private const val TEXT_MARGIN_DIP = 2
        private const val ICON_MARGIN_DIP = 8
        private const val HIGHLIGHT_RADIUS_DIP = 12
        private const val PRECIPITATION_ICON_SIZE_DIP = 14
        private const val WIND_ICON_SIZE_DIP = 16
    }
}
