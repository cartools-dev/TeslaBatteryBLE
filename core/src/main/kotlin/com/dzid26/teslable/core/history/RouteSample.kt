// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * The active navigation route of one DriveState reading (#207), derived from a
 * logged [BleRecord] like [DriveSample] and on the car's own clock. Present only
 * while the car navigates.
 *
 * The destination's coordinates are left out on purpose: the name is shown live
 * and neither is stored or exported (location data).
 */
data class RouteSample(
    /** The car's own `DriveState.timestamp`. */
    val timestampMillis: Long,
    /** Which vehicle the reading came from, as in [DriveSample.vehicleId]. */
    val vehicleId: String,
    /** `active_route_destination`: shown on screen only, never logged or exported. */
    val destination: String?,
    /** `active_route_miles_to_arrival`: remaining route distance in miles. */
    val milesToArrival: Float?,
    /** `active_route_minutes_to_arrival`: remaining time. */
    val minutesToArrival: Float?,
    /** `active_route_traffic_minutes_delay`: traffic delay included in the remaining time. */
    val trafficDelayMinutes: Float?,
    /**
     * `active_route_energy_at_arrival`: the car's predicted battery level on arrival.
     * Read as a percent (Fleet Telemetry maps it to `ExpectedEnergyPercentAtTripArrival`);
     * the unit is not documented in the proto and still needs a real-car log to confirm.
     */
    val energyAtArrivalPercent: Float?,
)
