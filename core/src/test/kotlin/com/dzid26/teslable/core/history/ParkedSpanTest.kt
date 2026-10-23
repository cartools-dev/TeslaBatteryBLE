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

    @Test
    fun `sleep is the parked stretch with nobody seen at the car`() {
        val samples = listOf(sample(0), sample(100), sample(200), sample(300), sample(700))
        val statuses = listOf(status(150, attended = true), status(500))
        val span = assertNotNull(longestSleep(samples, drives(samples), ProjectionContext(statuses = statuses)))
        assertEquals(200 * MINUTE, span.startMillis)
        assertEquals(700 * MINUTE, span.endMillis)
        assertEquals(700 * MINUTE, assertNotNull(longestParked(samples, drives(samples))).endMillis)
    }

    @Test
    fun `the state carried in from before the gap counts`() {
        val a = sample(0)
        val b = sample(1)
        val before = status(-5, attended = true)
        // No status inside a ten-second pair: the unlocked, present reading just before it still stands.
        assertEquals(false, unattendedBetween(a, b, ProjectionContext(statuses = listOf(before))))
        assertEquals(true, unattendedBetween(a, b, ProjectionContext(statuses = listOf(status(-5)))))
        // A carried reading older than a heartbeat is stale: the phone was away, nothing contradicts it.
        assertEquals(true, unattendedBetween(a, b, ProjectionContext(statuses = listOf(status(-120, attended = true)))))
        assertEquals(true, unattendedBetween(a, b, ProjectionContext()))
    }

    @Test
    fun `the lowest drain is the slowest pair of reads six hours or more apart`() {
        val samples = listOf(sample(0, 70), sample(10, 69), sample(400, 69), sample(1000, 68), sample(1100, 68))
        val span = assertNotNull(lowestDrain(samples, drives(samples)))
        // 10 -> 400 holds the level (no loss); 400 -> 1000 lost a point; the pairs around them are under six hours.
        assertEquals(10 * MINUTE, span.startMillis)
        assertEquals(400 * MINUTE, span.endMillis)
        assertNull(lowestDrain(listOf(sample(0, 70), sample(300, 69)), drives(listOf(sample(0), sample(300)))))
    }

    @Test
    fun `a pair starting at a read whose usable level lags the level is left out`() {
        val samples = listOf(sample(0, 70).copy(usableLevel = 69), sample(400, 69))
        assertNull(lowestDrain(samples, drives(samples)))
        assertNull(longestParked(samples, drives(samples)))
        val steady = listOf(sample(0, 70), sample(300, 69))
        assertEquals(300 * MINUTE, assertNotNull(longestParked(steady, drives(steady))).endMillis)
    }

    @Test
    fun `sentry or climate running keeps a locked car from counting as sleep`() {
        val a = sample(0)
        val b = sample(60)

        fun reading(
            minutes: Long,
            kind: ActivityKind,
            on: Boolean,
        ) = ActivitySample(minutes * MINUTE, "", kind, on)
        // Sentry on just before the gap stands for it; a later reading that turns it off ends it.
        assertEquals(false, unattendedBetween(a, b, ProjectionContext(activity = listOf(reading(-5, ActivityKind.SENTRY, true)))))
        assertEquals(true, unattendedBetween(a, b, ProjectionContext(activity = listOf(reading(-5, ActivityKind.SENTRY, false)))))
        // The climate running inside the gap counts, and a Sentry-off reading does not cancel a climate-on one.
        val climate = listOf(reading(10, ActivityKind.CLIMATE, true), reading(20, ActivityKind.SENTRY, false))
        assertEquals(false, unattendedBetween(a, b, ProjectionContext(activity = climate)))
        // A reading older than a heartbeat is stale.
        assertEquals(true, unattendedBetween(a, b, ProjectionContext(activity = listOf(reading(-120, ActivityKind.CLIMATE, true)))))
    }

    private fun status(
        minutes: Long,
        attended: Boolean = false,
    ) = StatusSample(
        timestampMillis = minutes * MINUTE,
        vehicleId = "",
        asleep = !attended,
        userPresent = attended,
        locked = !attended,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val ODOMETER = 1_234_500
    }
}
