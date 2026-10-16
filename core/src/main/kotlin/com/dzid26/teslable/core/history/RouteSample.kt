// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/**
 * The active navigation route of one DriveState reading (#207), derived from a
 * logged [BleRecord] like [DriveSample] and on the car's own clock. Present only
 * while the car navigates.
 *
 * This read model keeps the destination name for live display only and leaves the
 * coordinates out. The raw DriveState, destination and coordinates included, is
 * still in the car's `.drive.pblog` (ADR-0008), so the log is where that location
 * data lives. [toString] redacts the name so a log line or a failed assertion
 * never prints it.
 */
data class RouteSample(
    /** The car's own `DriveState.timestamp`. */
    val timestampMillis: Long,
    /** Which vehicle the reading came from, as in [DriveSample.vehicleId]. */
    val vehicleId: String,
    /** `active_route_destination`: for the on-screen card; not copied into diagnostics or exports. */
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
) {
    override fun toString(): String {
        val shownDestination = if (destination != null) "<redacted>" else "null"
        return "RouteSample(timestampMillis=$timestampMillis, vehicleId=$vehicleId, destination=$shownDestination, " +
            "milesToArrival=$milesToArrival, minutesToArrival=$minutesToArrival, " +
            "trafficDelayMinutes=$trafficDelayMinutes, energyAtArrivalPercent=$energyAtArrivalPercent)"
    }
}
