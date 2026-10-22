// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParkedSpanTest {
    private fun sample(
        minutes: Long,
        percent: Int = 70,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
        energy: Float? = null,
    ) = BatterySample(
        timestampMillis = minutes * MINUTE,
        batteryLevel = percent,
        chargingState = state,
        chargeLimit = null,
        chargeEnergyAdded = energy,
    )

    private fun drives(
        samples: List<BatterySample>,
        odometer: (BatterySample) -> Int = { ODOMETER },
    ) = samples.map {
        DriveSample(
            timestampMillis = it.timestampMillis,
            vehicleId = "",
            shiftState = null,
            speed = null,
            power = null,
            odometerInHundredthsOfAMile = odometer(it),
        )
    }

    @Test
    fun `holes between reads count when the odometer did not move`() {
        val samples = listOf(sample(0, 70), sample(10, 70), sample(900, 68), sample(910, 68))
        val span = assertNotNull(longestParked(samples, drives(samples)))
        assertEquals(0L, span.startMillis)
        assertEquals(910 * MINUTE, span.endMillis)
        assertEquals(2.0, span.dropPercent)
        assertEquals(2.0 / (910.0 / 60), span.percentPerHour, 1e-9)
    }

    @Test
    fun `a drive splits the stretches and the longer one wins`() {
        val samples = listOf(sample(0), sample(100), sample(200), sample(300), sample(700))
        val moved = drives(samples) { if (it.timestampMillis <= 200 * MINUTE) ODOMETER else ODOMETER + 100 }
        val span = assertNotNull(longestParked(samples, moved))
        assertEquals(300 * MINUTE, span.startMillis)
        assertEquals(700 * MINUTE, span.endMillis)
    }

    @Test
    fun `a session that ran unseen in a hole breaks the stretch`() {
        val samples = listOf(sample(0, 50, energy = 20f), sample(10, 50, energy = 20f), sample(900, 70, energy = 20f))
        val span = assertNotNull(longestParked(samples, drives(samples)))
        assertEquals(10 * MINUTE, span.endMillis)
        val changed = listOf(sample(0, 70, energy = 20f), sample(10, 70, energy = 20f), sample(900, 70, energy = 12f))
        assertEquals(10 * MINUTE, assertNotNull(longestParked(changed, drives(changed))).endMillis)
    }

    @Test
    fun `a charge that has only just begun still counts the stretch before it`() {
        val samples =
            listOf(sample(0, 70, energy = 20f), sample(900, 70, ChargingStateKind.Charging, energy = 0.03f))
        assertEquals(900 * MINUTE, assertNotNull(longestParked(samples, drives(samples))).endMillis)
        val going = listOf(sample(0, 70, energy = 20f), sample(900, 72, ChargingStateKind.Charging, energy = 4f))
        assertNull(longestParked(going, drives(going)))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val ODOMETER = 1_234_500
    }
}
