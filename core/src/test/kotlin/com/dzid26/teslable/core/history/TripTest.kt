// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripTest {
    private fun reading(
        minute: Long,
        miles: Float,
        destination: String? = "Home",
        minutesLeft: Float? = null,
    ) = RouteSample(
        timestampMillis = minute * MINUTE,
        vehicleId = "",
        destination = destination,
        milesToArrival = miles,
        minutesToArrival = minutesLeft,
        trafficDelayMinutes = 0f,
        energyAtArrivalPercent = 44f,
    )

    @Test
    fun `readings a few minutes apart are one trip with its longest distance as the length`() {
        val trips = segmentTrips(listOf(reading(0, 10f), reading(3, 7f), reading(6, 4f, minutesLeft = 5f)))
        assertEquals(1, trips.size)
        assertEquals(10f, trips.single().lengthMiles)
        assertEquals("Home", trips.single().destination)
        assertEquals(6 * MINUTE + 5 * MINUTE, trips.single().durationMillis)
    }

    @Test
    fun `a gap over ten minutes or a new destination starts another trip`() {
        val trips =
            segmentTrips(
                listOf(
                    reading(0, 10f),
                    reading(3, 7f),
                    reading(60, 5f, "Work"),
                    reading(63, 3f, "Work"),
                    reading(66, 9f, "Gym"),
                    reading(69, 6f, "Gym"),
                ),
            )
        assertEquals(listOf("Home", "Work", "Gym"), trips.map { it.destination })
    }

    @Test
    fun `a single reading is not a trip`() {
        assertEquals(emptyList(), segmentTrips(listOf(reading(0, 3f))))
    }

    @Test
    fun `the live route is not a past trip`() {
        val readings = listOf(reading(0, 10f), reading(3, 7f), reading(60, 5f), reading(63, 3f))
        val trips = segmentTrips(readings)
        assertEquals(1, trips.excludingLive(readings.last()).size)
        assertEquals(2, trips.excludingLive(null).size)
    }

    @Test
    fun `the rated range before the trip must be recent`() {
        val samples =
            listOf(
                BatterySample(0, 80, null, null, ratedRangeMiles = 200f),
                BatterySample(40 * MINUTE, 70, null, null, ratedRangeMiles = 180f),
            )
        assertEquals(200f, ratedMilesBefore(samples, 20 * MINUTE))
        assertNull(ratedMilesBefore(samples.take(1), 31 * MINUTE))
        assertEquals(180f, ratedMilesBefore(samples, 45 * MINUTE))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
