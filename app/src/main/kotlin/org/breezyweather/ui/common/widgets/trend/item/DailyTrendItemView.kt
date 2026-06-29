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

package org.breezyweather.ui.common.widgets.trend.item

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
import org.breezyweather.R
import org.breezyweather.common.extensions.dpToPx
import org.breezyweather.common.extensions.fontScaleToApply
import org.breezyweather.common.extensions.getTypefaceFromTextAppearance
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.common.widgets.trend.chart.AbsChartItemView
import kotlin.math.roundToInt

/**
 * Daily trend item view.
 */
class DailyTrendItemView @JvmOverloads constructor(
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
    private val mWeekTextPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val mDateTextPaint = Paint().apply {
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
    private var mWeekText: String? = null
    private var mDateText: String? = null
    private var mPrecipitationTextDay: String? = null
    private var mPrecipitationTextNight: String? = null
    private var mPrecipitationIsSnowDay = false
    private var mPrecipitationIsSnowNight = false
    private val mPrecipitationIconDrawableRain: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_water)?.mutate()
    private val mPrecipitationIconDrawableSnow: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_snowflake)?.mutate()
    private val mPrecipitationIconSize: Int
    private var mWindIconDrawableDay: Drawable? = null
    private var mWindIconDrawableNight: Drawable? = null
    private var mWindForceTextDay: String? = null
    private var mWindForceTextNight: String? = null
    private val mWindIconSize: Int

    @IntDef(INVISIBLE, GONE)
    internal annotation class IconVisibility

    @IconVisibility
    private var mMissingDayIconVisibility: Int = GONE
    private var mDayIconDrawable: Drawable? = null

    @IconVisibility
    private var mMissingNightIconVisibility: Int = GONE
    private var mNightIconDrawable: Drawable? = null

    @ColorInt
    private var mContentColor = 0

    @ColorInt
    private var mSubTitleColor = 0
    private var mWeekTextBaseLine = 0f
    private var mDateTextBaseLine = 0f
    private var mDayIconLeft = 0f
    private var mDayIconTop = 0f
    private var mTrendViewTop = 0f
    private var mNightIconLeft = 0f
    private var mNightIconTop = 0f
    private var mPrecipitationRowTopDay = 0f
    private var mPrecipitationRowTopNight = 0f
    private var mPrecipitationRowHeight = 0f
    private var mWindRowTopDay = 0f
    private var mWindRowTopNight = 0f
    private var mWindRowHeight = 0f
    private val mIconSize: Int
    override var chartTop: Int = 0
        private set
    override var chartBottom: Int = 0
        private set

    init {
        setWillNotDraw(false)
        mWeekTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.title_text_size).toFloat()
        }
        mDateTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.content_text_size).toFloat()
        }
        mPrecipitationTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.subtitle_text_size).toFloat()
        }
        mWindForceTextPaint.apply {
            typeface = getContext().getTypefaceFromTextAppearance(R.style.content_text)
            textSize = getContext().resources.getDimensionPixelSize(R.dimen.subtitle_text_size).toFloat()
        }
        setTextColor(Color.BLACK, Color.GRAY)
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

        // week text.
        var fontMetrics = mWeekTextPaint.fontMetrics
        y += textMargin
        mWeekTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += textMargin

        // date text.
        fontMetrics = mDateTextPaint.fontMetrics
        y += textMargin
        mDateTextBaseLine = y - fontMetrics.top
        y += fontMetrics.bottom - fontMetrics.top
        y += textMargin

        // day icon.
        if (mDayIconDrawable != null || mMissingDayIconVisibility == INVISIBLE) {
            y += iconMargin
            mDayIconLeft = (width - mIconSize) / 2f
            mDayIconTop = y
            y += mIconSize.toFloat()
            y += iconMargin
        }

        // precipitation probability row (droplet/snowflake icon + percentage), below the day icon.
        // Reserved unconditionally so the chart below stays aligned across all columns,
        // whether or not a given day actually has a precipitation probability to show.
        val precipFontMetrics = mPrecipitationTextPaint.fontMetrics
        mPrecipitationRowHeight = maxOf(
            mPrecipitationIconSize.toFloat(),
            precipFontMetrics.bottom - precipFontMetrics.top
        )
        mPrecipitationRowTopDay = y
        y += mPrecipitationRowHeight
        y += textMargin

        // wind direction row (arrow icon + force), below the precipitation row.
        // Also reserved unconditionally for alignment, like the precipitation row.
        mWindRowHeight = mWindIconSize.toFloat()
        mWindRowTopDay = y

        // bottomBlockHeight accumulates ONLY the rows reserved at the bottom (margin, night
        // icon, night precipitation/wind rows) so positions below can be computed relative to
        // the bottom of the view — kept separate from the top block's height (y) so the two
        // don't get summed into each other when computing "height - x" positions.
        var bottomBlockHeight = 0f

        // margin bottom.
        val marginBottom = context.dpToPx(TrendRecyclerView.ITEM_MARGIN_BOTTOM_DIP.toFloat())
        bottomBlockHeight += marginBottom

        // night icon.
        if (mNightIconDrawable != null || mMissingNightIconVisibility == INVISIBLE) {
            mNightIconLeft = (width - mIconSize) / 2f
            mNightIconTop = height - marginBottom - iconMargin - mIconSize
            bottomBlockHeight += mIconSize + 2 * iconMargin
        }

        // wind direction row (night) and precipitation probability row (night), mirroring the
        // day rows above the chart but in reverse order, sitting directly above the night icon.
        bottomBlockHeight += mPrecipitationRowHeight + textMargin
        mPrecipitationRowTopNight = height - bottomBlockHeight
        bottomBlockHeight += mWindRowHeight + textMargin
        mWindRowTopNight = height - bottomBlockHeight

        y += mWindRowHeight
        y += textMargin
        val consumedHeight: Float = y + bottomBlockHeight

        // chartItem item view.
        mChartItem?.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height - consumedHeight).toInt(), MeasureSpec.EXACTLY)
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
        // week text.
        mWeekText?.let {
            mWeekTextPaint.color = mContentColor
            canvas.drawText(it, measuredWidth / 2f, mWeekTextBaseLine, mWeekTextPaint)
        }

        // date text.
        mDateText?.let {
            mDateTextPaint.color = mSubTitleColor
            canvas.drawText(it, measuredWidth / 2f, mDateTextBaseLine, mDateTextPaint)
        }
        var restoreCount: Int

        // day icon.
        mDayIconDrawable?.let {
            restoreCount = canvas.save()
            canvas.translate(mDayIconLeft, mDayIconTop)
            it.draw(canvas)
            canvas.restoreToCount(restoreCount)
        }

        // precipitation probability: droplet/snowflake icon + percentage, centered as a group.
        drawPrecipitationRow(canvas, mPrecipitationTextDay, mPrecipitationIsSnowDay, mPrecipitationRowTopDay)
        drawPrecipitationRow(canvas, mPrecipitationTextNight, mPrecipitationIsSnowNight, mPrecipitationRowTopNight)

        // wind direction arrow + force, centered below the precipitation row (day) or
        // above the night icon (night).
        drawWindRow(canvas, mWindIconDrawableDay, mWindForceTextDay, mWindRowTopDay)
        drawWindRow(canvas, mWindIconDrawableNight, mWindForceTextNight, mWindRowTopNight)

        // night icon.
        mNightIconDrawable?.let {
            restoreCount = canvas.save()
            canvas.translate(mNightIconLeft, mNightIconTop)
            it.draw(canvas)
            canvas.restoreToCount(restoreCount)
        }
    }

    /** Droplet (or snowflake, when [isSnow]) icon + percentage, centered as a group. */
    private fun drawPrecipitationRow(canvas: Canvas, text: String?, isSnow: Boolean, rowTop: Float) {
        text?.let {
            val icon = if (isSnow) mPrecipitationIconDrawableSnow else mPrecipitationIconDrawableRain
            val textWidth = mPrecipitationTextPaint.measureText(it)
            val iconWidth = icon?.let { mPrecipitationIconSize + mPrecipitationIconSize / 4f } ?: 0f
            val groupWidth = iconWidth + textWidth
            val groupLeft = (measuredWidth - groupWidth) / 2f
            val rowCenterY = rowTop + mPrecipitationRowHeight / 2f

            icon?.let { drawable ->
                val restoreCount = canvas.save()
                drawable.setBounds(0, 0, mPrecipitationIconSize, mPrecipitationIconSize)
                canvas.translate(groupLeft, rowCenterY - mPrecipitationIconSize / 2f)
                drawable.draw(canvas)
                canvas.restoreToCount(restoreCount)
            }

            val textFontMetrics = mPrecipitationTextPaint.fontMetrics
            canvas.drawText(
                it,
                groupLeft + iconWidth,
                rowCenterY - (textFontMetrics.top + textFontMetrics.bottom) / 2f,
                mPrecipitationTextPaint
            )
        }
    }

    /** Wind direction arrow + Beaufort force number, centered as a group. */
    private fun drawWindRow(canvas: Canvas, icon: Drawable?, forceText: String?, rowTop: Float) {
        icon ?: return
        val textWidth = forceText?.let { mWindForceTextPaint.measureText(it) } ?: 0f
        val iconTextGap = forceText?.let { mWindIconSize / 4f } ?: 0f
        val groupWidth = mWindIconSize + iconTextGap + textWidth
        val groupLeft = (measuredWidth - groupWidth) / 2f
        val rowCenterY = rowTop + mWindRowHeight / 2f

        val restoreCount = canvas.save()
        icon.setBounds(0, 0, mWindIconSize, mWindIconSize)
        canvas.translate(groupLeft, rowTop)
        icon.draw(canvas)
        canvas.restoreToCount(restoreCount)

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

    override fun dispatchDraw(canvas: Canvas) {
        // Drawn after children (the chart) so the current-day highlight overlays the whole
        // column uniformly, including the chart area — drawing it in onDraw() instead left it
        // hidden under the chart's own background fill.
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

    fun setWeekText(weekText: String?) {
        mWeekText = weekText
        invalidate()
    }

    fun setDateText(dateText: String?) {
        mDateText = dateText
        invalidate()
    }

    fun setPrecipitationProbabilityDay(text: String?, isSnow: Boolean = false) {
        mPrecipitationTextDay = text
        mPrecipitationIsSnowDay = isSnow
        invalidate()
    }

    fun setPrecipitationProbabilityNight(text: String?, isSnow: Boolean = false) {
        mPrecipitationTextNight = text
        mPrecipitationIsSnowNight = isSnow
        invalidate()
    }

    fun setPrecipitationProbabilityColor(@ColorInt color: Int) {
        mPrecipitationTextPaint.color = color
        mPrecipitationIconDrawableRain?.let { DrawableCompat.setTint(it, color) }
        mPrecipitationIconDrawableSnow?.let { DrawableCompat.setTint(it, color) }
        invalidate()
    }

    fun setWindDirectionDay(d: Drawable?, forceText: String? = null) {
        mWindIconDrawableDay = d
        mWindForceTextDay = forceText
        invalidate()
    }

    fun setWindDirectionNight(d: Drawable?, forceText: String? = null) {
        mWindIconDrawableNight = d
        mWindForceTextNight = forceText
        invalidate()
    }

    fun setWindForceTextColor(@ColorInt color: Int) {
        mWindForceTextPaint.color = color
        invalidate()
    }

    fun setHighlighted(highlighted: Boolean) {
        mHighlighted = highlighted
        invalidate()
    }

    fun setTextColor(@ColorInt contentColor: Int, @ColorInt subTitleColor: Int) {
        mContentColor = contentColor
        mSubTitleColor = subTitleColor
        invalidate()
    }

    fun setDayIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mDayIconDrawable == null
        mDayIconDrawable = d
        mMissingDayIconVisibility = missingIconVisibility
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

    fun setNightIconDrawable(d: Drawable?, @IconVisibility missingIconVisibility: Int) {
        val nullDrawable = mNightIconDrawable == null
        mNightIconDrawable = d
        mMissingNightIconVisibility = missingIconVisibility
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
