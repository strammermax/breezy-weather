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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import breezyweather.domain.location.model.Location
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonGroup
import org.breezyweather.R
import org.breezyweather.common.activities.BreezyActivity
import org.breezyweather.common.extensions.getThemeColor
import org.breezyweather.domain.settings.SettingsManager
import org.breezyweather.ui.common.widgets.trend.TrendLayoutManager
import org.breezyweather.ui.common.widgets.trend.TrendRecyclerView
import org.breezyweather.ui.main.adapters.trend.DailyTrendAdapter
import org.breezyweather.ui.main.adapters.trend.HourlyTrendAdapter
import org.breezyweather.ui.main.widgets.TrendRecyclerViewScrollBar
import org.breezyweather.ui.theme.ThemeManager
import org.breezyweather.ui.theme.resource.providers.ResourceProvider

/**
 * Combined forecast card: a single tile that switches between the daily and the hourly trend
 * via a "Dag / Per uur" toggle. LiveWallpaperWeather: this replaces the two separate
 * daily and hourly cards (the standalone hourly card is no longer added to the list).
 */
class DailyViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_daily_trend_card, parent, false)
) {
    private val title: TextView = itemView.findViewById(R.id.daily_block_title)
    private val modeGroup: MaterialButtonGroup = itemView.findViewById(R.id.daily_block_mode_group)
    private val subtitle: TextView = itemView.findViewById(R.id.daily_block_subtitle)
    private val buttonGroup: MaterialButtonGroup = itemView.findViewById(R.id.daily_block_button_group)
    private val trendRecyclerView: TrendRecyclerView = itemView.findViewById(R.id.daily_block_trendRecyclerView)
    private val scrollBar = TrendRecyclerViewScrollBar()

    // false = daily, true = hourly. Kept on the (reused) holder so it survives data refreshes.
    private var hourlyMode = false

    private var boundActivity: BreezyActivity? = null
    private var boundLocation: Location? = null
    private var setSelectedTabCallback: ((String?) -> Unit)? = null
    private var selectedTabValue: String? = null

    init {
        trendRecyclerView.setHasFixedSize(true)
        trendRecyclerView.addItemDecoration(scrollBar)
    }

    @SuppressLint("NotifyDataSetChanged")
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
        boundActivity = activity
        boundLocation = location
        setSelectedTabCallback = setSelectedTab
        selectedTabValue = selectedTab

        setupModeToggle()
        bindTrend()
    }

    /** Single toggle button: label shows the OTHER mode (tap to switch to it). */
    private fun setupModeToggle() {
        modeGroup.children.filter { it is MaterialButton }.toList().forEach { modeGroup.removeView(it) }
        modeGroup.visibility = View.VISIBLE
        // Show the label of the mode you'll switch TO when tapped.
        val label = if (hourlyMode) {
            context.getString(R.string.forecast_toggle_daily)
        } else {
            context.getString(R.string.forecast_toggle_hourly)
        }
        modeGroup.addView(
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 11f
                isCheckable = false
                setOnClickListener {
                    hourlyMode = !hourlyMode
                    selectedTabValue = null
                    setSelectedTabCallback?.invoke(null)
                    setupModeToggle()
                    bindTrend()
                }
            }
        )
    }

    /** Binds the trend chart + trend-type dropdown for the currently selected mode. */
    @SuppressLint("NotifyDataSetChanged")
    private fun bindTrend() {
        val activity = boundActivity ?: return
        val location = boundLocation ?: return
        val weather = location.weather ?: return

        title.text = context.getString(if (hourlyMode) R.string.hourly_forecast else R.string.daily_forecast)

        // The RecyclerView's height is fixed in the layout XML to the daily dimension, since
        // this one view is shared between both modes — without this, hourly mode left a tall
        // empty gap below its (shorter) rows, showing the wallpaper photo through the card.
        trendRecyclerView.layoutParams = trendRecyclerView.layoutParams.apply {
            height = context.resources.getDimensionPixelSize(
                if (hourlyMode) R.dimen.hourly_trend_item_height else R.dimen.daily_trend_item_height
            )
        }

        val sub = if (hourlyMode) weather.current?.hourlyForecast else weather.current?.dailyForecast
        if (!hourlyMode) {
            val availableDays = weather.dailyForecastStartingToday.size
            subtitle.visibility = View.VISIBLE
            subtitle.text = buildList {
                add(context.getString(R.string.forecast_days_available, availableDays))
                sub?.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString("\n")
        } else if (sub.isNullOrEmpty()) {
            subtitle.visibility = View.GONE
        } else {
            subtitle.visibility = View.VISIBLE
            subtitle.text = sub
        }

        val trendRecyclerAdapter: RecyclerView.Adapter<*>
        val displayNames: List<String>
        val applyIndex: (Int) -> Unit
        var currentIndex: Int
        if (hourlyMode) {
            val adapter = HourlyTrendAdapter(activity, trendRecyclerView).apply { bindData(location) }
            displayNames = adapter.adapters.map { it.getDisplayName(activity) }
            applyIndex = { adapter.selectedIndex = it }
            currentIndex = adapter.selectedIndex
            trendRecyclerAdapter = adapter
        } else {
            val adapter = DailyTrendAdapter(activity, trendRecyclerView).apply { bindData(location) }
            displayNames = adapter.adapters.map { it.getDisplayName(activity) }
            applyIndex = { adapter.selectedIndex = it }
            currentIndex = adapter.selectedIndex
            trendRecyclerAdapter = adapter
        }

        selectedTabValue?.let { tab ->
            val idx = displayNames.indexOfFirst { it == tab }
            if (idx >= 0) {
                applyIndex(idx)
                currentIndex = idx
            } else {
                selectedTabValue = null
                setSelectedTabCallback?.invoke(null)
            }
        }

        if (displayNames.size < 2) {
            buttonGroup.visibility = View.GONE
        } else {
            buttonGroup.visibility = View.VISIBLE
            buttonGroup.children.filter { it is MaterialButton }.toList().forEach { buttonGroup.removeView(it) }

            // Icon-only hamburger button (rather than a text button showing the current trend
            // name) so it stays narrow and the title + mode toggle + this button fit on one line.
            val dropdownButton = MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialIconButtonStyle
            )
            dropdownButton.isCheckable = false
            dropdownButton.icon = ContextCompat.getDrawable(context, R.drawable.ic_list)
            dropdownButton.contentDescription = context.getString(R.string.action_more)
            dropdownButton.setOnClickListener { anchor ->
                PopupMenu(context, anchor).apply {
                    displayNames.forEachIndexed { index, name ->
                        menu.add(0, index, index, name)
                    }
                    setOnMenuItemClickListener { menuItem ->
                        applyIndex(menuItem.itemId)
                        selectedTabValue = displayNames[menuItem.itemId]
                        setSelectedTabCallback?.invoke(displayNames[menuItem.itemId])
                        true
                    }
                    show()
                }
            }
            buttonGroup.addView(dropdownButton)
        }

        trendRecyclerView.layoutManager = TrendLayoutManager(context)
        trendRecyclerView.setLineColor(
            context.getThemeColor(com.google.android.material.R.attr.colorOutline)
        )
        trendRecyclerView.setTextColor(
            ContextCompat.getColor(
                context,
                if (ThemeManager.isLightTheme(context, location)) {
                    R.color.colorTextGrey
                } else {
                    R.color.colorTextGrey2nd
                }
            )
        )
        trendRecyclerView.adapter = trendRecyclerAdapter
        trendRecyclerView.setKeyLineVisibility(
            SettingsManager.getInstance(context).isTrendHorizontalLinesEnabled
        )
        if (!hourlyMode) {
            weather.todayIndex?.let { todayIndex ->
                trendRecyclerView.scrollToPosition(todayIndex)
            }
        }
        scrollBar.resetColor(activity)
    }
}
