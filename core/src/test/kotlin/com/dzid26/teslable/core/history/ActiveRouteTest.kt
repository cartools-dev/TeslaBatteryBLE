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

    private companion object {
        const val MINUTE = 60_000L
    }
}
