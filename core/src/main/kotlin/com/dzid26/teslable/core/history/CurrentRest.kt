// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * What the car is doing now: [CHARGING] or [DRIVING] (in gear), or resting: [PARKED] sitting still, or
 * [POWERED_DOWN] parked and left alone long enough to count as a power down.
 */
enum class RestKind { CHARGING, DRIVING, PARKED, POWERED_DOWN }

/** The car's state now and when it began: the start of the stretch that leads up to it. */
data class CurrentRest(
    val kind: RestKind,
    val sinceMillis: Long,
)

/**
 * The car's state now, or null when it is not known: nothing heard from it for longer than a status heartbeat
 * ([STATUS_CHANGE_MAX_GAP_MILLIS]) before [nowMillis], or no drive reading to say whether it moved. It is [RestKind.CHARGING]
 * when the newest read is charging and [RestKind.DRIVING] when the newest drive reading, even one after the newest battery
 * read, is in gear; each since the unbroken run of such reads began.
 *
 * Otherwise it rests, by the rules the stats use. The newest drive reading must show the car at the odometer the
 * stretch holds; a drive after it, unseen, is not known. It is parked when the odometer has not moved since
 * [CurrentRest.sinceMillis] (the first read of the unbroken run of [parkedBetween] pairs that ends at the newest read,
 * holes included). It is powered down when, on top of that, nobody is in it and Sentry and the climate are off now
 * ([idleAt]), and have been for at least an hour (the shortest pair that counts as a power down): a car that has just
 * been left is still awake.
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
    return if (last == null ||
        stale
    ) {
        null
    } else {
        activeNow(samples, readings, parkedDrives.lastOrNull())
            ?: restNow(samples, readings, parkedDrives.lastOrNull(), nowMillis, context)
    }
}

/** Charging or driving now, with the start of the run of reads that says so; null when the car is doing neither. */
private fun activeNow(
    samples: List<BatterySample>,
    readings: List<DriveSample?>,
    newest: DriveSample?,
): CurrentRest? {
    val last = samples.last()
    return when {
        last.isCharging ->
            CurrentRest(
                RestKind.CHARGING,
                readAt(samples[trailingRun(samples.lastIndex, floor = 0) { samples[it - 1].isCharging }]),
            )
        newest?.inGear == true ->
            CurrentRest(
                RestKind.DRIVING,
                readAt(
                    samples[
                        trailingRun(samples.lastIndex, floor = 0) {
                            readings[it - 1]?.inGear ==
                                true
                        },
                    ],
                ),
            )
        else -> null
    }
}

/** Parked or powered down now, or null when the newest drive readings do not settle where the car is. */
private fun restNow(
    samples: List<BatterySample>,
    readings: List<DriveSample?>,
    newest: DriveSample?,
    nowMillis: Long,
    context: ProjectionContext,
): CurrentRest? {
    if (!settled(samples.last(), readings.lastOrNull(), newest)) return null
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
