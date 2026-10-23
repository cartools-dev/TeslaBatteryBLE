// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * The longest parked, sleep and deep sleep stretches the logs hold, each null when there is none:
 * [parked] sat still (odometer unchanged, not charged), [sleep] was parked with nobody at the car, and
 * [deepSleep] was asleep without a break in the phone's view. [lowestDrain] is the slowest loss
 * between two readings at least two hours apart.
 */
data class RestStats(
    val parked: ParkedSpan? = null,
    val sleep: ParkedSpan? = null,
    val deepSleep: DeepSleep? = null,
    val lowestDrain: ParkedSpan? = null,
) {
    val isEmpty: Boolean get() = parked == null && sleep == null && deepSleep == null && lowestDrain == null
}

/**
 * The [RestStats] of everything the logs hold.
 *
 * @param samples one vehicle's battery samples, oldest first.
 * @param drives the same vehicle's drive samples, oldest first.
 * @param context the fitted scale that refines the drops and the vehicle's status log, oldest first.
 */
fun restStats(
    samples: List<BatterySample>,
    drives: List<DriveSample>,
    context: ProjectionContext = ProjectionContext(),
): RestStats =
    RestStats(
        parked = longestParked(samples, drives, context.scale),
        sleep = longestSleep(samples, drives, context.statuses, context.scale),
        deepSleep = longestDeepSleep(samples, drives, context.statuses, context.scale),
        lowestDrain = lowestDrain(samples, drives, context.scale),
    )
