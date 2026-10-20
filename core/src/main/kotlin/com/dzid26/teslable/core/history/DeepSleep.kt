// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * One stretch the car slept through between two reads: it was awake at [startMillis] and again at [endMillis],
 * and the battery lost [dropPercent] points in between (read through the fitted scale when it is pinned).
 */
data class DeepSleep(
    val startMillis: Long,
    val endMillis: Long,
    val dropPercent: Double = 0.0,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** The drain over the sleep, percentage points lost per hour. */
    val percentPerHour: Double get() = dropPercent * HOUR_MILLIS / durationMillis
}

/**
 * The longest deep sleep in the logs, by the definition the parked projection
 * uses ([dischargeProjection]): the gap between two consecutive battery reads
 * with the car parked throughout (neither read charging, no gear, one odometer
 * reading) and a VCSEC status inside the gap saying asleep and none saying awake.
 * The reads are awake moments, so the gap is the sleep with at most a read's
 * length of slack at each end. A gap shorter than the projection's minimum
 * span does not count as slept enough. Ties go to the later sleep.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param statuses the same vehicle's status log, oldest first.
 * @param scale the fitted rated-range scale that refines the drop; null reads the integer levels.
 */
fun longestDeepSleep(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    statuses: List<StatusSample>,
    scale: FullRangeScale? = null,
): DeepSleep? {
    val readings = driveReadingsAt(samples, drives.filter { it.odometerInHundredthsOfAMile != null })
    return (1 until samples.size)
        .filter { index -> parkedBetween(samples[index - 1], samples[index], readings[index - 1], readings[index]) }
        .map { index -> samples[index - 1] to samples[index] }
        .filter { (a, b) -> sleptThrough(a, b, statuses) }
        .map { (a, b) -> deepSleepBetween(a, b, scale) }
        .filter { it.durationMillis >= MIN_SLEEP_SPAN_MILLIS }
        .maxWithOrNull(compareBy({ it.durationMillis }, { it.startMillis }))
}

private fun deepSleepBetween(
    a: BatterySample,
    b: BatterySample,
    scale: FullRangeScale?,
) = DeepSleep(
    startMillis = a.readAtMillis ?: a.timestampMillis,
    endMillis = b.readAtMillis ?: b.timestampMillis,
    dropPercent = (a.soc(scale) - b.soc(scale)).toDouble(),
)

private fun parkedBetween(
    a: BatterySample,
    b: BatterySample,
    readingA: DriveSample?,
    readingB: DriveSample?,
): Boolean = !a.isCharging && !b.isCharging && stayedParked(readingA, readingB)

private const val HOUR_MILLIS = 60 * 60_000L
