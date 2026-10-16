// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.core.protocol.ShiftStateKind
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where a parked car's battery is heading at its parked drain rate over the range. */
data class DischargeProjection(
    /** The latest sample, where the projected line starts. */
    val from: BatterySample,
    /** Percent projected one horizon after the last sample. */
    val projectedPercent: Int,
    /** Wall-clock time the projection refers to. */
    val projectedAtMillis: Long,
    /** How far ahead the projection looks: half of the selected range. */
    val horizonMillis: Long,
    /** The parked drain rate the line follows, in percentage points lost per hour. */
    val percentPerHour: Double,
    /** The level the line starts at: sub-percent from the fitted scale when it is pinned. */
    val fromSoc: Float = from.batteryLevel.toFloat(),
    /** The unrounded level one horizon ahead; [projectedPercent] is this, rounded. */
    val projectedSoc: Float = projectedPercent.toFloat(),
    /** The lower edge of the beam at the horizon: two standard errors of the rate; null when the fit is too thin. */
    val lowSoc: Float? = null,
    /** The upper edge of the beam at the horizon; null like [lowSoc]. */
    val highSoc: Float? = null,
) {
    /** The beam's two edges together; null when the fit is too thin for one. */
    val beam: Pair<Float, Float>?
        get() {
            val low = lowSoc ?: return null
            val high = highSoc ?: return null
            return low to high
        }
}

/**
 * Projects the parked drain: the drain rate of the parked stretches in the
 * selected range is carried half the range forward, matching the chart's
 * expanded x-axis.
 *
 * Only parked pairs of consecutive battery samples feed the rate. A pair is
 * parked when neither sample is charging, neither is in gear (R, N or D: a
 * car stopped at a light keeps its odometer but is mid-drive), and the
 * odometer did not move between them. Each sample's drive reading is the
 * [DriveSample] nearest to it in time, within [DRIVE_MATCH_MILLIS] (a drive
 * read follows every charge read). A sample with no such reading is not known to be parked, so
 * its pairs are left out. A long gap with no readings still counts when the
 * odometer is unchanged across it: the overnight drop up to the wake-up
 * reading is exactly the drain this projects, while a drive no phone saw
 * shows up as an odometer change and is left out.
 *
 * The rate is the summed signed change over the summed duration of the
 * parked pairs, so a rounding blip that drops and recovers while parked
 * cancels out instead of inflating the drop. The change is read on the rated
 * range through the fitted [scale] when it is pinned, so a pair of readings
 * is a fraction of a percent, not a whole level; without it the integer
 * levels stand in. The beam is two standard errors of that rate: the spread
 * of the pairs around it, never less than the readings' own resolution.
 *
 * While charging it still projects: the line shows where the battery would
 * head if unplugged now, next to the charge projection. Returns null once the
 * whole projection lies in the past (the last sample is older than the
 * horizon), when the range holds less than [MIN_RATE_SPAN_MILLIS] of parked
 * time, or when the summed drop is not positive: a rising or flat line would
 * make the rate meaningless.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param rangeMillis the selected range: its samples feed the rate, and the projection looks half of it ahead.
 * @param scale the fitted rated-range scale, or null to read the integer level.
 */
fun dischargeProjection(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    rangeMillis: Long,
    nowMillis: Long,
    scale: FullRangeScale? = null,
): DischargeProjection? {
    val last = samples.lastOrNull() ?: return null
    val horizon = rangeMillis / 2
    if (nowMillis - last.timestampMillis > horizon) return null

    val windowed = samples.filter { it.timestampMillis >= last.timestampMillis - rangeMillis }
    val parked = parkedDrain(windowed, drives.filter { it.odometerInHundredthsOfAMile != null }, scale)
    if (parked.millis < MIN_RATE_SPAN_MILLIS || parked.drop <= 0.0) return null

    val fromSoc = last.soc(scale)
    val ratePerMilli = parked.drop / parked.millis
    val projectedSoc = (fromSoc - ratePerMilli * horizon).toFloat().coerceIn(0f, FULL_PERCENT)
    val reach = 2.0 * parked.errorPerMilli * horizon
    val hasBeam = parked.pairs >= MIN_BEAM_PAIRS
    return DischargeProjection(
        from = last,
        projectedPercent = projectedSoc.roundToInt(),
        projectedAtMillis = last.timestampMillis + horizon,
        horizonMillis = horizon,
        percentPerHour = ratePerMilli * HOUR,
        fromSoc = fromSoc,
        projectedSoc = projectedSoc,
        lowSoc = if (hasBeam) (projectedSoc - reach).toFloat().coerceIn(0f, FULL_PERCENT) else null,
        highSoc = if (hasBeam) (projectedSoc + reach).toFloat().coerceIn(0f, FULL_PERCENT) else null,
    )
}

/** The summed signed drop (percent) and duration of the parked pairs, with their spread. */
private class ParkedDrain(
    val drop: Double,
    val millis: Long,
    val pairs: Int,
    /** Sum of each pair's squared residual from the mean rate, floored at the readings' resolution. */
    val squaredResiduals: Double,
) {
    /** One standard error of the rate, percent per millisecond; the resolution floor is already inside the sum. */
    val errorPerMilli: Double get() = sqrt(squaredResiduals) / millis
}

/** Sums the parked pairs of [samples]; [drives] all carry an odometer and are oldest first. */
private fun parkedDrain(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    scale: FullRangeScale?,
): ParkedDrain {
    val drops = mutableListOf<Double>()
    val spans = mutableListOf<Long>()
    val readings = driveReadingsAt(samples, drives)
    for (index in 1 until samples.size) {
        val a = samples[index - 1]
        val b = samples[index]
        val readingA = readings[index - 1]
        val readingB = readings[index]
        val stayed =
            readingA != null &&
                readingB != null &&
                readingA.odometerInHundredthsOfAMile == readingB.odometerInHundredthsOfAMile &&
                !readingA.inGear &&
                !readingB.inGear
        if (stayed && !a.isCharging && !b.isCharging) {
            drops += (a.soc(scale) - b.soc(scale)).toDouble()
            spans += b.timestampMillis - a.timestampMillis
        }
    }
    val drop = drops.sum()
    val millis = spans.sum()
    if (millis <= 0L) return ParkedDrain(drop, millis, drops.size, 0.0)
    val rate = drop / millis
    // A pair differs by two readings, each uniformly rounded: the floor says
    // no spread can be smaller than that, so a handful of tidy pairs cannot
    // claim a certainty the readings do not have.
    val floor = pairResolution(scale)
    var squared = 0.0
    for (index in drops.indices) {
        val residual = drops[index] - rate * spans[index]
        squared += maxOf(residual * residual, floor * floor)
    }
    return ParkedDrain(drop, millis, drops.size, squared)
}

/** Standard deviation of one pair's drop, percent: two uniformly rounded readings. */
private fun pairResolution(scale: FullRangeScale?): Double {
    val resolutionPercent =
        if (scale != null && scale.pinned) {
            (RANGE_RESOLUTION_MILES / scale.milesPerPercent).toDouble()
        } else {
            LEVEL_RESOLUTION_PERCENT
        }
    return resolutionPercent / UNIFORM_DIVISOR * TWO_READINGS
}

/** In R, N or D: the car is being driven even if the odometer has not ticked yet. */
private val DriveSample.inGear: Boolean
    get() = shiftState == ShiftStateKind.R || shiftState == ShiftStateKind.N || shiftState == ShiftStateKind.D

/**
 * The drive sample nearest to each battery sample, or null when none lies
 * within [DRIVE_MATCH_MILLIS]. One sweep, since both lists are oldest first.
 */
private fun driveReadingsAt(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
): List<DriveSample?> {
    var next = 0
    return samples.map { sample ->
        // Advance to the first drive sample after this battery sample; the
        // nearest one is either it or the one just before.
        while (next < drives.size && drives[next].timestampMillis <= sample.timestampMillis) next++
        listOfNotNull(drives.getOrNull(next - 1), drives.getOrNull(next))
            .minByOrNull { abs(it.timestampMillis - sample.timestampMillis) }
            ?.takeIf { abs(it.timestampMillis - sample.timestampMillis) <= DRIVE_MATCH_MILLIS }
    }
}

/** A full battery, percent. */
private const val FULL_PERCENT = 100f

/** A beam needs this many parked pairs behind the rate. */
private const val MIN_BEAM_PAIRS = 3

/** The car's rated-range step: 0.0816 mi, as seen in the logs. */
private const val RANGE_RESOLUTION_MILES = 0.082f

/** The displayed level steps in whole percent. */
private const val LEVEL_RESOLUTION_PERCENT = 1.0

/** sqrt(12): the standard deviation of a uniform step is the step over this. */
private const val UNIFORM_DIVISOR = 3.4641016

/** sqrt(2): a pair differs two independent readings. */
private const val TWO_READINGS = 1.4142136

private const val MINUTE = 60_000L

private const val HOUR = 60 * MINUTE

/** Less parked time than this makes the rate too noisy to extrapolate. */
private const val MIN_RATE_SPAN_MILLIS = 10 * MINUTE

/** A drive sample this close to a battery sample gives its odometer; a drive read follows each charge read. */
private const val DRIVE_MATCH_MILLIS = 2 * MINUTE
