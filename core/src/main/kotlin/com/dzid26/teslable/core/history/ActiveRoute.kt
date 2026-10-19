// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * The route the car is navigating right now, or null (#207).
 *
 * [latestRoute] is the car's newest drive reply, if it carried route fields
 * (the store drops a car's route as soon as a reply arrives without one, so a
 * finished or cancelled trip is gone). It counts as live while it still has a
 * distance left and was read recently, judged on the phone's clock
 * ([RouteSample.readAtMillis], else the car's own timestamp) against
 * [nowMillis], the phone's wall clock: the car answers a drive read every few
 * minutes while it navigates, so an older reading is a trip that ended with
 * the phone out of range.
 */
fun activeRoute(
    latestRoute: RouteSample?,
    nowMillis: Long,
): RouteSample? =
    latestRoute?.takeIf { route ->
        route.milesToArrival != null &&
            nowMillis - (route.readAtMillis ?: route.timestampMillis) <= ROUTE_FRESH_MILLIS
    }

/** A route reading older than this is a finished trip: the car answers a drive read every few minutes while it navigates. */
private const val ROUTE_FRESH_MILLIS = 10 * 60_000L
