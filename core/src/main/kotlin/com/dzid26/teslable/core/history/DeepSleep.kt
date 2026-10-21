// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/** What the battery lost while parked around a sleep: [percent] points over [spanMillis] between the two reads that bracket it. */
data class SleepDrop(
    val percent: Double,
    val spanMillis: Long,
)

/**
 * One deep sleep: the status log first said asleep at [startMillis] and said awake (or was last heard asleep)
 * at [endMillis]. [drop] is what the battery lost around it, null when no parked reads bracket it closely.
 */
data class DeepSleep(
    val startMillis: Long,
    val endMillis: Long,
    val drop: SleepDrop? = null,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** The drain around the sleep, percentage points lost per hour; null with no [drop]. */
    val percentPerHour: Double? get() = drop?.let { it.percent * HOUR_MILLIS / it.spanMillis }
}

/**
 * The longest of [deepSleeps]. Ties go to the later sleep.
 *
 * @see deepSleeps for the parameters.
 */
fun longestDeepSleep(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    statuses: List<StatusSample>,
    scale: FullRangeScale? = null,
): DeepSleep? = deepSleeps(samples, drives, statuses, scale).maxWithOrNull(compareBy({ it.durationMillis }, { it.startMillis }))

/**
 * Every deep sleep in the status log, the one definition of "the car slept" the app uses: a run of asleep
 * readings, ended by the next awake reading when one follows within a heartbeat. The log writes a reading on
 * every change and a heartbeat while the phone is connected, so a hole in it (the phone out of range or
 * reconnecting) is bridged when asleep readings stand either side, but an awake reading after a hole only
 * ends the run at the last asleep reading, since the real wake time is unknown. A run shorter than
 * [MIN_SLEEP_SPAN_MILLIS] is not a deep sleep.
 *
 * The drain is read from the last battery read at or before the sleep and the first at or after it, when both
 * lie within [DROP_SLACK_MILLIS] of the sleep's ends, neither is charging and the odometer and gear say the car
 * stayed parked. A sleep without such reads has no drain and feeds no rate.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param statuses the same vehicle's status log, oldest first.
 * @param scale the fitted rated-range scale that refines the drop; null reads the integer levels.
 */
fun deepSleeps(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    statuses: List<StatusSample>,
    scale: FullRangeScale? = null,
): List<DeepSleep> {
    val parkedDrives = drives.filter { it.odometerInHundredthsOfAMile != null }
    return asleepRuns(statuses)
        .filter { it.durationMillis >= MIN_SLEEP_SPAN_MILLIS }
        .map { it.copy(drop = dropAround(it, samples, parkedDrives, scale)) }
}

/** The runs of asleep readings in [statuses], each ended by the awake reading that follows it when one does. */
private fun asleepRuns(statuses: List<StatusSample>): List<DeepSleep> {
    val runs = mutableListOf<DeepSleep>()
    var first: StatusSample? = null
    var last: StatusSample? = null
    for (status in statuses) {
        val held = last
        if (first != null && held != null) {
            if (status.asleep) {
                last = status
                continue
            }
            val adjacent = status.timestampMillis - held.timestampMillis <= STATUS_CHANGE_MAX_GAP_MILLIS
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
    parkedDrives: List<DriveSample>,
    scale: FullRangeScale?,
): SleepDrop? {
    val after = firstReadAtOrAfter(samples, sleep.endMillis)
    if (after < 1 || after >= samples.size) return null
    val a = samples[after - 1]
    val b = samples[after]
    val close = sleep.startMillis - readAt(a) in 0..DROP_SLACK_MILLIS && readAt(b) - sleep.endMillis <= DROP_SLACK_MILLIS
    val readings = driveReadingsAt(listOf(a, b), parkedDrives)
    val parked = !a.isCharging && !b.isCharging && stayedParked(readings[0], readings[1])
    return if (close && parked) SleepDrop((a.soc(scale) - b.soc(scale)).toDouble(), readAt(b) - readAt(a)) else null
}

/** The index of the first sample read at or after [millis], or the size when none was; samples are oldest first. */
private fun firstReadAtOrAfter(
    samples: List<BatterySample>,
    millis: Long,
): Int {
    var low = 0
    var high = samples.size
    while (low < high) {
        val middle = (low + high) / 2
        if (readAt(samples[middle]) >= millis) high = middle else low = middle + 1
    }
    return low
}

private fun readAt(sample: BatterySample): Long = sample.readAtMillis ?: sample.timestampMillis

/** Less sleep than this is too short to tell from a pause between reads; a shorter drain span is too noisy to extrapolate. */
internal const val MIN_SLEEP_SPAN_MILLIS = 10 * 60_000L

/** A battery read this close to either end of a sleep stands for the level it started and ended at. */
private const val DROP_SLACK_MILLIS = 30 * 60_000L

private const val HOUR_MILLIS = 60 * 60_000L
