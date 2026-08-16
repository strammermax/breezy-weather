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

package com.liveweatherwallpaperapp.ui.details.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.WebView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.liveweatherwallpaperapp.R
import com.liveweatherwallpaperapp.common.extensions.pxToDp
import com.liveweatherwallpaperapp.common.extensions.spToPx
import com.liveweatherwallpaperapp.radar.RadarActivity
import com.liveweatherwallpaperapp.radar.RadarWebMapLoader
import com.liveweatherwallpaperapp.ui.common.widgets.Material3ExpressiveCardListItem
import kotlinx.coroutines.launch
import livewallpaperweather.domain.location.model.Location
import kotlin.math.roundToInt

@Composable
fun DetailsItem(
    headlineText: String,
    supportingText: String?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    withHelp: Boolean = false,
) {
    val context = LocalContext.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
            )
        }
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headlineText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (withHelp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        // TooltipBox already takes care of adding the info that there is a tooltip
                        contentDescription = null,
                        modifier = Modifier
                            .size(
                                context.pxToDp(
                                    context.spToPx(
                                        MaterialTheme.typography.bodyLarge.fontSize.value.roundToInt()
                                    ).roundToInt()
                                ).dp
                            )
                    )
                }
            }
            supportingText?.let {
                Text(text = it)
            }
        }
    }
}

@Composable
fun UnavailableChart(
    size: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.small_margin))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clearAndSetSemantics {} // Chart is not read by screen readers, so just ignore the info message
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_about),
                contentDescription = null
            )
            Text(
                text = if (size == 0) {
                    stringResource(R.string.chart_no_hourly_data)
                } else {
                    stringResource(R.string.chart_not_enough_hourly_data)
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DaytimeLabel(
    modifier: Modifier = Modifier,
) {
    TextFixedHeight(
        text = stringResource(R.string.daytime),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

@Composable
fun NighttimeLabelWithInfo(
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val coroutineScope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip { Text(stringResource(R.string.nighttime_details)) }
        },
        state = tooltipState
    ) {
        Row(
            modifier = modifier
                .clickable {
                    coroutineScope.launch {
                        tooltipState.show()
                    }
                }
                .height(
                    with(LocalDensity.current) {
                        MaterialTheme.typography.labelMedium.lineHeight.toDp()
                    }
                ),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.small_margin)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.nighttime),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null, // TooltipBox already takes care of adding the info that there is a tooltip
                modifier = Modifier
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun DetailsSectionHeader(
    sectionName: AnnotatedString,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
) {
    DetailsSectionHeader(
        section = {
            Text(
                text = sectionName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(
                        bottom = if (subtitle == null) dimensionResource(R.dimen.small_margin) else 0.dp
                    )
            )
        },
        subtitle = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(
                            bottom = dimensionResource(R.dimen.small_margin)
                        )
                )
            }
        },
        modifier = modifier
    )
}

@Composable
fun DetailsSectionHeader(
    sectionName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    DetailsSectionHeader(
        section = {
            Text(
                text = sectionName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(
                        bottom = if (subtitle == null) dimensionResource(R.dimen.small_margin) else 0.dp
                    )
            )
        },
        subtitle = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(
                            bottom = dimensionResource(R.dimen.small_margin)
                        )
                )
            }
        },
        modifier = modifier
    )
}

@Composable
internal fun DetailsSectionHeader(
    section: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .padding(start = dimensionResource(R.dimen.normal_margin))
    ) {
        section()
        subtitle?.invoke()
    }
}

@Composable
fun DetailsSectionDivider(
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(
        modifier = modifier
            .padding(vertical = dimensionResource(R.dimen.normal_margin))
    )
}

@Composable
fun DetailsCardText(
    text1: String,
    modifier: Modifier = Modifier,
    text2: String? = null,
) {
    Material3ExpressiveCardListItem(
        isFirst = true,
        isLast = true,
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = dimensionResource(R.dimen.normal_margin),
                vertical = dimensionResource(R.dimen.small_margin)
            ),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.normal_margin))
        ) {
            Text(text1)
            text2?.let {
                Text(it)
            }
        }
    }
}

@Composable
fun TextFixedHeight(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text,
        modifier
            .height(
                with(LocalDensity.current) {
                    style.lineHeight.times(maxLines).toDp()
                }
            )
            .wrapContentHeight(align = Alignment.CenterVertically),
        color,
        autoSize,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        minLines,
        onTextLayout,
        style
    )
}

@Composable
fun TextFixedHeight(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = 1,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text,
        modifier
            .height(
                with(LocalDensity.current) {
                    style.lineHeight.times(maxLines).toDp()
                }
            )
            .wrapContentHeight(align = Alignment.CenterVertically),
        color,
        autoSize,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        minLines,
        inlineContent,
        onTextLayout,
        style
    )
}

/**
 * Compact, non-interactive Ventusky map preview for a details screen (e.g. "windsnelheid-kaart"
 * for the wind screen, see call sites) -- the same "tap opens the full radar screen" pattern as
 * the main screen's own radar tile ([com.liveweatherwallpaperapp.ui.main.adapters.main.holder.
 * RadarViewHolder]), just embedded at the bottom of the relevant details screen instead. Always
 * opens on the Ventusky tab of the full radar screen (which only shows the precipitation layer;
 * there's no way to deep-link it to this tile's specific layer).
 *
 * [targetDayOfMonth]/[targetMonth] (Calendar.MONTH, 0-based), when given, make the tile jump to
 * that day on Ventusky's own date-tab strip instead of showing "now" -- pass null/null for today.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun VentuskyDetailTile(
    location: Location,
    ventuskyPage: String,
    modifier: Modifier = Modifier,
    targetMonth: Int? = null,
    targetDayOfMonth: Int? = null,
) {
    val context = LocalContext.current
    Material3ExpressiveCardListItem(
        isFirst = true,
        isLast = true,
        modifier = modifier.fillMaxWidth()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(320.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Ventusky's site remembers the last-viewed layer client-side (cookies/local
                    // storage) and re-applies it on load, regardless of which page slug is
                    // requested -- without this, every tile after the first ends up showing
                    // whichever layer was viewed most recently instead of its own configured one.
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    clearCache(true)
                    RadarWebMapLoader.loadVentusky(
                        this,
                        location.latitude,
                        location.longitude,
                        compact = true,
                        page = ventuskyPage,
                        targetMonth = targetMonth,
                        targetDay = targetDayOfMonth
                    )
                    // Non-interactive preview: swallow the tap (like the WebView's own map drag)
                    // and forward it as a single click that opens the full radar screen instead --
                    // same fix as RadarViewHolder's home-screen radar tile.
                    setOnClickListener {
                        context.startActivity(RadarActivity.createIntent(context, location, "ventusky"))
                    }
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) performClick()
                        true
                    }
                }
            }
        )
    }
}

/**
 * Add an inset of top + bottom padding + fab menu size
 * Currently top + bottom padding is applied twice due to an upstream bug, but we only apply the top padding once on
 *  our side (hence the * 3 multiplier)
 */
fun LazyListScope.bottomDetailsInset(modifier: Modifier = Modifier) {
    item(key = "bottom_inset") {
        Column(modifier = modifier) {
            Spacer(modifier = Modifier.height(16.dp * 3 + 56.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
