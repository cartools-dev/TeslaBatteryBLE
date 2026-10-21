// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * One stretch the car slept: the status log first said asleep at [startMillis] and said awake (or was last heard
 * asleep) at [endMillis]. [dropPercent] is what the battery lost over it, null when the battery reads around
 * the sleep do not bracket it closely enough to say (read through the fitted scale when it is pinned).
 */
data class DeepSleep(
    val startMillis: Long,
    val endMillis: Long,
    val dropPercent: Double? = null,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** The drain over the sleep, percentage points lost per hour; null with no [dropPercent]. */
    val percentPerHour: Double? get() = dropPercent?.let { it * HOUR_MILLIS / durationMillis }
}

/**
 * The longest deep sleep in the status log: a run of asleep readings ended by the next awake reading.
 * The log writes a reading on every change and a heartbeat while connected, so a run is a chain of
 * asleep readings no further apart than one heartbeat ([STATUS_CHANGE_MAX_GAP_MILLIS]); a hole in the
 * log (the phone out of range) ends it, since the car may have woken unseen. A run shorter than the
 * projection's minimum span does not count. Ties go to the later sleep.
 *
 * The drain comes from the battery reads either side of the run, when they are parked reads (no charge,
 * no gear, one odometer reading) and lie within [DROP_SLACK_MILLIS] of the run's ends.
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
    val longest =
        asleepRuns(statuses)
            .filter { it.durationMillis >= MIN_SLEEP_SPAN_MILLIS }
            .maxWithOrNull(compareBy({ it.durationMillis }, { it.startMillis }))
            ?: return null
    return longest.copy(dropPercent = dropAround(longest, samples, drives, scale))
}

/** The runs of asleep readings in [statuses], each ended by the awake reading that follows it when one does. */
private fun asleepRuns(statuses: List<StatusSample>): List<DeepSleep> {
    val runs = mutableListOf<DeepSleep>()
    var first: StatusSample? = null
    var last: StatusSample? = null
    for (status in statuses) {
        val held = last
        if (first != null && held != null) {
            val adjacent = status.timestampMillis - held.timestampMillis <= STATUS_CHANGE_MAX_GAP_MILLIS
            if (status.asleep && adjacent) {
                last = status
                continue
            }
            runs += DeepSleep(first.timestampMillis, if (adjacent) status.timestampMillis else held.timestampMillis)
        }
        first = status.takeIf { it.asleep }
        last = first
    }
    val tail = last
    if (first != null && tail != null) runs += DeepSleep(first.timestampMillis, tail.timestampMillis)
    return runs
}

private fun dropAround(
    sleep: DeepSleep,
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    scale: FullRangeScale?,
): Double? {
    val after = samples.indexOfFirst { readAt(it) >= sleep.endMillis }
    if (after < 1) return null
    val a = samples[after - 1]
    val b = samples[after]
    val close = sleep.startMillis - readAt(a) <= DROP_SLACK_MILLIS && readAt(b) - sleep.endMillis <= DROP_SLACK_MILLIS
    val readings = driveReadingsAt(listOf(a, b), drives.filter { it.odometerInHundredthsOfAMile != null })
    val parked = !a.isCharging && !b.isCharging && stayedParked(readings[0], readings[1])
    return if (readAt(a) <= sleep.startMillis && close && parked) (a.soc(scale) - b.soc(scale)).toDouble() else null
}

private fun readAt(sample: BatterySample): Long = sample.readAtMillis ?: sample.timestampMillis

/** A battery read this close to either end of a sleep stands for the level it started and ended at. */
private const val DROP_SLACK_MILLIS = 30 * 60_000L

private const val HOUR_MILLIS = 60 * 60_000L
