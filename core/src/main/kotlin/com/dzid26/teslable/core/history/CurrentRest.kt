// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/** How the car is resting now: [PARKED] sitting still, or [POWERED_DOWN] parked and left alone long enough to count as a power down. */
enum class RestKind { PARKED, POWERED_DOWN }

/** The car's resting state now and when it began: the start of the stretch that leads up to it. */
data class CurrentRest(
    val kind: RestKind,
    val sinceMillis: Long,
)

/**
 * The car's state now by the rules the stats use, or null when it is not resting or not known: charging, in gear,
 * with no reading of the drive state to say, or with nothing heard from the car for longer than a status heartbeat
 * ([STATUS_CHANGE_MAX_GAP_MILLIS]) before [nowMillis]. The newest drive reading, even one after the newest battery
 * read, must show the car out of gear at the odometer the stretch holds; a drive after it, unseen, is not known.
 *
 * It is parked when the odometer has not moved since [CurrentRest.sinceMillis] (the first read of the unbroken run
 * of [parkedBetween] pairs that ends at the newest read, holes included). It is powered down when, on top of that,
 * nobody is in it and Sentry and the climate are off now ([idleAt]), and have been for at least an hour (the
 * shortest pair that counts as a power down): a car that has just been left is still awake.
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
    val parkedDrives = drives.filter { it.odometerInHundredthsOfAMile != null }
    val readings = driveReadingsAt(samples, parkedDrives)
    val stale = nowMillis - heardFromAt(samples, context) > STATUS_CHANGE_MAX_GAP_MILLIS
    if (last == null || stale || !settled(last, readings.lastOrNull(), parkedDrives.lastOrNull())) return null
    val first = trailingRun(samples.lastIndex, floor = 0) { parkedBetween(samples[it - 1], samples[it], readings[it - 1], readings[it]) }
    val idleFirst =
        if (idleAt(nowMillis, context)) {
            trailingRun(samples.lastIndex, floor = first) { idleBetween(samples[it - 1], samples[it], context) }
        } else {
            null
        }
    val poweredDownSince = idleFirst?.let { readAt(samples[it]) }?.takeIf { nowMillis - it >= MIN_SLEEP_PAIR_MILLIS }
    return poweredDownSince?.let { CurrentRest(RestKind.POWERED_DOWN, it) } ?: CurrentRest(RestKind.PARKED, readAt(samples[first]))
}

/** The newest moment anything was heard from the car: its newest battery read or status reading. */
private fun heardFromAt(
    samples: List<BatterySample>,
    context: ProjectionContext,
): Long = maxOf(samples.lastOrNull()?.let { readAt(it) } ?: 0L, context.statuses.lastOrNull()?.timestampMillis ?: 0L)

/** True when the newest read is not charging and the newest drive reading is out of gear at the read's odometer. */
private fun settled(
    last: BatterySample,
    atLast: DriveSample?,
    newest: DriveSample?,
): Boolean =
    !last.isCharging &&
        atLast?.inGear == false &&
        newest?.inGear == false &&
        newest.odometerInHundredthsOfAMile == atLast.odometerInHundredthsOfAMile

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
