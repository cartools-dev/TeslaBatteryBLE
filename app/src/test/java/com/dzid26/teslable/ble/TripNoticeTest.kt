// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.RouteSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripNoticeTest {
    private val inputs =
        TripInputs(
            currentRatedMiles = 130f,
            scale = FullRangeScale(fullRangeMiles = 270f, offsetMiles = 0f, standardErrorMiles = 0.1f, samples = 40, pinned = true),
            ratedKwhPerMile = 0.25f,
        )

    private fun route(
        miles: Float? = 4.7f,
        minutes: Float? = 12.2f,
        delay: Float? = 0f,
        arrival: Float? = 44f,
    ) = RouteSample(0L, "car", "Home", miles, minutes, delay, arrival)

    @Test
    fun `rounds the route to the steps the text shows`() {
        val notice = tripNotice(route(), inputs, pricePerKwh = 0.30f)!!
        assertEquals(47, notice.milesLeftTenths)
        assertEquals(12, notice.minutesLeft)
        assertNull(notice.trafficMinutes)
        assertEquals(44, notice.arrivalPercent)
        // 44% of 270 mi is 118.8 rated miles; 130 - 118.8 = 11.2 miles at 0.25 kWh/mi is 2.8 kWh, 84 cents at 0.30.
        assertEquals(119, notice.arrivalRatedMiles)
        assertEquals(28, notice.energyTenthsKwh)
        assertEquals(84, notice.costCents)
    }

    @Test
    fun `a reading that moves within the shown step is the same notice`() {
        assertEquals(tripNotice(route(minutes = 12.2f), inputs, null), tripNotice(route(minutes = 12.4f), inputs, null))
        assertNotEquals(tripNotice(route(minutes = 12.2f), inputs, null), tripNotice(route(minutes = 11.4f), inputs, null))
    }

    @Test
    fun `shows the traffic delay only when there is one`() {
        assertEquals(3, tripNotice(route(delay = 3.2f), inputs, null)!!.trafficMinutes)
        assertNull(tripNotice(route(delay = 0.4f), inputs, null)!!.trafficMinutes)
    }

    @Test
    fun `hides the energy without a learned scale or price and says nothing without a route reading`() {
        val unlearned = tripNotice(route(), inputs.copy(scale = null), 0.30f)!!
        assertNull(unlearned.energyTenthsKwh)
        assertNull(unlearned.costCents)
        assertNull(tripNotice(route(), inputs, null)!!.costCents)
        assertNull(tripNotice(route(miles = null, minutes = null, arrival = null), inputs, null))
    }
}
