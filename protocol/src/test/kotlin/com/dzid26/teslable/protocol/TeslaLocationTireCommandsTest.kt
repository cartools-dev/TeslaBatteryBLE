// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.protocol

import com.tesla.generated.carserver.server.Action
import com.tesla.generated.carserver.server.GetLocationState
import com.tesla.generated.carserver.server.GetTirePressureState
import com.tesla.generated.carserver.server.GetVehicleData
import com.tesla.generated.carserver.server.Response
import com.tesla.generated.carserver.vehicle.ChargeState
import com.tesla.generated.carserver.vehicle.LocationState
import com.tesla.generated.carserver.vehicle.TirePressureState
import com.tesla.generated.carserver.vehicle.VehicleData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The LocationState and TirePressureState request and reply helpers. Like the other state
 * categories they have no Go vector: requests are checked by decoding them, replies by parsing
 * messages built from Tesla's protos.
 */
class TeslaLocationTireCommandsTest {
    private val location = LocationState(latitude = 52.25f, longitude = 21.0f, heading = 90, geo_elevation = 112.5f)
    private val tires = TirePressureState(tpms_pressure_fl = 2.9f, tpms_pressure_fr = 2.85f)

    @Test
    fun `the location request asks for the location state and no other category`() {
        val request = Action.ADAPTER.decode(TeslaLocationTireCommands.buildLocationStateRequest())
        assertEquals(GetVehicleData(getLocationState = GetLocationState()), request.vehicleAction?.getVehicleData)
    }

    @Test
    fun `the tire pressure request asks for the tire pressure state and no other category`() {
        val request = Action.ADAPTER.decode(TeslaLocationTireCommands.buildTirePressureStateRequest())
        assertEquals(GetVehicleData(getTirePressureState = GetTirePressureState()), request.vehicleAction?.getVehicleData)
    }

    @Test
    fun `parses the whole location state, elevation included`() {
        val payload = Response(vehicleData = VehicleData(location_state = location)).encode()
        val parsed = TeslaLocationTireCommands.parseLocationState(payload)
        assertEquals(location, parsed)
        assertEquals(112.5f, parsed!!.geo_elevation)
    }

    @Test
    fun `parses the whole tire pressure state`() {
        val payload = Response(vehicleData = VehicleData(tire_pressure_state = tires)).encode()
        assertEquals(tires, TeslaLocationTireCommands.parseTirePressureState(payload))
    }

    @Test
    fun `a reply for another category parses to null`() {
        val chargeReply = Response(vehicleData = VehicleData(charge_state = ChargeState(battery_level = 50))).encode()
        assertNull(TeslaLocationTireCommands.parseLocationState(chargeReply))
        assertNull(TeslaLocationTireCommands.parseTirePressureState(chargeReply))
        assertNull(TeslaLocationTireCommands.parseLocationState(Response().encode()))
        assertNull(TeslaLocationTireCommands.parseTirePressureState(Response().encode()))
    }
}
