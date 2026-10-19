// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.health.TripEstimate
import com.dzid26.teslable.core.health.tripEstimate
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.RouteSample
import com.dzid26.teslable.core.history.compactDuration
import com.dzid26.teslable.core.units.DistanceUnit
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The trip the car is navigating right now (#207): what is left of the route
 * and what the car predicts for the arrival. Shown only while a route is
 * live; a number the car or the app cannot supply hides its line instead of
 * showing a guess. The destination is read live and never stored.
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
            remainingText(route, estimate, unit, separator)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            arrivalText(estimate, unit)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            energyText(estimate, currencyCode)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** "4.7 mi · 12m left", with whichever of the two the car sent. */
@Composable
private fun remainingText(
    route: RouteSample,
    estimate: TripEstimate,
    unit: DistanceUnit,
    separator: Char,
): String? {
    val distance = estimate.milesRemaining?.let { unit.format(it, decimals = 1, decimalSeparator = separator) }
    val time =
        route.minutesToArrival?.takeIf { it.isFinite() && it >= 0f }?.let { minutes ->
            compactDuration((minutes * MILLIS_PER_MINUTE).toLong()) ?: stringResource(R.string.trip_under_a_minute)
        }
    return listOfNotNull(distance, time).takeIf { it.isNotEmpty() }?.joinToString(" · ")?.let { stringResource(R.string.trip_left, it) }
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

/** "5.4 kWh · $1.62 at home price", or the energy alone without a price. */
@Composable
private fun energyText(
    estimate: TripEstimate,
    currencyCode: String?,
): String? {
    val kwh = estimate.energyKwh
    val cost = estimate.costAtHomePrice
    return when {
        kwh == null -> null
        cost == null -> stringResource(R.string.trip_energy, formatKwh(kwh))
        else -> stringResource(R.string.trip_energy_cost, formatKwh(kwh), formatMoney(cost, currencyCode))
    }
}

private fun formatKwh(kwh: Float): String = String.format(Locale.getDefault(), "%.1f", kwh)

private fun formatMoney(
    amount: Float,
    currencyCode: String?,
): String = currencyFormat(currencyCode).format(amount.toDouble())

private const val MILLIS_PER_MINUTE = 60_000L

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
