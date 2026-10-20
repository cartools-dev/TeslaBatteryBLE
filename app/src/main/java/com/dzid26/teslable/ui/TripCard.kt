// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.health.TripEstimate
import com.dzid26.teslable.core.health.tripEstimate
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.RouteSample
import com.dzid26.teslable.core.history.compactDuration
import com.dzid26.teslable.core.history.tripProgress
import com.dzid26.teslable.core.units.DistanceUnit
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The trip the car is navigating right now (#207): how far along it is, what is
 * left of the route in big type and what the car predicts for the arrival. Shown
 * only while a route is live; a number the car or the app cannot supply hides
 * its figure instead of showing a guess. The destination is read live and never
 * stored.
 */
@Composable
internal fun TripCard(
    route: RouteSample,
    estimate: TripEstimate,
    modifier: Modifier = Modifier,
) {
    val unit = LocalDistanceUnit.current
    val separator = remember { DecimalFormatSymbols.getInstance().decimalSeparator }
    val currencyCode = LocalCurrencyCode.current
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            val destination = route.destination?.takeIf { it.isNotBlank() }
            Text(
                text = destination?.let { stringResource(R.string.trip_title_to, it) } ?: stringResource(R.string.trip_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            TripStats(route, estimate, unit, separator, currencyCode)
            route.tripProgress()?.let { progress ->
                Spacer(Modifier.height(8.dp))
                val description = stringResource(R.string.trip_progress_description, (progress * PERCENT_FULL).roundToInt())
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .semantics { contentDescription = description },
                )
            }
            arrivalText(estimate, unit)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The three figures worth a glance, big: distance to go, time to arrive and the price (the energy without one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripStats(
    route: RouteSample,
    estimate: TripEstimate,
    unit: DistanceUnit,
    separator: Char,
    currencyCode: String?,
) {
    val distance = estimate.milesRemaining?.let { unit.format(it, decimals = 1, decimalSeparator = separator) }
    val time =
        route.minutesToArrival?.takeIf { it.isFinite() && it >= 0f }?.let { minutes ->
            compactDuration((minutes * MILLIS_PER_MINUTE).toLong()) ?: stringResource(R.string.trip_under_a_minute)
        }
    val kwh = estimate.energyKwh?.let(::formatKwh)
    val cost = estimate.costAtHomePrice?.let { formatMoney(it, currencyCode) }
    // The stats wrap whole on a narrow phone instead of squeezing the digits.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        distance?.let { TripStat(it, stringResource(R.string.trip_distance_label)) }
        time?.let { TripStat(it, stringResource(R.string.trip_time_label)) }
        when {
            cost != null && kwh != null -> TripStat(cost, stringResource(R.string.trip_cost_label, kwh))
            kwh != null -> TripStat("$kwh kWh", stringResource(R.string.trip_energy_label))
        }
    }
}

@Composable
private fun TripStat(
    value: String,
    label: String,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Arrive with 44% (about 118 mi)": the car's predicted level, with the rated range it stands for once the full range is known. */
@Composable
private fun arrivalText(
    estimate: TripEstimate,
    unit: DistanceUnit,
): String? {
    val percent = estimate.arrivalPercent?.roundToInt() ?: return null
    val ratedMiles = estimate.arrivalRatedMiles
    return if (ratedMiles == null) {
        stringResource(R.string.trip_arrival, percent)
    } else {
        stringResource(R.string.trip_arrival_range, percent, unit.format(ratedMiles))
    }
}

internal fun formatKwh(kwh: Float): String = String.format(Locale.getDefault(), "%.1f", kwh)

internal fun formatMoney(
    amount: Float,
    currencyCode: String?,
): String = currencyFormat(currencyCode).format(amount.toDouble())

private const val MILLIS_PER_MINUTE = 60_000L
private const val PERCENT_FULL = 100f

/**
 * The trip card for a live [route] (see [activeRoute]), nothing for null. The
 * numbers come from the newest battery reading, the fitted full-range scale
 * and, when set, the home energy price.
 */
@Composable
internal fun TripSection(
    route: RouteSample?,
    currentRatedMiles: Float?,
    scale: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
) {
    if (route == null) return
    TripCard(route, tripEstimate(route, currentRatedMiles, scale, ratedKwhPerMile, pricePerKwh))
}
