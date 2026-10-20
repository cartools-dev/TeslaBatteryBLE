// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.RouteSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TripEstimateTest {
    private val scale =
        FullRangeScale(
            fullRangeMiles = 300f,
            offsetMiles = 2f,
            standardErrorMiles = 0.5f,
            samples = 40,
            pinned = true,
        )

    private fun route(
        miles: Float? = 25f,
        percent: Float? = 50f,
    ) = RouteSample(
        timestampMillis = 1_000L,
        vehicleId = "car",
        destination = "Work",
        milesToArrival = miles,
        minutesToArrival = 30f,
        trafficDelayMinutes = 0f,
        energyAtArrivalPercent = percent,
    )

    @Test
    fun `arrival miles energy and cost follow from the arrival percent`() {
        val estimate = tripEstimate(route(), 200f, scale, ratedKwhPerMile = 0.25f, pricePerKwh = 0.2f)
        assertEquals(25f, estimate.milesRemaining)
        assertEquals(50f, estimate.arrivalPercent)
        assertEquals(152f, estimate.arrivalRatedMiles!!, 0.001f)
        assertEquals(12f, estimate.energyKwh!!, 0.001f)
        assertEquals(2.4f, estimate.costAtHomePrice!!, 0.001f)
    }

    @Test
    fun `a scale with a large error shows the percent but no miles energy or cost`() {
        val estimate = tripEstimate(route(), 200f, scale.copy(standardErrorMiles = 20f), ratedKwhPerMile = 0.25f, pricePerKwh = 0.2f)
        assertEquals(50f, estimate.arrivalPercent)
        assertNull(estimate.arrivalRatedMiles)
        assertNull(estimate.energyKwh)
        assertNull(estimate.costAtHomePrice)
    }

    @Test
    fun `a tight scale gives miles and cost whether or not it is pinned`() {
        val estimate = tripEstimate(route(), 200f, scale.copy(pinned = false), ratedKwhPerMile = 0.25f, pricePerKwh = 0.2f)
        assertEquals(12f, estimate.energyKwh!!, 0.001f)
        assertEquals(2.4f, estimate.costAtHomePrice!!, 0.001f)
    }

    @Test
    fun `no price hides only the cost`() {
        val estimate = tripEstimate(route(), 200f, scale, ratedKwhPerMile = 0.25f, pricePerKwh = null)
        assertEquals(12f, estimate.energyKwh!!, 0.001f)
        assertNull(estimate.costAtHomePrice)
    }

    @Test
    fun `no rated constant hides energy and cost`() {
        val estimate = tripEstimate(route(), 200f, scale, ratedKwhPerMile = null, pricePerKwh = 0.2f)
        assertEquals(152f, estimate.arrivalRatedMiles!!, 0.001f)
        assertNull(estimate.energyKwh)
        assertNull(estimate.costAtHomePrice)
    }

    @Test
    fun `an arrival above the current charge uses no energy`() {
        val estimate = tripEstimate(route(percent = 90f), 100f, scale, ratedKwhPerMile = 0.25f, pricePerKwh = 0.2f)
        assertNull(estimate.energyKwh)
        assertNull(estimate.costAtHomePrice)
    }

    @Test
    fun `out of range or missing route values are dropped`() {
        val bad = tripEstimate(route(miles = -1f, percent = 140f), 200f, scale, ratedKwhPerMile = 0.25f, pricePerKwh = 0.2f)
        assertNull(bad.milesRemaining)
        assertNull(bad.arrivalPercent)
        assertNull(bad.arrivalRatedMiles)
        val none = tripEstimate(route(miles = null, percent = null), null, null, null, null)
        assertEquals(TripEstimate(null, null, null, null, null), none)
    }
}
