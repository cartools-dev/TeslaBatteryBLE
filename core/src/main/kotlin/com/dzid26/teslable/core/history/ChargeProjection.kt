// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import kotlin.math.sqrt

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
    /**
     * The earliest the limit could be reached if the whole remainder ran at
     * the highest charge power seen this run; null until the supply varied.
     */
    val earliestMillis: Long? = null,
    /** The latest, at the lowest charge power seen this run; null like [earliestMillis]. */
    val latestMillis: Long? = null,
) {
    /** The earliest and latest completion together; null when there is no beam. */
    val beam: Pair<Long, Long>?
        get() {
            val earliest = earliestMillis ?: return null
            val latest = latestMillis ?: return null
            return earliest to latest
        }
}

/**
 * Projects when the charge limit is reached while charging.
 *
 * Miles first: when a live [chargeRateMph] is known and a miles-per-percent
 * basis exists (the learned [FullRangeScale.milesPerPercent], else the current run's rated
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
    scale: FullRangeScale? = null,
): ChargeProjection? {
    val projection = baseChargeProjection(samples, chargeRateMph, scale) ?: return null
    return withBeam(projection, samples, scale)
}

private fun baseChargeProjection(
    samples: List<BatterySample>,
    chargeRateMph: Float?,
    scale: FullRangeScale?,
): ChargeProjection? {
    val last = samples.lastOrNull() ?: return null
    if (!last.isCharging) return null
    val target = last.chargeLimit ?: return null
    // Read on the rated range through the fitted scale when it is pinned, so
    // the level the run has gained is a fraction of a percent, not a step.
    val lastSoc = last.soc(scale)
    if (target <= lastSoc) return null

    val runStart = samples.indexOfLast { !it.isCharging } + 1
    val run = samples.subList(runStart, samples.size)
    if (run.size < 2) return null
    val first = run.first()
    val elapsed = last.timestampMillis - first.timestampMillis
    if (elapsed < MIN_RATE_SPAN_MILLIS) return null
    val remaining = target - lastSoc

    val milesPerPercent = milesPerPercent(first, last, scale?.milesPerPercent)
    val rate = chargeRateMph?.takeIf { it.isFinite() && it > 0f }
    if (rate != null && milesPerPercent != null) {
        return ChargeProjection(
            from = last,
            targetPercent = target,
            completionMillis = last.timestampMillis + (remaining * milesPerPercent / rate * 3_600_000f).toLong(),
        )
    }

    val gained = lastSoc - first.soc(scale)
    if (gained <= 0f) {
        // The level has not ticked yet: the run's own rated-range slope keeps
        // moving on a slow charger.
        val milesRate = ratedMilesPerHour(first, last, elapsed)
        val fullRange = scale?.milesPerPercent?.takeIf { it.isFinite() && it > 0f }?.let { it * 100f } ?: impliedFullRangeMiles(last)
        if (milesRate != null && fullRange != null) {
            val remainingMiles = fullRange * remaining / 100f
            return ChargeProjection(
                from = last,
                targetPercent = target,
                completionMillis = last.timestampMillis + (remainingMiles / milesRate * MILLIS_PER_HOUR).toLong(),
            )
        }
        return null
    }
    return ChargeProjection(
        from = last,
        targetPercent = target,
        completionMillis = last.timestampMillis + (remaining.toDouble() / levelSlope(run, scale, elapsed, gained)).toLong(),
    )
}

/**
 * The rate the centre follows, percent per millisecond: the fitted slope of
 * the run's levels once it has enough readings (the same line the beam's error
 * comes from, so the beam is centred on it), else the endpoints' average.
 */
private fun levelSlope(
    run: List<BatterySample>,
    scale: FullRangeScale?,
    elapsed: Long,
    gained: Float,
): Double = levelFit(run, scale)?.slope ?: (gained.toDouble() / elapsed)

/**
 * Adds the beam: the remaining time, stretched by how far the rate could be
 * off. Two sources, each a pair of factors on the remaining time, and the
 * beam spans the widest of them:
 *
 * - The AC voltage moves during a session, and at a fixed current the charge
 *   power follows it, so the time at the run's mean voltage stretches to the
 *   time at its highest and lowest voltage.
 * - The rate is fitted from the run's levels over time; two standard errors
 *   of that slope (floored at the readings' resolution) bound it.
 */
private fun withBeam(
    projection: ChargeProjection,
    samples: List<BatterySample>,
    scale: FullRangeScale?,
): ChargeProjection {
    val runStart = samples.indexOfLast { !it.isCharging } + 1
    val run = samples.subList(runStart, samples.size)
    val factors = mutableListOf<Double>()
    voltageFactors(run)?.let { (fast, slow) -> factors += listOf(fast, slow) }
    rateFactors(run, scale)?.let { (fast, slow) -> factors += listOf(fast, slow) }
    if (factors.isEmpty()) return projection
    val remaining = (projection.completionMillis - projection.from.timestampMillis).toDouble()
    return projection.copy(
        earliestMillis = projection.from.timestampMillis + (remaining * factors.min()).toLong(),
        latestMillis = projection.from.timestampMillis + (remaining * factors.max()).toLong(),
    )
}

/** The remaining-time factors at the run's highest and lowest voltage; null without a spread. */
private fun voltageFactors(run: List<BatterySample>): Pair<Double, Double>? {
    val volts = run.mapNotNull { it.chargerVoltage?.takeIf { volts -> volts > 0 }?.toDouble() }
    val bounds = volts.minOrNull()?.let { min -> volts.maxOrNull()?.let { max -> min to max } }
    if (volts.size < 2 || bounds == null || bounds.second <= bounds.first) return null
    val mean = volts.average()
    return mean / bounds.second to mean / bounds.first
}

/**
 * The remaining-time factors if the rate were two standard errors faster or
 * slower: the slope of level against time over the run, with its error
 * taken from the scatter around the line and never below the resolution of
 * one reading. Needs [MIN_RATE_FIT_SAMPLES] readings and a rising line.
 */
private fun rateFactors(
    run: List<BatterySample>,
    scale: FullRangeScale?,
): Pair<Double, Double>? {
    val relativeError = relativeRateError(run, scale) ?: return null
    val spread = (REACH_SIGMAS * relativeError).coerceAtMost(MAX_RATE_SPREAD)
    return 1.0 / (1.0 + spread) to 1.0 / (1.0 - spread)
}

/** The least-squares line of level against time over a run. */
private class LevelFit(
    /** Percent per millisecond. */
    val slope: Double,
    /** Sum of squared time deviations, ms squared. */
    val sxx: Double,
    /** Standard deviation of the readings around the line, percent, never below one reading's resolution. */
    val scatter: Double,
)

/** One standard error of the fitted level-over-time slope, as a share of the slope; null without a rising fit. */
private fun relativeRateError(
    run: List<BatterySample>,
    scale: FullRangeScale?,
): Double? = levelFit(run, scale)?.let { it.scatter / sqrt(it.sxx) / it.slope }

/** Fits the run's levels over time; null with too few readings or a line that does not rise. */
private fun levelFit(
    run: List<BatterySample>,
    scale: FullRangeScale?,
): LevelFit? {
    val t0 = run.first().timestampMillis
    val xs = run.map { (it.timestampMillis - t0).toDouble() }
    val ys = run.map { it.soc(scale).toDouble() }
    val mx = xs.average()
    val my = ys.average()
    val sxx = xs.sumOf { (it - mx) * (it - mx) }
    val slope = if (sxx > 0.0) xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx else 0.0
    if (run.size < MIN_RATE_FIT_SAMPLES || slope <= 0.0) return null
    val squared =
        xs.indices.sumOf { index ->
            val residual = ys[index] - (my + slope * (xs[index] - mx))
            residual * residual
        }
    return LevelFit(slope, sxx, maxOf(sqrt(squared / (run.size - 2)), readingResolution(scale)))
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
    return gainedMiles / (elapsedMillis / MILLIS_PER_HOUR)
}

/** The full rated range implied by one sample's range and level; null without both. */
private fun impliedFullRangeMiles(sample: BatterySample): Float? {
    val miles = sample.ratedRangeMiles?.takeIf { it.isFinite() && it > 0f } ?: return null
    return if (sample.batteryLevel > 0) miles / (sample.batteryLevel / 100f) else null
}

/** Miles per displayed percent: the learned scale, else the run's rated-range gain. */
private fun milesPerPercent(
    first: BatterySample,
    last: BatterySample,
    scaleMilesPerPercent: Float?,
): Float? {
    scaleMilesPerPercent?.takeIf { it.isFinite() && it > 0f }?.let { return it }
    val firstMiles = first.ratedRangeMiles ?: return null
    val lastMiles = last.ratedRangeMiles ?: return null
    val gainedPercent = last.batteryLevel - first.batteryLevel
    if (gainedPercent <= 0) return null
    val gainedMiles = lastMiles - firstMiles
    if (!gainedMiles.isFinite() || gainedMiles <= 0f) return null
    return gainedMiles / gainedPercent
}

private const val MIN_RATE_SPAN_MILLIS = 2 * 60_000L

private const val MILLIS_PER_HOUR = 3_600_000f

private const val MIN_RATE_FIT_SAMPLES = 4

/** How many standard errors the beam reaches. */
private const val REACH_SIGMAS = 2.0

/** The beam never claims the rate could be off by more than this share. */
private const val MAX_RATE_SPREAD = 0.9
