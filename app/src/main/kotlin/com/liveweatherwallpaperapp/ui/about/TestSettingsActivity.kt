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

package com.liveweatherwallpaperapp.ui.about

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.ui.common.widgets.Material3ExpressiveCardListItem
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.common.widgets.insets.bottomInsetItem
import com.liveweatherwallpaperapp.ui.settings.preference.SmallSeparatorItem
import com.liveweatherwallpaperapp.ui.theme.compose.themeRipple
import com.liveweatherwallpaperapp.wallpaper.CloudTuningActivity
import com.liveweatherwallpaperapp.wallpaper.WallpaperPhotoManagerActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Tester-only overview reachable from the About screen once tester mode is unlocked (see
 * [AboutScreen]'s 7-tap gesture). Lists the screens spread across the app that testers use to
 * tune the live wallpaper, so they don't need to remember where each one lives.
 */
@AndroidEntryPoint
class TestSettingsActivity : BreezyActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme {
                TestSettingsScreen(onBackPressed = { finish() })
            }
        }
    }
}

private class TestSettingsItem(
    val icon: ImageVector,
    @StringRes val titleId: Int,
    @StringRes val summaryId: Int,
    val onClick: () -> Unit,
)

@Composable
private fun TestSettingsScreen(onBackPressed: () -> Unit) {
    val scrollBehavior = generateCollapsedScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current

    val items = remember {
        listOf(
            TestSettingsItem(
                icon = Icons.Filled.Cloud,
                titleId = R.string.test_settings_cloud_tuning,
                summaryId = R.string.test_settings_cloud_tuning_summary
            ) {
                context.startActivity(Intent(context, CloudTuningActivity::class.java))
            },
            TestSettingsItem(
                icon = Icons.Filled.Image,
                titleId = R.string.test_settings_manage_backgrounds,
                summaryId = R.string.test_settings_manage_backgrounds_summary
            ) {
                context.startActivity(Intent(context, WallpaperPhotoManagerActivity::class.java))
            }
        )
    }

    Material3Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.test_settings_title),
                onBackPressed = onBackPressed,
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = paddingValues.plus(
                PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin))
            )
        ) {
            itemsIndexed(items) { index, item ->
                TestSettingsListItem(
                    icon = item.icon,
                    title = stringResource(item.titleId),
                    summary = stringResource(item.summaryId),
                    isFirst = index == 0,
                    isLast = index == items.lastIndex,
                    onClick = item.onClick
                )
                if (index != items.lastIndex) {
                    SmallSeparatorItem()
                }
            }
            bottomInsetItem()
        }
    }
}

@Composable
private fun TestSettingsListItem(
    icon: ImageVector,
    title: String,
    summary: String,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Material3ExpressiveCardListItem(isFirst = isFirst, isLast = isLast) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = themeRipple(),
                    onClick = onClick
                )
                .padding(dimensionResource(R.dimen.normal_margin)),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.normal_margin)))
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
