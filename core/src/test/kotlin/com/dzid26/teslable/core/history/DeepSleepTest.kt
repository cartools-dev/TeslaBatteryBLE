// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeepSleepTest {
    private fun sample(
        minutes: Long,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
    ) = BatterySample(
        timestampMillis = minutes * MINUTE,
        batteryLevel = 70,
        chargingState = state,
        chargeLimit = null,
    )

    private fun status(
        minutes: Long,
        asleep: Boolean = true,
    ) = StatusSample(
        timestampMillis = minutes * MINUTE,
        vehicleId = "",
        asleep = asleep,
        userPresent = false,
        locked = true,
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
    fun `the longest slept-through gap wins`() {
        val samples = listOf(sample(0), sample(120), sample(180), sample(780))
        val statuses = listOf(status(60), status(500))
        val sleep = assertNotNull(longestDeepSleep(samples, drives(samples), statuses))
        assertEquals(180 * MINUTE, sleep.startMillis)
        assertEquals(780 * MINUTE, sleep.endMillis)
        assertEquals(600 * MINUTE, sleep.durationMillis)
    }

    @Test
    fun `a gap with an awake status inside was not a deep sleep`() {
        val samples = listOf(sample(0), sample(600))
        val statuses = listOf(status(60), status(90, asleep = false))
        assertNull(longestDeepSleep(samples, drives(samples), statuses))
    }

    @Test
    fun `a gap shorter than the projection minimum does not count`() {
        val samples = listOf(sample(0), sample(5))
        assertNull(longestDeepSleep(samples, drives(samples), listOf(status(2))))
    }

    @Test
    fun `a gap with a drive or a charge in it does not count`() {
        val samples = listOf(sample(0), sample(600))
        val moved = drives(samples) { if (it.timestampMillis == 0L) ODOMETER else ODOMETER + 100 }
        assertNull(longestDeepSleep(samples, moved, listOf(status(60))))
        val charging = listOf(sample(0), sample(600, ChargingStateKind.Charging))
        assertNull(longestDeepSleep(charging, drives(charging), listOf(status(60))))
    }

    @Test
    fun `no status log means no deep sleep`() {
        val samples = listOf(sample(0), sample(600))
        assertNull(longestDeepSleep(samples, drives(samples), emptyList()))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val ODOMETER = 1_234_500
    }
}
