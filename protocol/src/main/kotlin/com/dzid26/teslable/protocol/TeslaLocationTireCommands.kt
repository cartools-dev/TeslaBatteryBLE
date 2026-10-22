// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.protocol

import com.tesla.generated.carserver.server.Action
import com.tesla.generated.carserver.server.GetLocationState
import com.tesla.generated.carserver.server.GetTirePressureState
import com.tesla.generated.carserver.server.GetVehicleData
import com.tesla.generated.carserver.server.Response
import com.tesla.generated.carserver.server.VehicleAction
import com.tesla.generated.carserver.vehicle.LocationState
import com.tesla.generated.carserver.vehicle.TirePressureState

/**
 * The `LocationState` and `TirePressureState` categories of `GetVehicleData`, one per request like
 * [TeslaCommands]. Both replies are returned whole: the history logs them verbatim (ADR-0008).
 */
object TeslaLocationTireCommands {
    /** Asks for the `LocationState`: coordinates, heading and the raw GPS elevation (`geo_elevation`). */
    fun buildLocationStateRequest(): ByteArray =
        Action(
            vehicleAction =
                VehicleAction(
                    getVehicleData = GetVehicleData(getLocationState = GetLocationState()),
                ),
        ).encode()

    fun buildTirePressureStateRequest(): ByteArray =
        Action(
            vehicleAction =
                VehicleAction(
                    getVehicleData = GetVehicleData(getTirePressureState = GetTirePressureState()),
                ),
        ).encode()

    /** The car's raw `LocationState` from a vehicle-data reply, or null when the reply holds none. */
    fun parseLocationState(payload: ByteArray): LocationState? {
        val data = Response.ADAPTER.decode(payload).vehicleData ?: return null
        return data.location_state
    }

    /** The car's raw `TirePressureState` from a vehicle-data reply, or null when the reply holds none. */
    fun parseTirePressureState(payload: ByteArray): TirePressureState? {
        val data = Response.ADAPTER.decode(payload).vehicleData ?: return null
        return data.tire_pressure_state
    }
}
