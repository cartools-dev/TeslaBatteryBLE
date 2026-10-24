// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import com.dzid26.teslable.core.health.DriveStats
import com.dzid26.teslable.core.units.DistanceUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DriveRatesTest {
    private lateinit var savedLocale: Locale

    @Before
    fun pinLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
    }

    private fun stats(
        costAtHomePrice: Float? = null,
        percentPerMile: Float? = null,
    ) = DriveStats(
        milesDriven = 10f,
        usedPercent = 5f,
        percentPerMile = percentPerMile,
        energyKwh = null,
        kwhPerMile = null,
        costAtHomePrice = costAtHomePrice,
    )

    @Test
    fun `distance shows one decimal with the symbol attached`() {
        assertEquals("12.4km", formatDistance(12.37, DistanceUnit.KILOMETERS, DISTANCE_DECIMALS))
        assertEquals("7.7mi", formatDistance(7.7, DistanceUnit.MILES, DISTANCE_DECIMALS))
        assertEquals("8mi", formatDistance(7.7, DistanceUnit.MILES))
    }

    @Test
    fun `a decimal comma locale writes the distance with a comma`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("12,4km", formatDistance(12.37, DistanceUnit.KILOMETERS, DISTANCE_DECIMALS))
    }

    @Test
    fun `the cost is quoted per 100 distance units`() {
        // $1.00 over 10mi is $10.00 per 100mi, $6.21 per 100km.
        assertEquals("$6.21", driveRates(stats(costAtHomePrice = 1f), DistanceUnit.KILOMETERS, "USD").cost)
        assertEquals("$10.00", driveRates(stats(costAtHomePrice = 1f), DistanceUnit.MILES, "USD").cost)
    }

    @Test
    fun `the percent rate and the cost are independent`() {
        val unpriced = driveRates(stats(percentPerMile = 0.5f), DistanceUnit.MILES, "USD")
        assertEquals("0.50", unpriced.percent)
        assertNull(unpriced.cost)
        val priced = driveRates(stats(costAtHomePrice = 1f, percentPerMile = 0.5f), DistanceUnit.KILOMETERS, "USD")
        assertEquals("0.31", priced.percent)
        assertEquals("$6.21", priced.cost)
    }

    @Test
    fun `with neither there is no rate`() {
        val rates = driveRates(stats(), DistanceUnit.MILES, "USD")
        assertNull(rates.percent)
        assertNull(rates.cost)
    }
}
