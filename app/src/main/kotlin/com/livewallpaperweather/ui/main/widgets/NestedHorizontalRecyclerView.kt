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
package com.livewallpaperweather.ui.main.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * This recycler view handles touch events when used inside an already horizontally scrollable/swipeable container
 */
open class NestedHorizontalRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {
    private var mPointerId = 0
    private var mInitialX = 0f
    private var mInitialY = 0f
    private val mTouchSlop = ViewConfiguration.get(getContext()).scaledTouchSlop
    private var mBeingDragged = false
    private var mHorizontalDragged = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mBeingDragged = false
                mHorizontalDragged = false

                mPointerId = ev.getPointerId(0)
                mInitialX = ev.x
                mInitialY = ev.y

                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mPointerId = ev.getPointerId(index)
                mInitialX = ev.getX(index)
                mInitialY = ev.getY(index)
            }

            MotionEvent.ACTION_MOVE -> {
                val index = ev.findPointerIndex(mPointerId)
                if (index == -1) {
                    Log.e(TAG, "Invalid pointerId=$mPointerId in onTouchEvent")
                } else {
                    val x = ev.getX(index)
                    val y = ev.getY(index)

                    if (!mBeingDragged && !mHorizontalDragged) {
                        if (abs(x - mInitialX) > mTouchSlop || abs(y - mInitialY) > mTouchSlop) {
                            mBeingDragged = true
                            if (isHorizontalDrag(x, y)) {
                                mHorizontalDragged = true
                            } else {
                                parent.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = ev.actionIndex
                val id = ev.getPointerId(index)
                if (mPointerId == id) {
                    val newIndex = if (index == 0) 1 else 0

                    this.mPointerId = ev.getPointerId(newIndex)
                    mInitialX = ev.getX(newIndex).toInt().toFloat()
                    mInitialY = ev.getY(newIndex).toInt().toFloat()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mBeingDragged = false
                mHorizontalDragged = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onInterceptTouchEvent(ev) && mBeingDragged && mHorizontalDragged
    }

    /**
     * Whether a drag from [mInitialX]/[mInitialY] to [x]/[y] counts as horizontal. Biased
     * towards horizontal (rather than requiring it to strictly exceed the vertical distance)
     * since a thumb swipe across this strip is rarely perfectly horizontal, and a near-diagonal
     * swipe used to lose to the page's vertical scroll, making the strip look unscrollable.
     */
    private fun isHorizontalDrag(x: Float, y: Float): Boolean {
        val dx = abs(x - mInitialX)
        val dy = abs(y - mInitialY)
        return dx > dy * HORIZONTAL_BIAS_FACTOR
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                val index = ev.actionIndex
                mPointerId = ev.getPointerId(index)
                mInitialX = ev.getX(index)
                mInitialY = ev.getY(index)
            }

            MotionEvent.ACTION_MOVE -> {
                val index = ev.findPointerIndex(mPointerId)
                if (index == -1) {
                    Log.e(TAG, "Invalid pointerId=$mPointerId in onTouchEvent")
                } else {
                    val x = ev.getX(index)
                    val y = ev.getY(index)

                    if (!mBeingDragged && !mHorizontalDragged) {
                        mBeingDragged = true
                        if (isHorizontalDrag(x, y)) {
                            mHorizontalDragged = true
                        } else {
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = ev.actionIndex
                val id = ev.getPointerId(index)
                if (mPointerId == id) {
                    val newIndex = if (index == 0) 1 else 0

                    this.mPointerId = ev.getPointerId(newIndex)
                    mInitialX = ev.getX(newIndex).toInt().toFloat()
                    mInitialY = ev.getY(newIndex).toInt().toFloat()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mBeingDragged = false
                mHorizontalDragged = false
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }

        return super.onTouchEvent(ev)
    }

    companion object {
        private const val TAG = "HorizontalRecyclerView"

        /**
         * Biases drag detection towards horizontal instead of the strict 1:1 ratio (dx > dy)
         * this used to require. At 0.6, a swipe counts as horizontal as long as the horizontal
         * distance is at least 60% of the vertical one — i.e. it still wins even when somewhat
         * more vertical than horizontal, since a thumb swipe across this strip is rarely exact.
         */
        private const val HORIZONTAL_BIAS_FACTOR = 0.6f
    }
}
