// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveRouteTest {
    private fun route(
        carMinutes: Long,
        miles: Float? = 4.7f,
        phoneMinutes: Long? = null,
    ) = RouteSample(
        timestampMillis = carMinutes * MINUTE,
        vehicleId = "",
        destination = "Home",
        milesToArrival = miles,
        minutesToArrival = 12f,
        trafficDelayMinutes = 0f,
        energyAtArrivalPercent = 44f,
        readAtMillis = phoneMinutes?.let { it * MINUTE },
    )

    @Test
    fun `a recent route is live`() {
        val latest = route(carMinutes = 100)
        assertEquals(latest, activeRoute(latest, nowMillis = 105 * MINUTE))
    }

    @Test
    fun `no route is no trip`() {
        assertNull(activeRoute(null, nowMillis = 101 * MINUTE))
    }

    @Test
    fun `a route with no distance left has ended`() {
        assertNull(activeRoute(route(carMinutes = 100, miles = null), nowMillis = 101 * MINUTE))
    }

    @Test
    fun `a route not refreshed for ten minutes is a finished trip`() {
        val latest = route(carMinutes = 100)
        assertEquals(latest, activeRoute(latest, nowMillis = 110 * MINUTE))
        assertNull(activeRoute(latest, nowMillis = 111 * MINUTE))
    }

    @Test
    fun `recency is judged on the phone clock when the car clock is off`() {
        // The car says 90 minutes, the phone read it at 100: ten minutes old at 105, not fifteen.
        val latest = route(carMinutes = 90, phoneMinutes = 100)
        assertEquals(latest, activeRoute(latest, nowMillis = 105 * MINUTE))
        assertNull(activeRoute(latest, nowMillis = 111 * MINUTE))
    }

    @Test
    fun `a trip keeps its longest remaining distance as its length`() {
        val first = route(carMinutes = 100, miles = 10f).continuing(null)
        val second = route(carMinutes = 103, miles = 6f).continuing(first)
        val rerouted = route(carMinutes = 106, miles = 8f).continuing(second)
        assertEquals(10f, first.tripStartMiles)
        assertEquals(10f, second.tripStartMiles)
        assertEquals(10f, rerouted.tripStartMiles)
        assertEquals(0.2f, rerouted.tripProgress()!!, 1e-6f)
    }

    @Test
    fun `a long gap starts a new trip`() {
        val first = route(carMinutes = 100, miles = 10f).continuing(null)
        val later = route(carMinutes = 130, miles = 3f).continuing(first)
        assertEquals(3f, later.tripStartMiles)
        assertEquals(0f, later.tripProgress())
    }

    @Test
    fun `progress is unknown without a length and complete at arrival`() {
        assertNull(route(carMinutes = 100).tripProgress())
        val arrived = route(carMinutes = 100, miles = 0f).copy(tripStartMiles = 5f)
        assertEquals(1f, arrived.tripProgress())
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
