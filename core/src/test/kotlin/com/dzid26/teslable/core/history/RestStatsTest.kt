// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RestStatsTest {
    private fun sample(minutes: Long) =
        BatterySample(
            timestampMillis = minutes * MINUTE,
            batteryLevel = 70,
            chargingState = ChargingStateKind.Disconnected,
            chargeLimit = null,
        )

    private fun drive(minutes: Long) =
        DriveSample(
            timestampMillis = minutes * MINUTE,
            vehicleId = "",
            shiftState = null,
            speed = null,
            power = null,
            odometerInHundredthsOfAMile = ODOMETER,
        )

    @Test
    fun `the whole log is looked at`() {
        val samples = listOf(sample(0), sample(600), sample(2 * DAY - 120), sample(2 * DAY))
        val drives = samples.map { drive(it.timestampMillis / MINUTE) }
        assertEquals(2 * DAY * MINUTE, assertNotNull(restStats(samples, drives).parked).durationMillis)
    }

    @Test
    fun `no logs means nothing to show`() {
        assertEquals(true, restStats(emptyList(), emptyList()).isEmpty)
        assertNull(restStats(emptyList(), emptyList()).deepSleep)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60L
        const val ODOMETER = 1_234_500
    }
}
