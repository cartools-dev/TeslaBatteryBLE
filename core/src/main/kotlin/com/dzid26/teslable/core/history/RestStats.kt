// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * The longest parked, sleep and deep sleep stretches the logs hold, each null when there is none:
 * [parked] sat still (odometer unchanged, not charged), [sleep] was parked with nobody at the car, and
 * [deepSleep] was asleep without a break in the phone's view.
 */
data class RestStats(
    val parked: ParkedSpan? = null,
    val sleep: ParkedSpan? = null,
    val deepSleep: DeepSleep? = null,
) {
    val isEmpty: Boolean get() = parked == null && sleep == null && deepSleep == null
}

/**
 * The [RestStats] of what the logs hold within [range] of [nowMillis], the same window the battery chart shows:
 * only the readings inside it are looked at, so a stretch that began before it counts from its first reading
 * inside.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param context the fitted scale that refines the drops and the vehicle's status log, oldest first.
 */
fun restStats(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    range: HistoryRange,
    nowMillis: Long,
    context: ProjectionContext = ProjectionContext(),
): RestStats {
    val cutoff = range.durationMillis?.let { nowMillis - it } ?: Long.MIN_VALUE
    val inSamples = samples.filter { it.timestampMillis >= cutoff }
    val inDrives = drives.filter { it.timestampMillis >= cutoff }
    val inStatuses = context.statuses.filter { it.timestampMillis >= cutoff }
    val scale = context.scale
    return RestStats(
        parked = longestParked(inSamples, inDrives, scale),
        sleep = longestSleep(inSamples, inDrives, inStatuses, scale),
        deepSleep = longestDeepSleep(inSamples, inDrives, inStatuses, scale),
    )
}
