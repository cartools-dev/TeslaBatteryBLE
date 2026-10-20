// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.BatterySample
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.Trip
import com.dzid26.teslable.core.history.estimate

/**
 * The finished navigations (#207), one at a time: a selector by name and start time,
 * and for the chosen trip the same figures as the live trip card, its distance,
 * duration and price in big type and the level the car predicted for the arrival.
 * Shows nothing until there is a past trip. [trips] are oldest first; the newest
 * is open by default.
 */
@Composable
internal fun PastTripsCard(
    trips: List<Trip>,
    samples: List<BatterySample>,
    scale: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
) {
    if (trips.isEmpty()) return
    var selectedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    val trip = trips.firstOrNull { it.startMillis == selectedStart } ?: trips.last()
    val estimate = remember(trip, samples, scale, ratedKwhPerMile, pricePerKwh) { trip.estimate(samples, scale, ratedKwhPerMile, pricePerKwh) }
    val unit = LocalDistanceUnit.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.trip_past_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            TripSelector(trips.asReversed(), trip) { selectedStart = it.startMillis }
            Spacer(Modifier.height(8.dp))
            TripStats(
                distance = tripDistanceText(estimate.milesRemaining),
                time = tripTimeText(trip.durationMillis),
                estimate = estimate,
                currencyCode = LocalCurrencyCode.current,
                labels = pastTripLabels,
            )
            arrivalText(estimate, unit)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The chosen trip as a button; a tap lists every trip by name and start time, newest first. */
@Composable
private fun TripSelector(
    options: List<Trip>,
    selected: Trip,
    onSelect: (Trip) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.trip_past_selector_description)
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }) {
            Text(tripLabel(selected, is24Hour), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { trip ->
                DropdownMenuItem(
                    text = { Text(tripLabel(trip, is24Hour), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        open = false
                        onSelect(trip)
                    },
                )
            }
        }
    }
}

/** "Home · Oct 19, 2:05 PM": the destination, or "Trip" without one, and when it began. */
@Composable
private fun tripLabel(
    trip: Trip,
    is24Hour: Boolean,
): String =
    stringResource(
        R.string.trip_past_option,
        trip.destination ?: stringResource(R.string.trip_title),
        formatTime(trip.startMillis, showDate = true, is24Hour = is24Hour),
    )
