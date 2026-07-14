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

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.AppMessage
import com.liveweatherwallpaperapp.common.AppMessageKind
import com.liveweatherwallpaperapp.common.AppMessageStore
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.utils.helpers.IntentHelper
import com.liveweatherwallpaperapp.ui.theme.ThemeManager
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import com.liveweatherwallpaperapp.ui.theme.compose.themeRipple
import com.liveweatherwallpaperapp.ui.theme.resource.providers.ResourceProvider
import com.liveweatherwallpaperapp.wallpaper.LiveWallpaperConfigActivity
import livewallpaperweather.domain.location.model.Location

/**
 * Main-screen card for app-level messages (system warnings + weetjes) that would otherwise
 * only reach the user through an easy-to-miss notification -- reuses the same visual pattern
 * as [AlertViewHolder]'s weather-alert card, but driven by [AppMessageStore] instead of
 * [livewallpaperweather.domain.weather.model.Weather.alertList].
 */
class AppMessageViewHolder(parent: ViewGroup) : AbstractMainCardViewHolder(
    LayoutInflater.from(parent.context).inflate(R.layout.container_main_app_message, parent, false)
) {

    override fun onBindView(
        activity: BreezyActivity,
        location: Location,
        provider: ResourceProvider,
        listAnimationEnabled: Boolean,
        itemAnimationEnabled: Boolean,
    ) {
        super.onBindView(activity, location, provider, listAnimationEnabled, itemAnimationEnabled)

        val store = AppMessageStore(activity)
        val initialMessages = store.activeMessages()
        itemView.visibility = if (initialMessages.isEmpty()) View.GONE else View.VISIBLE

        itemView.findViewById<ComposeView>(R.id.container_main_app_message_list).setContent {
            BreezyWeatherTheme(!ThemeManager.isLightTheme(activity, location)) {
                var messages by remember { mutableStateOf(initialMessages) }
                Column {
                    messages.forEach { message ->
                        AppMessageItem(
                            message = message,
                            onDismiss = {
                                store.dismiss(message.kind)
                                messages = messages.filterNot { it.kind == message.kind }
                                if (messages.isEmpty()) {
                                    itemView.visibility = View.GONE
                                }
                            },
                            onClick = {
                                when (message.kind) {
                                    AppMessageKind.WEETJE -> {
                                        val intent = if (!message.url.isNullOrBlank()) {
                                            Intent(Intent.ACTION_VIEW, Uri.parse(message.url))
                                        } else {
                                            IntentHelper.buildMainActivityIntent(null)
                                        }
                                        activity.startActivity(intent)
                                    }
                                    AppMessageKind.WARNING -> {
                                        activity.startActivity(
                                            Intent(activity, LiveWallpaperConfigActivity::class.java)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppMessageItem(
    message: AppMessage,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = androidx.compose.ui.Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = themeRipple(),
                onClick = onClick
            ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        leadingContent = {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    if (message.kind == AppMessageKind.WARNING) R.drawable.ic_warning else R.drawable.ic_alert
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                message.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = message.body?.let {
            {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
