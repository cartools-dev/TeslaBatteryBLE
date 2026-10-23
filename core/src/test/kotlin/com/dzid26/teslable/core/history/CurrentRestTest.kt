// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import com.dzid26.teslable.protocol.ShiftStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CurrentRestTest {
    private fun sample(
        minutes: Long,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
    ) = BatterySample(
        timestampMillis = minutes * MINUTE,
        batteryLevel = 70,
        chargingState = state,
        chargeLimit = null,
    )

    private fun drives(
        samples: List<BatterySample>,
        shift: (BatterySample) -> ShiftStateKind? = { ShiftStateKind.P },
        odometer: (BatterySample) -> Int = { ODOMETER },
    ) = samples.map {
        DriveSample(
            timestampMillis = it.timestampMillis,
            vehicleId = "",
            shiftState = shift(it),
            speed = null,
            power = null,
            odometerInHundredthsOfAMile = odometer(it),
        )
    }

    private fun present(minutes: Long) = StatusSample(minutes * MINUTE, "", asleep = false, userPresent = true, locked = false)

    private fun climate(minutes: Long) = ActivitySample(minutes * MINUTE, "", ActivityKind.CLIMATE, true)

    @Test
    fun `parked since the unbroken run began, and sleeping once idle for an hour`() {
        val samples = listOf(sample(0), sample(100), sample(200), sample(400))
        val now = 405 * MINUTE
        val sleeping = assertNotNull(currentRest(samples, drives(samples), now))
        assertEquals(RestKind.SLEEPING, sleeping.kind)
        assertEquals(0L, sleeping.sinceMillis)
        // Someone in the car in the last gap: parked, not sleeping, and not since the idle run either.
        val parked = assertNotNull(currentRest(samples, drives(samples), now, ProjectionContext(statuses = listOf(present(300)))))
        assertEquals(RestKind.PARKED, parked.kind)
        assertEquals(0L, parked.sinceMillis)
    }

    @Test
    fun `just left is parked, not yet sleeping`() {
        val samples = listOf(sample(0), sample(10))
        val rest = assertNotNull(currentRest(samples, drives(samples), 12 * MINUTE))
        assertEquals(RestKind.PARKED, rest.kind)
    }

    @Test
    fun `the climate running now means parked, and the stretch before a drive does not count`() {
        val samples = listOf(sample(0), sample(100), sample(200), sample(300))
        val moved = drives(samples, odometer = { if (it.timestampMillis < 150 * MINUTE) ODOMETER else ODOMETER + 100 })
        val rest = assertNotNull(currentRest(samples, moved, 305 * MINUTE, ProjectionContext(activity = listOf(climate(302)))))
        assertEquals(RestKind.PARKED, rest.kind)
        assertEquals(200 * MINUTE, rest.sinceMillis)
    }

    @Test
    fun `charging, in gear, no reading or no samples is no rest`() {
        val charging = listOf(sample(0), sample(100, ChargingStateKind.Charging))
        assertNull(currentRest(charging, drives(charging), 101 * MINUTE))
        val driving = listOf(sample(0), sample(100))
        assertNull(currentRest(driving, drives(driving, shift = { ShiftStateKind.D }), 101 * MINUTE))
        assertNull(currentRest(driving, emptyList(), 101 * MINUTE))
        assertNull(currentRest(emptyList(), emptyList(), 0L))
    }

    @Test
    fun `nothing heard for longer than a heartbeat is not known`() {
        val samples = listOf(sample(0), sample(100), sample(200))
        assertNull(currentRest(samples, drives(samples), 200 * MINUTE + 3 * 24 * 60 * MINUTE))
        // A status reading heard recently keeps the state current though the last battery read is old.
        val heard = ProjectionContext(statuses = listOf(StatusSample(1000 * MINUTE, "", asleep = true, userPresent = false, locked = true)))
        assertEquals(RestKind.SLEEPING, assertNotNull(currentRest(samples, drives(samples), 1005 * MINUTE, heard)).kind)
    }

    @Test
    fun `a newer drive reading in gear or at another odometer means not parked`() {
        val samples = listOf(sample(0), sample(100), sample(200))
        val parked = drives(samples)
        val newerInGear = parked + parked.last().copy(timestampMillis = 205 * MINUTE, shiftState = ShiftStateKind.D)
        assertNull(currentRest(samples, newerInGear, 206 * MINUTE))
        val newerMoved = parked + parked.last().copy(timestampMillis = 205 * MINUTE, odometerInHundredthsOfAMile = ODOMETER + 50)
        assertNull(currentRest(samples, newerMoved, 206 * MINUTE))
        val newerParked = parked + parked.last().copy(timestampMillis = 205 * MINUTE)
        assertEquals(RestKind.SLEEPING, assertNotNull(currentRest(samples, newerParked, 206 * MINUTE)).kind)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val ODOMETER = 1_234_500
    }
}
