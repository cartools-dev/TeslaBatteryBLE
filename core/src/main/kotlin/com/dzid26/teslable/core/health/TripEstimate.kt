// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.RouteSample

/**
 * What the active route will take (#207). Every part is nullable so a missing
 * input hides a row instead of showing a wrong number (the rule of
 * [chargeSessionCost]). Distances and ranges are in miles; format them with
 * `DistanceUnit` at the display edge.
 */
data class TripEstimate(
    /** Remaining route distance in miles. */
    val milesRemaining: Float?,
    /** The car's predicted battery level on arrival, percent. */
    val arrivalPercent: Float?,
    /** Rated miles left on arrival; null until the full-range scale is good enough (see [goodEnoughForTrips]). */
    val arrivalRatedMiles: Float?,
    /** Energy the rest of the trip uses, kWh; null when it is not above zero. */
    val energyKwh: Float?,
    /** [energyKwh] times the home price, with no charging losses; null without a price. */
    val costAtHomePrice: Float?,
)

/**
 * The predicted trip numbers from the car's own route reading.
 *
 * Arrival rated miles come from the arrival percent through the learned scale
 * (`offset + percent / 100 * fullRange`), so they stay consistent with the SOC
 * shown on the hero. Energy is `(current - arrival) rated miles * k`, where
 * [ratedKwhPerMile] is the learned rated constant; there is no pack capacity in
 * the protocol. Cost reuses [chargeSessionCost].
 */
fun tripEstimate(
    route: RouteSample,
    currentRatedMiles: Float?,
    fullRange: FullRangeScale?,
    ratedKwhPerMile: Float?,
    pricePerKwh: Float?,
): TripEstimate {
    val miles = route.milesToArrival?.takeIf { it.isFinite() && it >= 0f }
    val percent = route.energyAtArrivalPercent?.takeIf { it.isFinite() && it in 0f..PERCENT_MAX }
    val arrivalMiles =
        if (percent != null && fullRange != null && fullRange.goodEnoughForTrips()) {
            fullRange.offsetMiles + percent / PERCENT_MAX * fullRange.fullRangeMiles
        } else {
            null
        }
    val usedMiles =
        if (arrivalMiles != null && currentRatedMiles != null && currentRatedMiles.isFinite()) {
            currentRatedMiles - arrivalMiles
        } else {
            null
        }
    val constant = ratedKwhPerMile?.takeIf { it.isFinite() && it > 0f }
    val energyKwh = if (usedMiles != null && constant != null) (usedMiles * constant).takeIf { it > 0f } else null
    return TripEstimate(
        milesRemaining = miles,
        arrivalPercent = percent,
        arrivalRatedMiles = arrivalMiles,
        energyKwh = energyKwh,
        costAtHomePrice = chargeSessionCost(energyKwh, pricePerKwh),
    )
}

/**
 * Whether this scale is accurate enough to turn an arrival level into rated miles and energy. This is a
 * looser bar than [FullRangeScale.pinned], which guards the tenths of a percent the hero draws: a trip's
 * energy is a few kWh, so a slope that is off by a couple of percent does not matter, and the scale's
 * values move smoothly, where the pinned flag can flip on a single reading near its threshold.
 */
fun FullRangeScale.goodEnoughForTrips(): Boolean = standardErrorMiles <= fullRangeMiles * TRIP_MAX_SCALE_ERROR

private const val TRIP_MAX_SCALE_ERROR = 0.03f
private const val PERCENT_MAX = 100f
