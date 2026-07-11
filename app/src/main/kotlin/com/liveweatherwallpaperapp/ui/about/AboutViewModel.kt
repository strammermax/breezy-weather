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

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {
    internal fun getAboutAppLinks(activity: Activity): Array<AboutAppLinkItem> {
        return arrayOf(
            AboutAppLinkItem(
                iconId = R.drawable.ic_shield_lock,
                titleId = R.string.about_privacy_policy
            ) {
                IntentHelper.startPrivacyPolicyActivity(activity)
            },
            AboutAppLinkItem(
                iconId = R.drawable.ic_contract,
                titleId = R.string.about_dependencies
            ) {
                IntentHelper.startDependenciesActivity(activity)
            },
            AboutAppLinkItem(
                iconId = R.drawable.ic_code,
                titleId = R.string.about_release_notes
            ) {
                IntentHelper.startReleaseNotesActivity(activity)
            }
        )
    }
}
