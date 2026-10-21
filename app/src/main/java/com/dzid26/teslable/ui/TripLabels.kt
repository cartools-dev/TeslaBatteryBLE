// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.health.TripEstimate
import com.dzid26.teslable.core.history.compactDuration
import java.text.DecimalFormatSymbols

/** The string ids naming the distance and time figures: what is left of a live trip, what a past one took. */
internal class TripLabels(
    @StringRes val distance: Int,
    @StringRes val time: Int,
)

internal val liveTripLabels = TripLabels(R.string.trip_distance_label, R.string.trip_time_label)

internal val pastTripLabels = TripLabels(R.string.trip_past_distance_label, R.string.trip_past_time_label)

/** "4.7 mi" from miles, or null when there are none. */
@Composable
internal fun tripDistanceText(miles: Float?): String? {
    val unit = LocalDistanceUnit.current
    val separator = remember { DecimalFormatSymbols.getInstance().decimalSeparator }
    return miles?.let { unit.format(it, decimals = 1, decimalSeparator = separator) }
}

/** "12m" from a duration in millis, "under a minute" below one, null for none or a negative one. */
@Composable
internal fun tripTimeText(millis: Long?): String? =
    millis?.takeIf { it >= 0L }?.let { compactDuration(it) ?: stringResource(R.string.trip_under_a_minute) }

/** The three figures worth a glance, big: distance, time and the price (the energy without one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripStats(
    distance: String?,
    time: String?,
    estimate: TripEstimate,
    currencyCode: String?,
    labels: TripLabels,
) {
    val kwh = estimate.energyKwh?.let(::formatKwh)
    val cost = estimate.costAtHomePrice?.let { formatMoney(it, currencyCode) }
    // The stats wrap whole on a narrow phone instead of squeezing the digits.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(STAT_GAP), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        distance?.let { TripStat(it, stringResource(labels.distance)) }
        time?.let { TripStat(it, stringResource(labels.time)) }
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
        // A long label wraps under its figure instead of widening the column and pushing the next figure down a row.
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = STAT_LABEL_MAX_WIDTH),
        )
    }
}

private val STAT_GAP = 16.dp
private val STAT_LABEL_MAX_WIDTH = 112.dp
