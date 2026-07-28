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

package com.liveweatherwallpaperapp.ui.settings.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.liveweatherwallpaperapp.BreezyWeather
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.extensions.currentLocale
import com.liveweatherwallpaperapp.common.extensions.plus
import com.liveweatherwallpaperapp.common.source.RemovedSource
import com.liveweatherwallpaperapp.sources.SourceManager
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.settings.preference.SmallSeparatorItem
import com.liveweatherwallpaperapp.ui.settings.preference.bottomInsetItem
import com.liveweatherwallpaperapp.ui.settings.preference.clickablePreferenceItem
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceScreen
import com.liveweatherwallpaperapp.ui.settings.preference.composables.PreferenceViewWithCard
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.Collator
import javax.inject.Inject

@AndroidEntryPoint
class PrivacyPolicyActivity : BreezyActivity() {

    @Inject
    lateinit var sourceManager: SourceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BreezyWeatherTheme {
                ContentView()
            }
        }
    }

    @Composable
    private fun ContentView() {
        val context = LocalContext.current
        val resources = LocalResources.current
        val scrollBehavior = generateCollapsedScrollBehavior()
        val uriHandler = LocalUriHandler.current
        val sources = remember {
            sourceManager.getHttpSources()
                .filter { it !is RemovedSource && it.privacyPolicyUrl.startsWith("http") }
                .sortedWith { s1, s2 ->
                    // Sort by name because there are now a lot of sources
                    Collator.getInstance(
                        this@PrivacyPolicyActivity.currentLocale
                    ).compare(s1.name, s2.name)
                }
        }

        Material3Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.about_privacy_policy),
                    onBackPressed = { finish() },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            PreferenceScreen(
                paddingValues = paddingValues.plus(PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin)))
            ) {
                if (!resources.getString(R.string.brand_name).contains("breezy", ignoreCase = true) ||
                    BreezyWeather.instance.isSignedByBreezy ||
                    BreezyWeather.instance.debugMode
                ) {
                    clickablePreferenceItem(R.string.brand_name) { id ->
                        PreferenceViewWithCard(
                            title = stringResource(id),
                            summary = stringResource(R.string.weather_vista_privacy_policy_summary),
                            isFirst = true
                        ) {
                            context.startActivity(Intent(context, WeatherVistaPrivacyPolicyActivity::class.java))
                        }
                    }
                }

                itemsIndexed(sources) { index, preferenceSource ->
                    SmallSeparatorItem()
                    PreferenceViewWithCard(
                        title = preferenceSource.name,
                        summary = preferenceSource.privacyPolicyUrl,
                        isLast = index == sources.lastIndex
                    ) {
                        uriHandler.openUri(preferenceSource.privacyPolicyUrl)
                    }
                }

                bottomInsetItem()
            }
        }
    }
}
