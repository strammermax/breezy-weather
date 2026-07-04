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

package com.livewallpaperweather.ui.about

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.livewallpaperweather.BreezyWeather
import com.livewallpaperweather.BuildConfig
import com.livewallpaperweather.R
import com.livewallpaperweather.background.updater.interactor.GetApplicationRelease
import com.livewallpaperweather.common.extensions.plus
import com.livewallpaperweather.common.extensions.withIOContext
import com.livewallpaperweather.common.extensions.withUIContext
import com.livewallpaperweather.common.utils.helpers.SnackbarHelper
import com.livewallpaperweather.ui.common.composables.AlertDialogLink
import com.livewallpaperweather.ui.common.widgets.Material3ExpressiveCardListItem
import com.livewallpaperweather.ui.common.widgets.Material3Scaffold
import com.livewallpaperweather.ui.common.widgets.generateCollapsedScrollBehavior
import com.livewallpaperweather.ui.common.widgets.getCardListItemMarginDp
import com.livewallpaperweather.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.livewallpaperweather.ui.common.widgets.insets.bottomInsetItem
import com.livewallpaperweather.ui.settings.preference.SmallSeparatorItem
import com.livewallpaperweather.ui.settings.preference.largeSeparatorItem
import com.livewallpaperweather.ui.theme.compose.themeRipple

internal class AboutAppLinkItem(
    @DrawableRes val iconId: Int,
    @StringRes val titleId: Int,
    val onClick: () -> Unit,
)

@Composable
internal fun AboutScreen(
    onBackPressed: () -> Unit,
    aboutViewModel: AboutViewModel = viewModel(),
) {
    val scrollBehavior = generateCollapsedScrollBehavior()

    val scope = rememberCoroutineScope()
    val isCheckingUpdates = remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = LocalActivity.current

    val uriHandler = LocalUriHandler.current
    val linkToOpen = rememberSaveable { mutableStateOf("") }
    val dialogLinkOpenState = rememberSaveable { mutableStateOf(false) }

    val contactLinks = buildList {
        BuildConfig.SOURCE_CODE_LINK.takeIf {
            it.startsWith("https://") &&
                (
                    !BuildConfig.SOURCE_CODE_LINK.contains("breezy", ignoreCase = true) ||
                        BreezyWeather.instance.isSignedByBreezy ||
                        BreezyWeather.instance.debugMode
                    )
        }?.let {
            add(
                AboutAppLinkItem(
                    iconId = R.drawable.ic_code,
                    titleId = R.string.about_source_code
                ) {
                    linkToOpen.value = it
                    dialogLinkOpenState.value = true
                }
            )
        }
        BuildConfig.CONTACT_MATRIX.takeIf {
            it.startsWith("https://") &&
                (
                    !BuildConfig.CONTACT_MATRIX.contains("breezy", ignoreCase = true) ||
                        BreezyWeather.instance.isSignedByBreezy ||
                        BreezyWeather.instance.debugMode
                    )
        }?.let {
            add(
                AboutAppLinkItem(
                    iconId = R.drawable.ic_forum,
                    titleId = R.string.about_matrix
                ) {
                    linkToOpen.value = it
                    dialogLinkOpenState.value = true
                }
            )
        }
    }

    val isUpdateCheckerEnabled = remember {
        BreezyWeather.instance.isGitHubUpdateCheckerEnabled ||
            (
                BuildConfig.RELEASES_LINK.isNotEmpty() &&
                    (
                        !BuildConfig.RELEASES_LINK.contains("breezy", ignoreCase = true) ||
                            BreezyWeather.instance.isSignedByBreezy ||
                            BreezyWeather.instance.debugMode
                        )
                )
    }

    Material3Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            FitStatusBarTopAppBar(
                title = stringResource(R.string.action_about),
                onBackPressed = onBackPressed,
                scrollBehavior = scrollBehavior
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = it.plus(
                PaddingValues(horizontal = dimensionResource(R.dimen.normal_margin))
            )
        ) {
            item {
                Header()
            }
            if (isUpdateCheckerEnabled) {
                item {
                    AboutAppLink(
                        isFirst = true,
                        isLast = true,
                        icon = {
                            // Use crossfade animation to prevent the progress indicator from flickering when repeatedly
                            // pressing the update card as this causes the loading state to change back and forth almost
                            // instantly.
                            Crossfade(
                                targetState = isCheckingUpdates.value,
                                label = ""
                            ) { loading ->
                                when (loading) {
                                    false -> {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_sync),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    true -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        },
                        title = stringResource(R.string.about_check_for_app_updates),
                        onClick = {
                            if (BreezyWeather.instance.isGitHubUpdateCheckerEnabled) {
                                if (!isCheckingUpdates.value) {
                                    scope.launch {
                                        isCheckingUpdates.value = true

                                        withUIContext {
                                            try {
                                                when (
                                                    val result = withIOContext {
                                                        aboutViewModel.checkForUpdate(
                                                            context,
                                                            forceCheck = true
                                                        )
                                                    }
                                                ) {
                                                    is GetApplicationRelease.Result.NewUpdate -> {
                                                        SnackbarHelper.showSnackbar(
                                                            context.getString(
                                                                R.string.notification_app_update_available
                                                            ),
                                                            context.getString(R.string.action_download)
                                                        ) {
                                                            uriHandler.openUri(result.release.releaseLink)
                                                        }
                                                    }

                                                    is GetApplicationRelease.Result.NoNewUpdate -> {
                                                        SnackbarHelper.showSnackbar(
                                                            context.getString(R.string.about_no_new_updates)
                                                        )
                                                    }

                                                    is GetApplicationRelease.Result.OsTooOld -> {
                                                        SnackbarHelper.showSnackbar(
                                                            context.getString(
                                                                R.string.about_update_check_eol
                                                            )
                                                        )
                                                    }

                                                    else -> {}
                                                }
                                            } catch (e: Exception) {
                                                e.message?.let { msg ->
                                                    SnackbarHelper.showSnackbar(
                                                        msg
                                                    )
                                                }
                                                e.printStackTrace()
                                            } finally {
                                                isCheckingUpdates.value = false
                                            }
                                        }
                                    }
                                }
                            } else {
                                linkToOpen.value = BuildConfig.RELEASES_LINK
                                dialogLinkOpenState.value = true
                            }
                        }
                    )
                }
            }
            largeSeparatorItem()

            if (contactLinks.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.about_contact))
                }
                itemsIndexed(contactLinks) { index, item ->
                    AboutAppLink(
                        iconId = item.iconId,
                        title = stringResource(item.titleId),
                        isFirst = index == 0,
                        isLast = index == contactLinks.lastIndex,
                        onClick = item.onClick
                    )
                    if (index != contactLinks.lastIndex) {
                        SmallSeparatorItem()
                    }
                }
                largeSeparatorItem()
            }

            item { SectionTitle(stringResource(R.string.about_app)) }
            if (activity != null) {
                itemsIndexed(aboutViewModel.getAboutAppLinks(activity)) { index, item ->
                    AboutAppLink(
                        iconId = item.iconId,
                        title = stringResource(item.titleId),
                        isFirst = index == 0,
                        isLast = index == aboutViewModel.getAboutAppLinks(activity).lastIndex,
                        onClick = item.onClick
                    )
                    if (index != aboutViewModel.getAboutAppLinks(activity).lastIndex) {
                        SmallSeparatorItem()
                    }
                }
            }

            largeSeparatorItem()
            item { SectionTitle(stringResource(R.string.about_fork_title)) }
            item {
                Text(
                    text = stringResource(R.string.about_fork_description),
                    modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item {
                AboutAppLink(
                    iconId = R.drawable.ic_code,
                    title = stringResource(R.string.about_fork_source),
                    isFirst = true,
                    isLast = true,
                    onClick = {
                        linkToOpen.value = "https://github.com/breezy-weather/breezy-weather"
                        dialogLinkOpenState.value = true
                    }
                )
            }

            bottomInsetItem(
                extraHeight = getCardListItemMarginDp(context).dp
            )
        }

        if (dialogLinkOpenState.value) {
            AlertDialogLink(
                onClose = { dialogLinkOpenState.value = false },
                linkToOpen = linkToOpen.value
            )
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_round),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
        Spacer(
            modifier = Modifier
                .height(dimensionResource(R.dimen.small_margin))
                .fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.brand_name),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = versionFormatted,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(dimensionResource(R.dimen.normal_margin)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
}

private val versionFormatted: String
    get() = when {
        BuildConfig.DEBUG -> "v${BuildConfig.VERSION_NAME} (debug ${BuildConfig.COMMIT_SHA})"
        else -> "v${BuildConfig.VERSION_NAME}"
    }

@Composable
private fun AboutAppLink(
    icon: @Composable () -> Unit,
    title: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.normal_margin)))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun AboutAppLink(
    @DrawableRes iconId: Int,
    title: String,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit,
) {
    AboutAppLink(
        icon = {
            Icon(
                painter = painterResource(iconId),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        title = title,
        isFirst = isFirst,
        isLast = isLast,
        onClick = onClick
    )
}

