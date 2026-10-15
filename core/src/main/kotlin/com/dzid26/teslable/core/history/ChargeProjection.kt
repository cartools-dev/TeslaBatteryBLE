// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

// Chart-only analysis kept in `core` so the drawing code stays free of the
// rules and the rules stay unit-testable.

/** Where the current charge is heading, derived from the samples so far. */
data class ChargeProjection(
    /** The latest sample, where the projected line starts. */
    val from: BatterySample,
    /** The charge limit the projection aims at. */
    val targetPercent: Int,
    /** Projected wall-clock time the limit is reached. */
    val completionMillis: Long,
)

/**
 * Projects when the charge limit is reached while charging.
 *
 * Miles first: when a live [chargeRateMph] is known and a miles-per-percent
 * basis exists (the learned [fullRangeMiles], else the current run's rated
 * range gain), the remaining time is `remainingPercent * milesPerPercent /
 * rateMph`. The percent-based average of the trailing run is the fallback
 * (integer SOC only). Returns null when the last sample is not charging, the
 * charge limit is unknown or already reached, the run has fewer than two
 * samples, the readings are less than [MIN_RATE_SPAN_MILLIS] apart, or the
 * chosen basis has no positive slope.
 */
fun chargeProjection(
    samples: List<BatterySample>,
    chargeRateMph: Float? = null,
    fullRangeMiles: Float? = null,
): ChargeProjection? {
    val last = samples.lastOrNull() ?: return null
    if (!last.isCharging) return null
    val target = last.chargeLimit ?: return null
    if (target <= last.percent) return null

    val runStart = samples.indexOfLast { !it.isCharging } + 1
    val run = samples.subList(runStart, samples.size)
    if (run.size < 2) return null
    val first = run.first()
    val elapsed = last.timestampMillis - first.timestampMillis
    if (elapsed < MIN_RATE_SPAN_MILLIS) return null
    val remaining = target - last.percent

    val milesPerPercent = milesPerPercent(first, last, fullRangeMiles)
    val rate = chargeRateMph?.takeIf { it.isFinite() && it > 0f }
    if (rate != null && milesPerPercent != null) {
        return ChargeProjection(
            from = last,
            targetPercent = target,
            completionMillis = last.timestampMillis + (remaining * milesPerPercent / rate * 3_600_000f).toLong(),
        )
    }

    val gained = last.percent - first.percent
    if (gained <= 0) {
        // The level has not ticked yet: the run's own rated-range slope keeps
        // moving on a slow charger.
        val milesRate = ratedMilesPerHour(first, last, elapsed)
        val fullRange = fullRangeMiles?.takeIf { it.isFinite() && it > 0f } ?: impliedFullRangeMiles(last)
        if (milesRate != null && fullRange != null) {
            val remainingMiles = fullRange * remaining / 100f
            return ChargeProjection(
                from = last,
                targetPercent = target,
                completionMillis = last.timestampMillis + (remainingMiles / milesRate * 3_600_000f).toLong(),
            )
        }
        return null
    }
    return ChargeProjection(
        from = last,
        targetPercent = target,
        completionMillis = last.timestampMillis + remaining * elapsed / gained,
    )
}

/** Rated miles gained per hour over the run; null without both readings or a positive gain. */
private fun ratedMilesPerHour(
    first: BatterySample,
    last: BatterySample,
    elapsedMillis: Long,
): Float? {
    val firstMiles = first.ratedRangeMiles ?: return null
    val lastMiles = last.ratedRangeMiles ?: return null
    val gainedMiles = lastMiles - firstMiles
    if (!gainedMiles.isFinite() || gainedMiles <= 0f) return null
    return gainedMiles / (elapsedMillis / 3_600_000f)
}

/** The full rated range implied by one sample's range and level; null without both. */
private fun impliedFullRangeMiles(sample: BatterySample): Float? {
    val miles = sample.ratedRangeMiles?.takeIf { it.isFinite() && it > 0f } ?: return null
    return if (sample.percent > 0) miles / (sample.percent / 100f) else null
}

/** Miles per displayed percent: the learned scale, else the run's rated-range gain. */
private fun milesPerPercent(
    first: BatterySample,
    last: BatterySample,
    fullRangeMiles: Float?,
): Float? {
    fullRangeMiles?.takeIf { it.isFinite() && it > 0f }?.let { return it / 100f }
    val firstMiles = first.ratedRangeMiles ?: return null
    val lastMiles = last.ratedRangeMiles ?: return null
    val gainedPercent = last.percent - first.percent
    if (gainedPercent <= 0) return null
    val gainedMiles = lastMiles - firstMiles
    if (!gainedMiles.isFinite() || gainedMiles <= 0f) return null
    return gainedMiles / gainedPercent
}

private const val MIN_RATE_SPAN_MILLIS = 2 * 60_000L
