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
        odometer: Int? = null,
    ) = RouteSample(
        timestampMillis = minute * MINUTE,
        vehicleId = "",
        destination = destination,
        milesToArrival = miles,
        minutesToArrival = minutesLeft,
        trafficDelayMinutes = 0f,
        energyAtArrivalPercent = 44f,
        odometerInHundredthsOfAMile = odometer,
    )

    @Test
    fun `readings a few minutes apart are one trip with its longest distance as the length`() {
        val trips = segmentTrips(listOf(reading(0, 10f), reading(3, 7f), reading(6, 1f, minutesLeft = 5f)))
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
    fun `the trip whose newest reading is the live route is marked in progress`() {
        val readings = listOf(reading(0, 10f), reading(3, 7f), reading(60, 5f), reading(63, 3f))
        val trips = segmentTrips(readings)
        assertEquals(listOf(false, true), trips.markingLive(readings.last()).map { it.inProgress })
        assertEquals(listOf(false, false), trips.markingLive(null).map { it.inProgress })
    }

    @Test
    fun `a finished trip arrives at the battery reading after it, else the car's prediction`() {
        val trip =
            segmentTrips(
                listOf(reading(0, 10f, minutesLeft = 20f), reading(10, 5f, minutesLeft = 10f), reading(20, 0f, minutesLeft = 0f)),
            ).single()
        val samples =
            listOf(
                BatterySample(-5 * MINUTE, 60, null, null, ratedRangeMiles = 180f),
                BatterySample(22 * MINUTE, 50, null, null, ratedRangeMiles = 150f),
            )
        val summary = trip.summary(samples, scale, ratedKwhPerMile = 0.25f, pricePerKwh = 0.30f)
        assertEquals(60f, summary.startPercent)
        assertEquals(50f, summary.arrivalPercent)
        // 10 mi in 20 minutes.
        assertEquals(30f, summary.averageMilesPerHour!!, 0.01f)
        assertEquals(44f, trip.summary(emptyList(), scale, null, null).arrivalPercent)
        assertNull(trip.summary(emptyList(), scale, null, null).startPercent)
    }

    @Test
    fun `a cancelled trip counts the miles driven and the time spent, not the plan`() {
        val trip = segmentTrips(listOf(reading(0, 10f, minutesLeft = 20f), reading(10, 6f, minutesLeft = 12f))).single()
        assertEquals(false, trip.arrived)
        assertEquals(4f, trip.distanceMiles)
        assertEquals(10 * MINUTE, trip.durationMillis)
        val samples =
            listOf(
                BatterySample(-5 * MINUTE, 60, null, null, ratedRangeMiles = 180f),
                BatterySample(12 * MINUTE, 57, null, null, ratedRangeMiles = 171f),
            )
        // The car's arrival prediction was for the destination it never reached.
        assertNull(trip.summary(emptyList(), scale, null, null).arrivalPercent)
        assertEquals(57f, trip.summary(samples, scale, null, null).arrivalPercent)
    }

    @Test
    fun `a finished trip's distance comes from the odometer when it has one`() {
        val trip =
            segmentTrips(
                listOf(
                    reading(0, 10f, minutesLeft = 20f, odometer = 100_000),
                    reading(10, 1f, minutesLeft = 2f, odometer = 100_480),
                ),
            ).single()
        assertEquals(true, trip.arrived)
        assertEquals(4.8f, trip.distanceMiles!!, 0.001f)
    }

    @Test
    fun `the planned length and time stay on a cancelled trip`() {
        val trip = segmentTrips(listOf(reading(0, 10f, minutesLeft = 20f), reading(10, 6f, minutesLeft = 12f))).single()
        assertEquals(10f, trip.lengthMiles)
        assertEquals(20 * MINUTE, trip.plannedMillis)
        assertEquals(4f, trip.distanceMiles)
    }

    @Test
    fun `a trip in progress shows the whole route and its predicted time`() {
        val trip =
            segmentTrips(
                listOf(reading(0, 10f, minutesLeft = 20f), reading(5, 7f, minutesLeft = 14f)),
            ).single().copy(inProgress = true)
        assertEquals(10f, trip.distanceMiles)
        assertEquals(19 * MINUTE, trip.durationMillis)
    }

    @Test
    fun `energy rates need a distance worth dividing by`() {
        val short = segmentTrips(listOf(reading(0, 0.4f, minutesLeft = 2f), reading(2, 0f, minutesLeft = 0f))).single()
        val samples = listOf(BatterySample(-5 * MINUTE, 60, null, null, ratedRangeMiles = 180f))
        assertNull(short.summary(samples, scale, 0.25f, 0.30f).kwhPerMile)
    }

    @Test
    fun `an in-progress trip has no battery reading after it yet`() {
        val trip =
            segmentTrips(
                listOf(reading(0, 10f, minutesLeft = 20f), reading(5, 7f, minutesLeft = 14f)),
            ).single().copy(inProgress = true)
        val samples = listOf(BatterySample(30 * MINUTE, 40, null, null, ratedRangeMiles = 120f))
        assertEquals(44f, trip.summary(samples, scale, null, null).arrivalPercent)
    }

    private companion object {
        const val MINUTE = 60_000L
        val scale = FullRangeScale(fullRangeMiles = 300f, offsetMiles = 0f, standardErrorMiles = 0.1f, samples = 40, pinned = true)
    }
}
