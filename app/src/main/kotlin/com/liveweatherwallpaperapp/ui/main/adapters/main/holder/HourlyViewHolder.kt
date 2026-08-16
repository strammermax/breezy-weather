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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonGroup
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.fontScaleToApply
import com.liveweatherwallpaperapp.common.extensions.getThemeColor
import com.liveweatherwallpaperapp.domain.settings.SettingsManager
import com.liveweatherwallpaperapp.ui.common.adapters.ButtonAdapter
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendLayoutManager
import com.liveweatherwallpaperapp.ui.common.widgets.trend.TrendRecyclerView
import com.liveweatherwallpaperapp.ui.main.adapters.trend.HourlyTrendAdapter
import com.liveweatherwallpaperapp.ui.main.widgets.TrendRecyclerViewScrollBar
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import livewallpaperweather.domain.location.model.Location
import kotlin.math.roundToInt

class HourlyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_hourly_trend_card, parent, false)
) {
    private val subtitle: TextView = itemView.findViewById(R.id.hourly_block_subtitle)
    private val buttonGroup: MaterialButtonGroup = itemView.findViewById(R.id.hourly_block_button_group)
    private val trendRecyclerView: TrendRecyclerView = itemView.findViewById(R.id.hourly_block_trendRecyclerView)
    private val scrollBar: TrendRecyclerViewScrollBar = TrendRecyclerViewScrollBar()

    init {
        trendRecyclerView.setHasFixedSize(true)
        trendRecyclerView.addItemDecoration(scrollBar)
    }

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
        selectedTab: String?,
        setSelectedTab: (String?) -> Unit,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val weather = location.weather ?: return

        if (weather.current?.hourlyForecast.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = weather.current?.hourlyForecast
        }

        val trendAdapter = HourlyTrendAdapter(activity, trendRecyclerView).apply {
            bindData(location)
        }
        val buttonList: MutableList<ButtonAdapter.Button> = trendAdapter.adapters.map {
            object : ButtonAdapter.Button {
                override val name = it.getDisplayName(activity)
            }
        }.toMutableList()
        selectedTab?.let { tab ->
            buttonList.indexOfFirst { it.name == tab }.let {
                if (it >= 0) {
                    trendAdapter.selectedIndex = it
                } else {
                    setSelectedTab(null) // Reset
                }
            }
        }

        if (buttonList.size < 2) {
            buttonGroup.visibility = View.GONE
        } else {
            buttonGroup.visibility = View.VISIBLE
            // Dirty trick to get the button group to actually redraw with the correct styles AND the overflow menu
            while (
                buttonGroup.children
                    .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                    .count() != 0
            ) {
                buttonGroup.children
                    .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                    .forEach {
                        buttonGroup.removeView(it)
                    }
            }
            buttonGroup.children
                .filter { it is MaterialButton && it.tag == MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                .forEach {
                    it.contentDescription = context.getString(R.string.action_more)
                }
            buttonList.forEachIndexed { index, button ->
                buttonGroup.addView(
                    MaterialButton(
                        context,
                        null,
                        com.google.android.material.R.attr.materialButtonStyle
                    ).apply {
                        text = button.name
                        isCheckable = true
                        isChecked = index == trendAdapter.selectedIndex
                        setOnClickListener {
                            trendAdapter.selectedIndex = index
                            setSelectedTab(button.name)
                            buttonGroup.children
                                .filter { it is MaterialButton && it.tag != MaterialButtonGroup.OVERFLOW_BUTTON_TAG }
                                .forEach { button ->
                                    (button as MaterialButton).isChecked = false
                                }
                            isChecked = true
                        }
                    }
                )
            }
        }
        trendRecyclerView.layoutManager = TrendLayoutManager(context)
        trendRecyclerView.setLineColor(
            context.getThemeColor(com.google.android.material.R.attr.colorOutline)
        )
        trendRecyclerView.setTextColor(
            ContextCompat.getColor(
                context,
                if (ThemeManager.isLightTheme(context, location)) R.color.colorTextGrey else R.color.colorTextGrey2nd
            )
        )
        trendRecyclerView.adapter = trendAdapter
        trendRecyclerView.setKeyLineVisibility(
            SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled
        )

        // Center the current hour in the visible area on open, instead of leaving the list
        // scrolled to its start (which is now midnight, not "now" — see hourlyTrendCurrentIndex).
        centerOnHour(weather.hourlyTrendCurrentIndex)

        scrollBar.resetColor(activity)
    }

    /**
     * Scrolls so [index] is centered in [trendRecyclerView]'s visible width. Right after
     * adapter/layoutManager assignment the RecyclerView's width is still 0 (its measure/layout
     * pass hasn't run yet), so this re-posts itself until a real width is available.
     */
    private fun centerOnHour(index: Int, attemptsLeft: Int = MAX_CENTER_ATTEMPTS) {
        val layoutManager = trendRecyclerView.layoutManager as? TrendLayoutManager ?: return
        val itemCount = trendRecyclerView.adapter?.itemCount ?: 0
        if (index < 0 || index >= itemCount) return
        val width = trendRecyclerView.width
        if (width <= 0) {
            if (attemptsLeft > 0) {
                trendRecyclerView.post { centerOnHour(index, attemptsLeft - 1) }
            }
            return
        }
        val itemWidth = (
            context.resources.getDimensionPixelSize(R.dimen.trend_item_width) * context.fontScaleToApply
            ).roundToInt()
        val offset = ((width - itemWidth) / 2).coerceAtLeast(0)
        android.util.Log.d(
            "HourlyCenterDebug",
            "scrollToPositionWithOffset index=$index offset=$offset itemWidth=$itemWidth width=$width"
        )
        layoutManager.scrollToPositionWithOffset(index, offset)
    }

    companion object {
        private const val MAX_CENTER_ATTEMPTS = 10
    }
}
