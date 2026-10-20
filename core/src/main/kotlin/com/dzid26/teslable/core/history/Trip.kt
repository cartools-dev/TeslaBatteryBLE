// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.core.health.TripEstimate
import com.dzid26.teslable.core.health.tripEstimate

/**
 * One navigation (#207), finished or in progress: the route readings the car sent while it was
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
    /** True while this is the route being navigated now: its totals are the car's prediction so far. */
    val inProgress: Boolean = false,
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

/** These trips with the one whose newest reading is [live], the route being navigated now, marked in progress. */
fun List<Trip>.markingLive(live: RouteSample?): List<Trip> =
    if (live == null) this else map { it.copy(inProgress = it.last.observedAtMillis() == live.observedAtMillis()) }

/**
 * What a trip took: the totals as of its last reading. The percents and the energy come
 * from the fitted full range and the battery readings around the trip; a missing input
 * hides its figure rather than showing a guess.
 */
data class TripSummary(
    /** The energy, cost and arrival level (see [tripEstimate]) on the trip's full length. */
    val estimate: TripEstimate,
    /** The battery level just before the trip began, percent. */
    val startPercent: Float?,
    /** The level on arrival: the battery reading just after the trip, else the car's own prediction. */
    val arrivalPercent: Float?,
    /** Distance over duration, miles per hour. */
    val averageMilesPerHour: Float?,
    /** The energy over the distance, kWh per mile. */
    val kwhPerMile: Float?,
)

/**
 * The summary of this trip from the battery readings, the full-range scale, the learned rated
 * constant and the home price: the same inputs as the live card, on the trip's full length.
 */
fun Trip.summary(
    samples: List<BatterySample>,
    scale: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
): TripSummary {
    val before = sampleBefore(samples, first.timestampMillis)
    val after = if (inProgress) null else sampleAfter(samples, last.timestampMillis + durationLeftMillis())
    // The level the trip arrived at: the battery reading just after it, else the car's last prediction.
    val arrival = after?.soc(scale) ?: last.energyAtArrivalPercent ?: first.energyAtArrivalPercent
    val estimate =
        tripEstimate(
            first.copy(milesToArrival = lengthMiles, energyAtArrivalPercent = arrival),
            before?.ratedRangeMiles,
            scale,
            ratedKwhPerMile,
            pricePerKwh,
        )
    val miles = lengthMiles?.takeIf { it > 0f }
    val hours = durationMillis.takeIf { it >= MILLIS_PER_MINUTE }?.let { it / MILLIS_PER_HOUR }
    return TripSummary(
        estimate = estimate,
        startPercent = before?.soc(scale),
        arrivalPercent = estimate.arrivalPercent,
        averageMilesPerHour = if (miles != null && hours != null) miles / hours else null,
        kwhPerMile = if (miles != null && miles >= MIN_RATE_MILES) estimate.energyKwh?.div(miles) else null,
    )
}

private fun Trip.durationLeftMillis(): Long {
    val minutes = last.minutesToArrival?.takeIf { it.isFinite() && it > 0f } ?: 0f
    return (minutes * MILLIS_PER_MINUTE).toLong()
}

/** The newest reading at or before [atMillis], if it is under half an hour old. */
private fun sampleBefore(
    samples: List<BatterySample>,
    atMillis: Long,
): BatterySample? =
    samples
        .filter { it.timestampMillis <= atMillis }
        .maxByOrNull { it.timestampMillis }
        ?.takeIf { atMillis - it.timestampMillis <= SAMPLE_MAX_AGE_MILLIS }

/** The first reading at or after [atMillis], if it is under half an hour later. */
private fun sampleAfter(
    samples: List<BatterySample>,
    atMillis: Long,
): BatterySample? =
    samples
        .filter { it.timestampMillis >= atMillis }
        .minByOrNull { it.timestampMillis }
        ?.takeIf { it.timestampMillis - atMillis <= SAMPLE_MAX_AGE_MILLIS }

private fun namesDiffer(
    a: String?,
    b: String?,
): Boolean = a != null && b != null && a != b

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000f
private const val SAMPLE_MAX_AGE_MILLIS = 30 * MILLIS_PER_MINUTE

/** A rate over less than this is noise: the efficiency of a few hundred metres means nothing. */
private const val MIN_RATE_MILES = 1f
