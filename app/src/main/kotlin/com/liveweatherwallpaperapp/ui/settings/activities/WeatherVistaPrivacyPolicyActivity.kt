package com.liveweatherwallpaperapp.ui.settings.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.activities.BreezyActivity
import com.liveweatherwallpaperapp.ui.common.widgets.Material3Scaffold
import com.liveweatherwallpaperapp.ui.common.widgets.generateCollapsedScrollBehavior
import com.liveweatherwallpaperapp.ui.common.widgets.insets.FitStatusBarTopAppBar
import com.liveweatherwallpaperapp.ui.theme.compose.BreezyWeatherTheme

class WeatherVistaPrivacyPolicyActivity : BreezyActivity() {

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
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
                        .padding(dimensionResource(R.dimen.normal_margin))
                ) {
                    Text(
                        text = stringResource(R.string.brand_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.weather_vista_privacy_policy_updated),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_intro_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_intro)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_data_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_data)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_use_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_use)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_sharing_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_sharing)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_retention_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_retention)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_rights_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_rights)
                    )
                    PolicySection(
                        title = stringResource(R.string.weather_vista_privacy_policy_contact_title),
                        body = stringResource(R.string.weather_vista_privacy_policy_contact)
                    )
                }
            }
        }
    }

    @Composable
    private fun PolicySection(title: String, body: String) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}
