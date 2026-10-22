// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import kotlin.math.abs

/**
 * One stretch the car sat parked: the odometer unchanged, out of gear and not charging from the first read
 * at [startMillis] to the last at [endMillis], however far apart the reads were. [dropPercent] is what the battery lost.
 */
data class ParkedSpan(
    val startMillis: Long,
    val endMillis: Long,
    val dropPercent: Double,
) {
    val durationMillis: Long get() = endMillis - startMillis

    /** The drain over the stretch, percentage points lost per hour. */
    val percentPerHour: Double get() = dropPercent * HOUR_MILLIS / durationMillis
}

/**
 * The longest stretch of consecutive parked pairs of battery reads: holes in the reads count, since the
 * odometer says the car did not move and [chargedBetween] says it was not plugged in meanwhile. Ties go
 * to the later stretch.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param scale the fitted rated-range scale that refines the drop; null reads the integer levels.
 */
fun longestParked(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    scale: FullRangeScale? = null,
): ParkedSpan? {
    val readings = driveReadingsAt(samples, drives.filter { it.odometerInHundredthsOfAMile != null })
    var longest: ParkedSpan? = null
    var first = 0
    for (index in 1..samples.size) {
        val continues = index < samples.size && parkedBetween(samples[index - 1], samples[index], readings[index - 1], readings[index])
        if (continues) continue
        val span = parkedSpan(samples, first, index - 1, scale)
        if (span != null && span.durationMillis >= (longest?.durationMillis ?: 1L)) longest = span
        first = index
    }
    return longest
}

private fun parkedSpan(
    samples: List<BatterySample>,
    first: Int,
    last: Int,
    scale: FullRangeScale?,
): ParkedSpan? {
    if (last <= first) return null
    val a = samples[first]
    val b = samples[last]
    return ParkedSpan(readAt(a), readAt(b), (a.soc(scale) - b.soc(scale)).toDouble())
}

/** True when the car stayed parked between two consecutive reads: it did not move and was not charged in between. */
internal fun parkedBetween(
    a: BatterySample,
    b: BatterySample,
    readingA: DriveSample?,
    readingB: DriveSample?,
): Boolean = stayedParked(readingA, readingB) && !chargedBetween(a, b)

/**
 * True when the car was plugged in between two reads. A charge running at [a] counts. At [b] it counts unless
 * the session has only just begun (the energy added is still near zero: the owner came to the car and plugged
 * in, so the stretch before it was parked). With no charge showing at [b], a changed energy-added figure that
 * is not near zero, or a level that rose by more than rounding, says a session ran unseen in the hole.
 */
internal fun chargedBetween(
    a: BatterySample,
    b: BatterySample,
): Boolean {
    val before = a.chargeEnergyAdded
    val after = b.chargeEnergyAdded
    val begun = after != null && after <= NEAR_ZERO_KWH
    return when {
        a.isCharging -> true
        b.isCharging -> !begun
        else ->
            b.batteryLevel - a.batteryLevel >= LEVEL_RISE_POINTS ||
                (before != null && after != null && abs(after - before) > ENERGY_EPSILON_KWH && !begun)
    }
}

/**
 * True when nobody was seen at the car between two reads: no status reading strictly inside the gap says
 * unlocked or someone present. A gap with no status readings at all counts, since nothing contradicts it.
 *
 * @param statuses the vehicle's status log, oldest first.
 */
internal fun unattendedBetween(
    a: BatterySample,
    b: BatterySample,
    statuses: List<StatusSample>,
): Boolean {
    val from = readAt(a)
    val to = readAt(b)
    var low = 0
    var high = statuses.size
    while (low < high) {
        val middle = (low + high) / 2
        if (statuses[middle].timestampMillis > from) high = middle else low = middle + 1
    }
    var index = low
    while (index < statuses.size && statuses[index].timestampMillis < to) {
        if (!statuses[index].locked || statuses[index].userPresent) return false
        index++
    }
    return true
}

/** A charge session this young at a read has only just begun: the energy added is still near zero. */
private const val NEAR_ZERO_KWH = 0.1f

/** A level up by this many points while not charging is a session, not a rounding blip. */
private const val LEVEL_RISE_POINTS = 2

/** Energy-added figures closer than this are the same reading. */
private const val ENERGY_EPSILON_KWH = 0.01f

private const val HOUR_MILLIS = 60 * 60_000L
