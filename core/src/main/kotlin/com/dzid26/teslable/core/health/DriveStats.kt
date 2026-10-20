// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import com.dzid26.teslable.core.history.DrivePoint
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.totalDrop

/**
 * What a stretch of driving used, for the caption under the drive chart. Every part is nullable so a
 * missing input hides a line instead of showing a wrong number (the rule of [tripEstimate]).
 * Distances are in miles; format them with `DistanceUnit` at the display edge.
 */
data class DriveStats(
    /** Miles driven inside the trips of the window; gaps between trips (a charge, a parked stretch) do not count. */
    val milesDriven: Float,
    /** Percent of the battery the trips used. */
    val usedPercent: Float,
    /** Percent per mile driven; null below [MIN_STATS_MILES]. */
    val percentPerMile: Float?,
    /** Energy the trips used, kWh; null until the full range is pinned and the rated constant is known. */
    val energyKwh: Float?,
    /** [energyKwh] per mile driven (the efficiency); null when [energyKwh] or the distance is missing. */
    val kwhPerMile: Float?,
    /** [energyKwh] times the home price, with no charging losses; null without a price. */
    val costAtHomePrice: Float?,
)

/**
 * The stats of [points], one drive window's series (see `drivePoints`).
 *
 * The energy is the percent used, times the miles one percent stands for ([scale]), times
 * [ratedKwhPerMile], so it is an estimate from the learned rated constant, not a metered figure.
 * Cost reuses [chargeSessionCost]. Null when there is no trip with at least two points.
 */
fun driveStats(
    points: List<DrivePoint>,
    scale: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
): DriveStats? {
    val miles =
        points
            .groupBy { it.trip }
            .values
            .sumOf { trip -> trip.last().milesDriven - trip.first().milesDriven }
            .toFloat()
    if (miles <= 0f) return null
    val used = points.totalDrop { it.socPercent }
    val milesPerPercent = scale?.takeIf { it.pinned }?.milesPerPercent?.takeIf { it.isFinite() && it > 0f }
    val constant = ratedKwhPerMile?.takeIf { it.isFinite() && it > 0f }
    val enough = miles >= MIN_STATS_MILES
    val energyKwh =
        if (enough && milesPerPercent != null && constant != null) {
            (used * milesPerPercent * constant).takeIf { it > 0f }
        } else {
            null
        }
    return DriveStats(
        milesDriven = miles,
        usedPercent = used,
        percentPerMile = if (enough && used > 0f) used / miles else null,
        energyKwh = energyKwh,
        kwhPerMile = energyKwh?.let { it / miles },
        costAtHomePrice = chargeSessionCost(energyKwh, pricePerKwh),
    )
}

/** Below this the rates swing on a single percent step, so they stay hidden. */
const val MIN_STATS_MILES = 1f
