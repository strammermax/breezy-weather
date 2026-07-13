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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.common.update.GithubReleaseNotesSource
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Shows the GitHub release history (see [GithubReleaseNotesSource]) — reachable from
 * Settings > About > Release notes, and auto-launched once by [com.liveweatherwallpaperapp.ui
 * .main.MainActivity] the first time the app is opened after an update.
 */
@AndroidEntryPoint
class ReleaseNotesActivity : BreezyActivity() {

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
        val scrollBehavior = generateCollapsedScrollBehavior()
        var releases by remember { mutableStateOf<List<GithubReleaseNotesSource.ReleaseNotes>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            releases = GithubReleaseNotesSource(applicationContext).getReleases()
            loading = false
        }

        Material3Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                FitStatusBarTopAppBar(
                    title = stringResource(R.string.about_release_notes),
                    onBackPressed = { finish() },
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(dimensionResource(R.dimen.normal_margin))
            ) {
                when {
                    loading -> CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
                    releases.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.release_notes_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.release_notes_whats_new),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        releases.forEachIndexed { index, release ->
                            ReleaseSection(
                                release = release,
                                initiallyExpanded = index == 0
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ReleaseSection(
        release: GithubReleaseNotesSource.ReleaseNotes,
        initiallyExpanded: Boolean,
    ) {
        var expanded by remember(release.tagName) { mutableStateOf(initiallyExpanded) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(top = 18.dp, bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(end = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val date = formatReleaseDate(release.publishedAt)
                if (date.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        if (expanded) {
            val changes = release.changes
            if (changes.isEmpty()) {
                Text(
                    text = stringResource(R.string.release_notes_no_details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            } else {
                changes.forEach { change ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(text = "•", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = change,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    private fun formatReleaseDate(value: String): String = runCatching {
        if (value.length == 10) {
            java.time.LocalDate.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        } else {
            OffsetDateTime.parse(value).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }.getOrDefault(value)
}
