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

package com.liveweatherwallpaperapp.ui.common.widgets.trend.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.Size
import androidx.core.graphics.ColorUtils
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.dpToPx
import com.liveweatherwallpaperapp.common.extensions.getTypefaceFromTextAppearance
import com.liveweatherwallpaperapp.ui.common.widgets.DayNightShaderWrapper

/**
 * Polyline and histogram view.
 */
class PolylineAndHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbsChartItemView(context, attrs, defStyleAttr) {
    private val mPaint = Paint().apply {
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        isFilterBitmap = true
    }
    private val mPath = Path()
    private val mShaderWrapper: DayNightShaderWrapper

    @Size(3)
    private var mHighPolylineValues: Array<Float?>? = arrayOfNulls(3)

    @Size(3)
    private var mLowPolylineValues: Array<Float?>? = arrayOfNulls(3)
    private var mHighPolylineValueStr: String? = null
    private var mLowPolylineValueStr: String? = null
    private var mHighestPolylineValue: Float? = null
    private var mLowestPolylineValue: Float? = null
    private var mHistogramValue: Float? = null
    private var mHistogramValueStr: String? = null
    private var mHighestHistogramValue: Float? = null
    private var mLowestHistogramValue: Float? = null

    @Size(3)
    private var mPrecipPolylineValues: Array<Float?>? = null
    private var mPrecipPolylineValueStr: String? = null
    private var mHighestPrecipValue: Float? = null
    private var mLowestPrecipValue: Float? = null
    private var mPrecipRegionTopFraction: Float = PRECIP_REGION_TOP_FRACTION
    private val mHighPolylineY = IntArray(3)
    private val mLowPolylineY = IntArray(3)
    private val mPrecipPolylineY = IntArray(3)
    private var mHistogramY = 0
    override val marginTop: Int
    override val marginBottom: Int
    private val mPolylineWidth: Int
    private val mPolylineTextSize: Int
    private val mHistogramWidth: Int
    private val mHistogramTextSize: Int
    private val mChartLineWidth: Int
    private val mTextMargin: Int
    private val mDotRadius: Float
    private val mLineColors: IntArray = intArrayOf(Color.BLACK, Color.DKGRAY, Color.LTGRAY)
    private val mShadowColors: IntArray = intArrayOf(Color.BLACK, Color.WHITE)
    private var mHighTextColor = 0
    private var mLowTextColor = 0
    private var mTextShadowColor = 0
    private var mHistogramTextColor = 0
    private var mHistogramColor = 0
    private var mHistogramAlpha = 0f
    private var mPrecipColor = 0
    private var mPrecipTextColor = 0

    init {
        setTextColors(Color.BLACK, Color.DKGRAY, Color.GRAY)
        setHistogramColor(Color.WHITE)
        setHistogramAlpha(0.33f)
        marginTop = getContext().dpToPx(MARGIN_TOP_DIP).toInt()
        marginBottom = getContext().dpToPx(MARGIN_BOTTOM_DIP).toInt()
        mPolylineTextSize = getContext().dpToPx(POLYLINE_TEXT_SIZE_DIP).toInt()
        mHistogramTextSize = getContext().dpToPx(HISTOGRAM_TEXT_SIZE_DIP).toInt()
        mPolylineWidth = getContext().dpToPx(POLYLINE_SIZE_DIP).toInt()
        mHistogramWidth = getContext().dpToPx(HISTOGRAM_WIDTH_DIP).toInt()
        mChartLineWidth = getContext().dpToPx(CHART_LINE_SIZE_DIP).toInt()
        mTextMargin = getContext().dpToPx(TEXT_MARGIN_DIP).toInt()
        mDotRadius = getContext().dpToPx(DOT_RADIUS_DIP)
        mPaint.typeface = getContext().getTypefaceFromTextAppearance(R.style.title_text)
        mShaderWrapper = DayNightShaderWrapper(measuredWidth, measuredHeight)
        setShadowColors(Color.BLACK, Color.GRAY, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ensureShader(mShaderWrapper.isLightTheme)
        computeCoordinates()
        drawTimeLine(canvas)
        if (mHistogramValue != null &&
            (mHistogramValue != 0f || (mHighestPolylineValue == null && mLowestPolylineValue == null)) &&
            mHistogramValueStr != null &&
            mHighestHistogramValue != null &&
            mLowestHistogramValue != null
        ) {
            drawHistogram(canvas)
        }
        if (mHighestPolylineValue != null && mLowestPolylineValue != null) {
            if (mHighPolylineValues != null && mHighPolylineValueStr != null) {
                drawHighPolyLine(canvas)
            }
            if (mLowPolylineValues != null && mLowPolylineValueStr != null) {
                drawLowPolyline(canvas)
            }
        }
        if (mPrecipPolylineValues != null &&
            mPrecipPolylineValueStr != null &&
            mHighestPrecipValue != null &&
            mLowestPrecipValue != null
        ) {
            drawPrecipPolyline(canvas)
        }
    }

    private fun drawTimeLine(canvas: Canvas) {
        mPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = mChartLineWidth.toFloat()
            color = mLineColors[2]
        }
        canvas.drawLine(
            measuredWidth / 2f,
            marginTop.toFloat(),
            measuredWidth / 2f,
            (measuredHeight - marginBottom).toFloat(),
            mPaint
        )
    }

    private fun drawHighPolyLine(canvas: Canvas) {
        assert(mHighPolylineValues != null)
        assert(mHighPolylineValueStr != null)
        if (mHighPolylineValues!![0] != null && mHighPolylineValues!![2] != null) {
            // shadow.
            mPaint.apply {
                color = Color.BLACK
                shader = mShaderWrapper.shader
                style = Paint.Style.FILL
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mHighPolylineY[0].toFloat())
                lineTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), mHighPolylineY[1].toFloat())
                lineTo(getRTLCompactX(measuredWidth.toFloat()), mHighPolylineY[2].toFloat())
                lineTo(getRTLCompactX(measuredWidth.toFloat()), (measuredHeight - marginBottom).toFloat())
                lineTo(getRTLCompactX(0f), (measuredHeight - marginBottom).toFloat())
                close()
            }
            canvas.drawPath(mPath, mPaint)

            // line.
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mPolylineWidth.toFloat()
                color = mLineColors[0]
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mHighPolylineY[0].toFloat())
                smoothLineTo(
                    getRTLCompactX(0f),
                    mHighPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mHighPolylineY[1].toFloat()
                )
                smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mHighPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mHighPolylineY[2].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        } else if (mHighPolylineValues!![0] == null) {
            // shadow.
            mPaint.apply {
                color = Color.BLACK
                shader = mShaderWrapper.shader
                style = Paint.Style.FILL
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), mHighPolylineY[1].toFloat())
                lineTo(getRTLCompactX(measuredWidth.toFloat()), mHighPolylineY[2].toFloat())
                lineTo(getRTLCompactX(measuredWidth.toFloat()), (measuredHeight - marginBottom).toFloat())
                lineTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), (measuredHeight - marginBottom).toFloat())
                close()
            }
            canvas.drawPath(mPath, mPaint)

            // line.
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mPolylineWidth.toFloat()
                color = mLineColors[0]
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), mHighPolylineY[1].toFloat())
                smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mHighPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mHighPolylineY[2].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        } else {
            // shadow.
            mPaint.apply {
                color = Color.BLACK
                shader = mShaderWrapper.shader
                style = Paint.Style.FILL
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mHighPolylineY[0].toFloat())
                lineTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), mHighPolylineY[1].toFloat())
                lineTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), (measuredHeight - marginBottom).toFloat())
                lineTo(getRTLCompactX(0f), (measuredHeight - marginBottom).toFloat())
                close()
            }
            canvas.drawPath(mPath, mPaint)

            // line.
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mPolylineWidth.toFloat()
                color = mLineColors[0]
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mHighPolylineY[0].toFloat())
                smoothLineTo(
                    getRTLCompactX(0f),
                    mHighPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mHighPolylineY[1].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        }

        // dot marker for this day's value.
        drawDot(canvas, (measuredWidth / 2.0).toFloat(), mHighPolylineY[1].toFloat(), mLineColors[0])

        // text.
        mPaint.apply {
            color = mHighTextColor
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = mPolylineTextSize.toFloat()
            setShadowLayer(2f, 0f, 1f, mTextShadowColor)
        }
        canvas.drawText(
            mHighPolylineValueStr ?: "",
            getRTLCompactX((measuredWidth / 2.0).toFloat()),
            if (mHighPolylineY[1] > mLowPolylineY[1]) {
                mHighPolylineY[1] - mPaint.fontMetrics.top + mTextMargin
            } else {
                mHighPolylineY[1] - mPaint.fontMetrics.bottom - mTextMargin
            },
            mPaint
        )
        mPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    private fun drawLowPolyline(canvas: Canvas) {
        assert(mLowPolylineValues != null)
        assert(mLowPolylineValueStr != null)
        if (mLowPolylineValues!![0] != null && mLowPolylineValues!![2] != null) {
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                mPaint.strokeWidth = mPolylineWidth.toFloat()
                mPaint.color = mLineColors[1]
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mLowPolylineY[0].toFloat())
                smoothLineTo(
                    getRTLCompactX(0f),
                    mLowPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mLowPolylineY[1].toFloat()
                )
                smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mLowPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mLowPolylineY[2].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        } else if (mLowPolylineValues!![0] == null) {
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mPolylineWidth.toFloat()
                color = mLineColors[1]
            }
            mPath.apply {
                reset()
                moveTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mLowPolylineY[1].toFloat()
                )
                smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mLowPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mLowPolylineY[2].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        } else {
            mPaint.apply {
                shader = null
                style = Paint.Style.STROKE
                strokeWidth = mPolylineWidth.toFloat()
                color = mLineColors[1]
            }
            mPath.apply {
                reset()
                moveTo(getRTLCompactX(0f), mLowPolylineY[0].toFloat())
                smoothLineTo(
                    getRTLCompactX(0f),
                    mLowPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mLowPolylineY[1].toFloat()
                )
            }
            canvas.drawPath(mPath, mPaint)
        }

        // dot marker for this day's value.
        drawDot(canvas, (measuredWidth / 2.0).toFloat(), mLowPolylineY[1].toFloat(), mLineColors[1])

        // text.
        mPaint.apply {
            color = mLowTextColor
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = mPolylineTextSize.toFloat()
            setShadowLayer(2f, 0f, 1f, mTextShadowColor)
        }
        canvas.drawText(
            mLowPolylineValueStr ?: "",
            getRTLCompactX((measuredWidth / 2.0).toFloat()),
            if (mHighPolylineY[1] > mLowPolylineY[1]) {
                mLowPolylineY[1] - mPaint.fontMetrics.bottom - mTextMargin
            } else {
                mLowPolylineY[1] - mPaint.fontMetrics.top + mTextMargin
            },
            mPaint
        )
        mPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    private fun drawHistogram(canvas: Canvas) {
        assert(mHistogramValueStr != null)
        mPaint.apply {
            color = mHistogramColor
            alpha = (255 * mHistogramAlpha).toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(
                (measuredWidth / 2.0 - mHistogramWidth).toFloat(),
                mHistogramY.toFloat(),
                (measuredWidth / 2.0 + mHistogramWidth).toFloat(),
                (measuredHeight - marginBottom).toFloat()
            ),
            mHistogramWidth.toFloat(),
            mHistogramWidth.toFloat(),
            mPaint
        )
        mPaint.apply {
            color = mHistogramTextColor
            alpha = 255
            textAlign = Paint.Align.CENTER
            textSize = mHistogramTextSize.toFloat()
        }
        canvas.drawText(
            mHistogramValueStr ?: "",
            (measuredWidth / 2.0).toFloat(),
            (
                (measuredHeight - marginBottom - mPaint.fontMetrics.top) + 2.0 * mTextMargin + mPolylineTextSize
                ).toFloat(),
            mPaint
        )
        mPaint.alpha = 255
    }

    /**
     * Daily total precipitation as a connected line (with a dot + "x.xx mm" label per day),
     * mirroring [drawLowPolyline] but scaled into its own value range and confined to the
     * lower [PRECIP_REGION_TOP_FRACTION] of the chart so it doesn't collide with the
     * temperature lines above it.
     */
    private fun drawPrecipPolyline(canvas: Canvas) {
        val values = mPrecipPolylineValues ?: return
        mPaint.apply {
            shader = null
            style = Paint.Style.STROKE
            strokeWidth = mPolylineWidth.toFloat()
            color = mPrecipColor
        }
        mPath.reset()
        when {
            values[0] != null && values[2] != null -> {
                mPath.moveTo(getRTLCompactX(0f), mPrecipPolylineY[0].toFloat())
                mPath.smoothLineTo(
                    getRTLCompactX(0f),
                    mPrecipPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mPrecipPolylineY[1].toFloat()
                )
                mPath.smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mPrecipPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mPrecipPolylineY[2].toFloat()
                )
                canvas.drawPath(mPath, mPaint)
            }
            values[0] == null && values[2] != null -> {
                mPath.moveTo(getRTLCompactX((measuredWidth / 2.0).toFloat()), mPrecipPolylineY[1].toFloat())
                mPath.smoothLineTo(
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mPrecipPolylineY[1].toFloat(),
                    getRTLCompactX(measuredWidth.toFloat()),
                    mPrecipPolylineY[2].toFloat()
                )
                canvas.drawPath(mPath, mPaint)
            }
            values[0] != null && values[2] == null -> {
                mPath.moveTo(getRTLCompactX(0f), mPrecipPolylineY[0].toFloat())
                mPath.smoothLineTo(
                    getRTLCompactX(0f),
                    mPrecipPolylineY[0].toFloat(),
                    getRTLCompactX((measuredWidth / 2.0).toFloat()),
                    mPrecipPolylineY[1].toFloat()
                )
                canvas.drawPath(mPath, mPaint)
            }
            else -> Unit // isolated point: no neighbor to connect to, just the dot + label below.
        }

        drawDot(canvas, (measuredWidth / 2.0).toFloat(), mPrecipPolylineY[1].toFloat(), mPrecipColor)

        mPaint.apply {
            color = mPrecipTextColor
            style = Paint.Style.FILL
            textAlign = Paint.Align.CENTER
            textSize = mHistogramTextSize.toFloat()
            setShadowLayer(2f, 0f, 1f, mTextShadowColor)
        }
        canvas.drawText(
            mPrecipPolylineValueStr ?: "",
            getRTLCompactX((measuredWidth / 2.0).toFloat()),
            mPrecipPolylineY[1] - mPaint.fontMetrics.top + mTextMargin,
            mPaint
        )
        mPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    }

    // control.
    fun setData(
        @Size(3) highPolylineValues: Array<Float?>?,
        @Size(3) lowPolylineValues: Array<Float?>?,
        highPolylineValueStr: String?,
        lowPolylineValueStr: String?,
        highestPolylineValue: Float?,
        lowestPolylineValue: Float?,
        histogramValue: Float?,
        histogramValueStr: String?,
        highestHistogramValue: Float?,
        lowestHistogramValue: Float?,
    ) {
        mHighPolylineValues = highPolylineValues
        mLowPolylineValues = lowPolylineValues
        mHighPolylineValueStr = highPolylineValueStr
        mLowPolylineValueStr = lowPolylineValueStr
        mHighestPolylineValue = highestPolylineValue
        mLowestPolylineValue = lowestPolylineValue
        mHistogramValue = histogramValue
        mHistogramValueStr = histogramValueStr
        mHighestHistogramValue = highestHistogramValue
        mLowestHistogramValue = lowestHistogramValue
        invalidate()
    }

    fun setLineColors(
        @ColorInt colorHigh: Int,
        @ColorInt colorLow: Int,
        @ColorInt colorSubLine: Int,
    ) {
        mLineColors[0] = colorHigh
        mLineColors[1] = colorLow
        mLineColors[2] = colorSubLine
        invalidate()
    }

    fun setShadowColors(
        @ColorInt colorHigh: Int,
        @ColorInt colorLow: Int,
        lightTheme: Boolean,
    ) {
        mShadowColors[0] = if (lightTheme) {
            ColorUtils.setAlphaComponent(colorHigh, (255 * SHADOW_ALPHA_FACTOR_LIGHT).toInt())
        } else {
            ColorUtils.setAlphaComponent(colorLow, (255 * SHADOW_ALPHA_FACTOR_DARK).toInt())
        }
        mShadowColors[1] = Color.TRANSPARENT
        ensureShader(lightTheme)
        invalidate()
    }

    fun setTextColors(
        @ColorInt highTextColor: Int,
        @ColorInt lowTextColor: Int,
        @ColorInt histogramTextColor: Int,
    ) {
        mHighTextColor = highTextColor
        mLowTextColor = lowTextColor
        mTextShadowColor = Color.argb((255 * 0.2).toInt(), 0, 0, 0)
        mHistogramTextColor = histogramTextColor
        invalidate()
    }

    fun setHistogramAlpha(
        @FloatRange(from = 0.0, to = 1.0) histogramAlpha: Float,
    ) {
        mHistogramAlpha = histogramAlpha
        invalidate()
    }

    /** Color of the histogram bar — kept distinct from the polyline colors so it stays
     * visible regardless of how close it sits to the (semi-transparent) polyline shadow fill. */
    fun setHistogramColor(@ColorInt histogramColor: Int) {
        mHistogramColor = histogramColor
        invalidate()
    }

    /**
     * Daily total precipitation, drawn as its own connected line (see [drawPrecipPolyline])
     * rather than the single-value [setData] histogram — a day-to-day amount reads better as
     * a trend line than as one isolated bar per visible day.
     */
    fun setPrecipPolylineData(
        @Size(3) values: Array<Float?>?,
        valueStr: String?,
        highestValue: Float?,
        lowestValue: Float?,
        @FloatRange(from = 0.0, to = 1.0) regionTopFraction: Float = PRECIP_REGION_TOP_FRACTION,
    ) {
        mPrecipPolylineValues = values
        mPrecipPolylineValueStr = valueStr
        mHighestPrecipValue = highestValue
        mLowestPrecipValue = lowestValue
        mPrecipRegionTopFraction = regionTopFraction
        invalidate()
    }

    fun setPrecipColors(@ColorInt lineColor: Int, @ColorInt textColor: Int) {
        mPrecipColor = lineColor
        mPrecipTextColor = textColor
        invalidate()
    }

    private fun ensureShader(lightTheme: Boolean) {
        if (mShaderWrapper.isDifferent(measuredWidth, measuredHeight, lightTheme, mShadowColors)) {
            mShaderWrapper.setShader(
                LinearGradient(
                    0f,
                    marginTop.toFloat(),
                    0f,
                    (measuredHeight - marginBottom).toFloat(),
                    mShadowColors[0],
                    mShadowColors[1],
                    Shader.TileMode.CLAMP
                ),
                measuredWidth,
                measuredHeight,
                lightTheme,
                mShadowColors
            )
        }
    }

    private fun computeCoordinates() {
        val canvasHeight = (measuredHeight - marginTop - marginBottom).toFloat()
        if (mHighestPolylineValue != null && mLowestPolylineValue != null) {
            mHighPolylineValues?.let {
                for (i in it.indices) {
                    if (it[i] == null) {
                        mHighPolylineY[i] = 0
                    } else {
                        mHighPolylineY[i] = computeSingleCoordinate(
                            canvasHeight,
                            it[i]!!,
                            mHighestPolylineValue!!,
                            mLowestPolylineValue!!
                        )
                    }
                }
            }
            mLowPolylineValues?.let {
                for (i in it.indices) {
                    if (it[i] == null) {
                        mLowPolylineY[i] = 0
                    } else {
                        mLowPolylineY[i] = computeSingleCoordinate(
                            canvasHeight,
                            it[i]!!,
                            mHighestPolylineValue!!,
                            mLowestPolylineValue!!
                        )
                    }
                }
            }
        }
        if (mHistogramValue != null && mHighestHistogramValue != null && mLowestHistogramValue != null) {
            mHistogramY = computeSingleCoordinate(
                canvasHeight,
                mHistogramValue!!,
                mHighestHistogramValue!!,
                mLowestHistogramValue!!
            )
        }
        if (mHighestPrecipValue != null && mLowestPrecipValue != null) {
            // Confined to the lower portion of the chart so it reads as a separate band below
            // the temperature lines rather than overlapping them.
            val regionHeight = canvasHeight * (1f - mPrecipRegionTopFraction)
            val max = mHighestPrecipValue!!
            val min = mLowestPrecipValue!!
            mPrecipPolylineValues?.let {
                for (i in it.indices) {
                    mPrecipPolylineY[i] = if (it[i] == null || max <= min) {
                        (measuredHeight - marginBottom)
                    } else {
                        (measuredHeight - marginBottom - regionHeight * (it[i]!! - min) / (max - min)).toInt()
                    }
                }
            }
        }
    }

    private fun computeSingleCoordinate(
        canvasHeight: Float,
        value: Float,
        max: Float,
        min: Float,
    ): Int {
        return ((measuredHeight - marginBottom - (canvasHeight * (value - min) / (max - min)))).toInt()
    }

    /**
     * Adds a smooth (S-shaped) curve segment between two points, with flat tangents at
     * both ends so consecutive segments connect without a visible kink — gives the
     * polyline a rounded, "spline" look instead of straight angular segments.
     */
    private fun Path.smoothLineTo(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val midX = fromX + (toX - fromX) / 2f
        cubicTo(midX, fromY, midX, toY, toX, toY)
    }

    private fun drawDot(canvas: Canvas, x: Float, y: Float, @ColorInt color: Int) {
        mPaint.apply {
            shader = null
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawCircle(getRTLCompactX(x), y, mDotRadius, mPaint)
    }

    private fun getRTLCompactX(x: Float): Float {
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) (measuredWidth - x) else x
    }

    companion object {
        private const val MARGIN_TOP_DIP = 24f
        private const val MARGIN_BOTTOM_DIP = 36f
        private const val POLYLINE_SIZE_DIP = 1.5f
        private const val POLYLINE_TEXT_SIZE_DIP = 14f
        private const val HISTOGRAM_WIDTH_DIP = 4.5f
        private const val HISTOGRAM_TEXT_SIZE_DIP = 12f
        private const val CHART_LINE_SIZE_DIP = 1f
        private const val TEXT_MARGIN_DIP = 2f
        private const val DOT_RADIUS_DIP = 3f
        // ACT-013: glass cards show the live-wallpaper snapshot through their
        // transparent background, so the high/low-temperature area shading (which
        // used to read as a subtle tint on an opaque card) now reads as a dark
        // blotch/bar over the photo. Lower both factors to keep a faint area hint.
        private const val SHADOW_ALPHA_FACTOR_LIGHT = 0.08f
        private const val SHADOW_ALPHA_FACTOR_DARK = 0.12f

        // The precipitation polyline only uses the bottom 45% of the chart's height, keeping
        // it visually separate from the temperature lines above it.
        private const val PRECIP_REGION_TOP_FRACTION = 0.55f
    }
}
