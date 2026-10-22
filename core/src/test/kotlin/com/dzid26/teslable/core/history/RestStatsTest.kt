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
    fun `only the selected range is looked at`() {
        // Parked for two days: the day range sees only the last two hours of reads.
        val samples = listOf(sample(0), sample(600), sample(2 * DAY - 120), sample(2 * DAY))
        val drives = samples.map { drive(it.timestampMillis / MINUTE) }
        val now = 2 * DAY * MINUTE
        val all = restStats(samples, drives, HistoryRange.ALL, now)
        assertEquals(2 * DAY * MINUTE, assertNotNull(all.parked).durationMillis)
        val day = restStats(samples, drives, HistoryRange.DAY, now)
        assertEquals(120 * MINUTE, assertNotNull(day.parked).durationMillis)
    }

    @Test
    fun `no logs means nothing to show`() {
        assertEquals(true, restStats(emptyList(), emptyList(), HistoryRange.ALL, 0L).isEmpty)
        assertNull(restStats(emptyList(), emptyList(), HistoryRange.DAY, 0L).deepSleep)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60L
        const val ODOMETER = 1_234_500
    }
}
