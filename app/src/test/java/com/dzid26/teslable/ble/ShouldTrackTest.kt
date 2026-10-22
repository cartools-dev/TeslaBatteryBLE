// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShouldTrackTest {
    private val address = "18:12:ED:33:33:80"

    private fun state(
        phase: ConnectionPhase,
        keySlot: Int? = null,
        tracking: Boolean = true,
    ) = BleUiState(
        trackingEnabled = tracking,
        vehicles = listOf(Vehicle(bleName = "S1", address = address, keySlot = keySlot)),
        connections = mapOf(address to TeslaConnection(address = address, name = "S1", phase = phase)),
    )

    @Test
    fun startsWhilePairedCarIsConnecting() {
        listOf(ConnectionPhase.CONNECTING, ConnectionPhase.CONNECTED, ConnectionPhase.DISCOVERING).forEach {
            assertTrue(state(it, keySlot = 3).shouldTrack())
        }
    }

    @Test
    fun ignoresUnpairedCarWhileConnecting() {
        assertFalse(state(ConnectionPhase.CONNECTING).shouldTrack())
    }

    @Test
    fun ignoresIdleAndFailedLinks() {
        listOf(ConnectionPhase.IDLE, ConnectionPhase.FAILED, ConnectionPhase.DISCONNECTED).forEach {
            assertFalse(state(it, keySlot = 3).shouldTrack())
        }
    }

    @Test
    fun staysOffWhenTrackingIsDisabled() {
        assertFalse(state(ConnectionPhase.CONNECTING, keySlot = 3, tracking = false).shouldTrack())
    }
}
