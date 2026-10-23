// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/** How the car is resting now: [PARKED] sitting still, or [SLEEPING] parked and left alone long enough to count as sleep. */
enum class RestKind { PARKED, SLEEPING }

/** The car's resting state now and when it began: the start of the stretch that leads up to it. */
data class CurrentRest(
    val kind: RestKind,
    val sinceMillis: Long,
)

/**
 * The car's state now by the rules the stats use, or null when it is not resting: charging, in gear, or with no
 * reading of the drive state to say. It is parked when the odometer has not moved since [sinceMillis] (the first
 * read of the unbroken run of [parkedBetween] pairs that ends at the newest read, holes included). It is sleeping
 * when, on top of that, nobody is in it and Sentry and the climate are off now ([idleAt]), and have been for at
 * least an hour (the shortest pair that counts as sleep): a car that has just been left is still awake.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param context the vehicle's status log and its Sentry and climate readings.
 */
fun currentRest(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    nowMillis: Long,
    context: ProjectionContext = ProjectionContext(),
): CurrentRest? {
    val last = samples.lastOrNull()
    val readings = driveReadingsAt(samples, drives.filter { it.odometerInHundredthsOfAMile != null })
    if (last == null || last.isCharging || readings.lastOrNull()?.inGear != false) return null
    val first = trailingRun(samples.lastIndex, floor = 0) { parkedBetween(samples[it - 1], samples[it], readings[it - 1], readings[it]) }
    val idleFirst =
        if (idleAt(nowMillis, context)) {
            trailingRun(samples.lastIndex, floor = first) { idleBetween(samples[it - 1], samples[it], context) }
        } else {
            null
        }
    val sleepingSince = idleFirst?.let { readAt(samples[it]) }?.takeIf { nowMillis - it >= MIN_SLEEP_PAIR_MILLIS }
    return sleepingSince?.let { CurrentRest(RestKind.SLEEPING, it) } ?: CurrentRest(RestKind.PARKED, readAt(samples[first]))
}

/** The index the unbroken run of indexes ending at [from] reaches back to, stopping above [floor], while [accept] says so. */
private fun trailingRun(
    from: Int,
    floor: Int,
    accept: (Int) -> Boolean,
): Int {
    var index = from
    while (index > floor && accept(index)) index--
    return index
}
