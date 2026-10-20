// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import com.dzid26.teslable.core.history.DrivePoint
import com.dzid26.teslable.core.history.FullRangeScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DriveStatsTest {
    private fun point(
        miles: Double,
        percent: Float,
        trip: Int = 0,
    ) = DrivePoint(timestampMillis = 0, milesDriven = miles, socPercent = percent, regenerating = false, trip = trip)

    // 300 mi at 100%: 3 miles per percent.
    private val scale = FullRangeScale(300f, 0f, 1f, 20, pinned = true)

    @Test
    fun `computes the rates from the percent used and the rated constant`() {
        // 10 mi, 5% used = 15 rated miles at 0.25 kWh/mi = 3.75 kWh.
        val stats = assertNotNull(driveStats(listOf(point(0.0, 80f), point(10.0, 75f)), scale, 0.25f, 0.2f))
        assertEquals(10f, stats.milesDriven)
        assertEquals(5f, stats.usedPercent)
        assertEquals(0.5f, assertNotNull(stats.percentPerMile), 1e-4f)
        assertEquals(3.75f, assertNotNull(stats.energyKwh), 1e-4f)
        assertEquals(0.375f, assertNotNull(stats.kwhPerMile), 1e-4f)
        assertEquals(0.75f, assertNotNull(stats.costAtHomePrice), 1e-4f)
    }

    @Test
    fun `counts only the miles inside trips`() {
        val points = listOf(point(0.0, 80f), point(4.0, 78f), point(40.0, 78f, trip = 1), point(46.0, 75f, trip = 1))
        val stats = assertNotNull(driveStats(points, scale, 0.25f, null))
        assertEquals(10f, stats.milesDriven)
        assertEquals(5f, stats.usedPercent)
        assertNull(stats.costAtHomePrice)
    }

    @Test
    fun `keeps the percent rate but hides energy without a pinned scale or constant`() {
        val points = listOf(point(0.0, 80f), point(10.0, 75f))
        val unpinned = assertNotNull(driveStats(points, scale.copy(pinned = false), 0.25f, 0.2f))
        assertEquals(0.5f, assertNotNull(unpinned.percentPerMile), 1e-4f)
        assertNull(unpinned.energyKwh)
        assertNull(unpinned.kwhPerMile)
        assertNull(assertNotNull(driveStats(points, scale, null, 0.2f)).energyKwh)
        assertNull(assertNotNull(driveStats(points, null, 0.25f, 0.2f)).energyKwh)
    }

    @Test
    fun `hides the rates over a very short drive and the stats when nothing was driven`() {
        val short = assertNotNull(driveStats(listOf(point(0.0, 80f), point(0.4, 79f)), scale, 0.25f, null))
        assertNull(short.percentPerMile)
        assertNull(short.energyKwh)
        assertNull(driveStats(listOf(point(0.0, 80f), point(0.0, 80f)), scale, 0.25f, null))
        assertNull(driveStats(emptyList(), scale, 0.25f, null))
    }

    @Test
    fun `a regenerating drive that ends higher shows no energy or percent rate`() {
        val stats = assertNotNull(driveStats(listOf(point(0.0, 70f), point(5.0, 71f)), scale, 0.25f, 0.2f))
        assertNull(stats.percentPerMile)
        assertNull(stats.energyKwh)
    }
}
