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

class DriveRateTest {
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
    fun `cost per distance keeps a digit more than the currency`() {
        assertEquals("$0.075", formatCostRate(0.075f, "USD"))
        assertEquals("¥5.0", formatCostRate(5f, "JPY"))
    }

    @Test
    fun `a price replaces the percent rate with the cost per distance`() {
        // $1.00 over 10mi is $0.10/mi, $0.062/km.
        val rate = driveRate(stats(costAtHomePrice = 1f, percentPerMile = 0.5f), DistanceUnit.KILOMETERS, "USD")
        assertEquals(DriveRate.Cost("$0.062", "km"), rate)
    }

    @Test
    fun `without a price the percent rate stands in`() {
        val rate = driveRate(stats(percentPerMile = 0.5f), DistanceUnit.MILES, "USD")
        assertEquals(DriveRate.Percent("0.50", "mi"), rate)
    }

    @Test
    fun `with neither there is no rate`() {
        assertNull(driveRate(stats(), DistanceUnit.MILES, "USD"))
    }
}
