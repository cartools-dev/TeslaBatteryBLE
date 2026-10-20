// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.asleep
import com.dzid26.teslable.protocol.locked
import com.dzid26.teslable.protocol.shiftStateKind
import com.dzid26.teslable.protocol.userPresent

// One-way conversion: BleRecords kept in the history logs become the app's
// read models (BatterySample, StatusSample, DriveSample). The read models are
// derived on load; nothing ever writes a record back from parsed fields — the
// store logs the car's raw reply verbatim, next to the phone's acquisition
// time (ADR-0008). A value that needs a rule (a gear, a charging state, the status flags) comes
// from the shared properties in protocol/StateViews.kt, never re-derived here.

/**
 * The battery sample for a logged charge reply, or null unless the record
 * holds a charge state with a level and the car's own timestamp. The sample
 * sits on that car timestamp: `device_timestamp` never stands in for it (ADR-0006).
 * It also carries `device_timestamp` as [BatterySample.readAtMillis], the phone
 * time the reply was read, for showing a reading's age.
 */
fun BleRecord.toBatterySample(vehicleId: String): BatterySample? =
    charge_state?.toBatterySample(vehicleId)?.copy(readAtMillis = device_timestamp?.toEpochMilli())

/**
 * The status sample for a logged VCSEC reply, or null unless the record holds
 * a status and `device_timestamp`. VCSEC replies carry no time of their own, so the
 * phone's `device_timestamp` is the sample's time.
 */
fun BleRecord.toStatusSample(vehicleId: String): StatusSample? {
    val status = vehicle_status ?: return null
    val time = device_timestamp?.toEpochMilli() ?: return null
    return StatusSample(
        timestampMillis = time,
        vehicleId = vehicleId,
        asleep = status.asleep,
        userPresent = status.userPresent,
        locked = status.locked,
    )
}

/**
 * The drive sample for a logged DriveState reply, or null unless the record
 * holds a drive state with the car's own timestamp. The sample sits on that
 * car timestamp: `device_timestamp` never stands in for it (ADR-0006, ADR-0008).
 */
fun BleRecord.toDriveSample(vehicleId: String): DriveSample? {
    val drive = drive_state ?: return null
    val time = drive.timestamp?.toEpochMilli() ?: return null
    return DriveSample(
        timestampMillis = time,
        vehicleId = vehicleId,
        shiftState = drive.shiftStateKind,
        speed = drive.speed,
        power = drive.power,
        odometerInHundredthsOfAMile = drive.odometer_in_hundredths_of_a_mile,
    )
}

/**
 * The navigation route of a logged DriveState reply, or null unless the record
 * holds a drive state with the car's own timestamp and at least one route field
 * (a car that is not navigating sends none). Like [toDriveSample] it sits on the
 * car's timestamp, never `device_timestamp`.
 */
fun BleRecord.toRouteSample(vehicleId: String): RouteSample? {
    val drive = drive_state
    val time = drive?.timestamp?.toEpochMilli()
    if (drive == null || time == null) return null
    val sample =
        RouteSample(
            timestampMillis = time,
            vehicleId = vehicleId,
            destination = drive.active_route_destination?.takeIf { it.isNotEmpty() },
            milesToArrival = drive.active_route_miles_to_arrival,
            minutesToArrival = drive.active_route_minutes_to_arrival,
            trafficDelayMinutes = drive.active_route_traffic_minutes_delay,
            energyAtArrivalPercent = drive.active_route_energy_at_arrival,
            readAtMillis = device_timestamp?.toEpochMilli(),
            odometerInHundredthsOfAMile = drive.odometer_in_hundredths_of_a_mile,
        )
    val hasRoute =
        sample.destination != null ||
            sample.milesToArrival != null ||
            sample.minutesToArrival != null ||
            sample.energyAtArrivalPercent != null
    return sample.takeIf { hasRoute }
}
