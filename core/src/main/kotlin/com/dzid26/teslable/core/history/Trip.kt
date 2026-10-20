// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.core.health.TripEstimate
import com.dzid26.teslable.core.health.tripEstimate

/**
 * One finished navigation (#207): the route readings the car sent while it was
 * guiding to one destination, with no gap over [ROUTE_FRESH_MILLIS] between them.
 * Held in memory from the drive log, so the destination name is shown but never
 * copied into a history view of its own.
 */
data class Trip(
    /** The first reading: the trip as the car saw it at the start. */
    val first: RouteSample,
    /** The last reading: the car's closest prediction of the arrival. */
    val last: RouteSample,
    /** The longest remaining distance seen, in miles: the length of the trip. */
    val lengthMiles: Float?,
) {
    /** The phone clock at the start of the trip, millis. */
    val startMillis: Long get() = first.observedAtMillis()

    /** The destination the car named, or null when it sent none. */
    val destination: String? get() = (last.destination ?: first.destination)?.takeIf { it.isNotBlank() }

    /** From the first reading to the arrival: the time between them plus what the car still gave the last reading. */
    val durationMillis: Long
        get() {
            val driven = last.observedAtMillis() - first.observedAtMillis()
            val left = last.minutesToArrival?.takeIf { it.isFinite() && it > 0f } ?: 0f
            return driven + (left * MILLIS_PER_MINUTE).toLong()
        }
}

/**
 * The finished trips in [readings], oldest first. A trip is two or more readings of
 * the same destination, each within [ROUTE_FRESH_MILLIS] of the one before; a single
 * reading is a navigation cancelled at once and is left out.
 */
fun segmentTrips(readings: List<RouteSample>): List<Trip> {
    val runs = mutableListOf<MutableList<RouteSample>>()
    for (reading in readings.sortedBy { it.observedAtMillis() }) {
        val run = runs.lastOrNull()
        val previous = run?.last()
        if (run != null && previous != null && !startsNewTrip(previous, reading)) {
            run += reading
        } else {
            runs += mutableListOf(reading)
        }
    }
    return runs.filter { it.size >= 2 }.map { run ->
        val miles = run.mapNotNull { it.milesToArrival?.takeIf { m -> m.isFinite() && m >= 0f } }.maxOrNull()
        Trip(run.first(), run.last(), miles)
    }
}

private fun startsNewTrip(
    previous: RouteSample,
    next: RouteSample,
): Boolean =
    next.observedAtMillis() - previous.observedAtMillis() > ROUTE_FRESH_MILLIS ||
        namesDiffer(previous.destination, next.destination)

/** The trips that are over: [live], the route being navigated now, is not a past trip. */
fun List<Trip>.excludingLive(live: RouteSample?): List<Trip> =
    if (live == null) this else filterNot { it.last.observedAtMillis() == live.observedAtMillis() }

/**
 * What the trip took, from the full-range scale and the battery reading just before it
 * began: the same numbers as the live card, on the trip's full length and the car's last
 * arrival prediction. A missing input hides its figure.
 */
fun Trip.estimate(
    samples: List<BatterySample>,
    scale: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
): TripEstimate {
    val whole =
        first.copy(
            milesToArrival = lengthMiles,
            energyAtArrivalPercent = last.energyAtArrivalPercent ?: first.energyAtArrivalPercent,
        )
    return tripEstimate(whole, ratedMilesBefore(samples, first.timestampMillis), scale, ratedKwhPerMile, pricePerKwh)
}

/** The rated range of the newest reading at or before [atMillis], if it is under half an hour old. */
internal fun ratedMilesBefore(
    samples: List<BatterySample>,
    atMillis: Long,
): Float? =
    samples
        .filter { it.timestampMillis <= atMillis }
        .maxByOrNull { it.timestampMillis }
        ?.takeIf { atMillis - it.timestampMillis <= RATED_MAX_AGE_MILLIS }
        ?.ratedRangeMiles

private fun namesDiffer(
    a: String?,
    b: String?,
): Boolean = a != null && b != null && a != b

private const val MILLIS_PER_MINUTE = 60_000L
private const val RATED_MAX_AGE_MILLIS = 30 * MILLIS_PER_MINUTE
