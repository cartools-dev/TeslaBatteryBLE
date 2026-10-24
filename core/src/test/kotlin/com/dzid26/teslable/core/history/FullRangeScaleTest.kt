// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullRangeScaleTest {
    private fun sample(
        minutes: Long,
        level: Int,
        miles: Float?,
    ) = BatterySample(
        timestampMillis = minutes * 60_000L,
        batteryLevel = level,
        chargingState = ChargingStateKind.Charging,
        chargeLimit = null,
        ratedRangeMiles = miles,
    )

    /** A charge climbing in 0.25% steps of true SOC; the level is [round] of it. */
    private fun climb(
        scale: Float,
        offset: Float,
        from: Float,
        to: Float,
        round: (Float) -> Int,
    ): List<BatterySample> {
        val out = mutableListOf<BatterySample>()
        var soc = from
        var minute = 0L
        while (soc <= to) {
            out += sample(minute++, round(soc), offset + scale * soc / 100f)
            soc += 0.25f
        }
        return out
    }

    @Test
    fun `recovers the scale whichever way the level rounds`() {
        for (round in listOf<(Float) -> Int>(
            { floor(it).toInt() },
            { it.roundToInt() },
            { ceil(it).toInt() },
        )) {
            val scale = FullRangeScaleEstimator.estimate(climb(270f, -2f, 40.1f, 70f, round))!!
            assertTrue(scale.pinned)
            assertEquals(2.7f, scale.milesPerPercent, 0.015f)
        }
    }

    @Test
    fun `a charge that completes at a 100 limit reads exactly 100 there`() {
        // The fitted line says 267.6 mi at 100, but the car stopped charging at 267.4: that is full.
        val climb = climb(270f, -2f, 40.1f, 98f) { it.roundToInt() }.map { it.copy(chargeLimit = 100) }
        val last = climb.last()
        val done =
            last.copy(
                timestampMillis = last.timestampMillis + 60_000L,
                batteryLevel = 100,
                usableLevel = 100,
                chargingState = ChargingStateKind.Complete,
                ratedRangeMiles = 267.4f,
            )
        val scale = FullRangeScaleEstimator.estimate(climb + done)!!
        assertEquals(100f, done.soc(scale), 0.001f)
        assertEquals(2.7f, scale.milesPerPercent, 0.015f)
    }

    @Test
    fun `a full charge older than the fitted levels does not anchor the top`() {
        val old = climb(270f, -2f, 97f, 99.75f) { it.roundToInt() }.map { it.copy(chargeLimit = 100) }
        val oldDone =
            old.last().copy(
                timestampMillis = old.last().timestampMillis + 60_000L,
                batteryLevel = 100,
                usableLevel = 100,
                chargingState = ChargingStateKind.Complete,
                ratedRangeMiles = 267.4f,
            )
        val recent = climb(270f, -2f, 40.1f, 70f) { it.roundToInt() }.map { it.copy(timestampMillis = it.timestampMillis + 10_000_000L) }
        val withOld = FullRangeScaleEstimator.estimate(listOf(oldDone) + recent)!!
        assertEquals(FullRangeScaleEstimator.estimate(recent)!!.offsetMiles, withOld.offsetMiles)
    }

    @Test
    fun `a completed charge below a 100 limit does not anchor the top`() {
        val climb = climb(270f, -2f, 40.1f, 80f) { it.roundToInt() }
        val done = climb.last().copy(timestampMillis = 10_000_000L, chargingState = ChargingStateKind.Complete, chargeLimit = 80)
        assertEquals(
            FullRangeScaleEstimator.estimate(climb)!!.offsetMiles,
            FullRangeScaleEstimator.estimate(climb + done)!!.offsetMiles,
        )
    }

    @Test
    fun `a cold battery does not move the scale or step the plotted soc`() {
        // The car holds back one point while cold: the range follows the usable level, the display does not.
        val cold =
            climb(270f, 0f, 40.1f, 70f) { it.roundToInt() }.map {
                it.copy(usableLevel = it.batteryLevel - 1, ratedRangeMiles = it.ratedRangeMiles!! - 2.7f)
            }
        val scale = FullRangeScaleEstimator.estimate(cold)!!
        assertEquals(2.7f, scale.milesPerPercent, 0.015f)
        assertEquals(0f, scale.offsetMiles, 0.5f)
        // The plotted line is smooth: neighbouring samples differ by their own range step, not by a point.
        val socs = cold.map { it.soc(scale) }
        assertTrue(socs.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 0.5f })
    }

    @Test
    fun `fine soc comes from the miles not the integer level`() {
        val scale = FullRangeScaleEstimator.estimate(climb(270f, 0f, 40.1f, 70f) { it.roundToInt() })!!
        // 0.6 mi above the 50% boundary: ~50.2% on the displayed scale.
        assertEquals(50.22f, scale.socPercent(0f + 270f * 0.5022f)!!, 0.15f)
    }

    @Test
    fun `stays unpinned with one level span`() {
        val samples =
            listOf(sample(0, 79, 213.0f), sample(1, 80, 215.7f), sample(2, 81, 218.4f))
        val scale = FullRangeScaleEstimator.estimate(samples)
        assertTrue(scale == null || !scale.pinned)
        assertNull(scale?.socPercent(240f))
    }

    @Test
    fun `a drive between two reads does not move the scale`() {
        val charge = climb(270f, 0f, 40.1f, 70f) { it.roundToInt() }
        // A sample after a drive: level 1 lower but miles far lower than the step implies.
        val bumped = charge.take(50) + sample(999, charge[49].batteryLevel + 1, 120f) + charge.drop(50)
        val scale = FullRangeScaleEstimator.estimate(bumped)!!
        assertEquals(2.7f, scale.milesPerPercent, 0.02f)
    }

    @Test
    fun `needs several boundaries`() {
        assertNull(FullRangeScaleEstimator.estimate(emptyList()))
        assertNull(FullRangeScaleEstimator.estimate(listOf(sample(0, 80, null))))
        assertNotNull(FullRangeScaleEstimator.estimate(climb(270f, 0f, 40.1f, 70f) { it.roundToInt() }))
        assertFalse(
            FullRangeScaleEstimator
                .estimate(listOf(sample(0, 80, 216f), sample(1, 80, 216.1f)))
                ?.pinned == true,
        )
    }

    @Test
    fun `tenths split a soc cleanly`() {
        assertEquals(0, socTenths(0f))
        assertEquals(51, socTenths(5.05f))
        assertEquals(620, socTenths(62f))
        assertEquals(624, socTenths(62.4f))
        assertEquals(1000, socTenths(99.96f))
        assertEquals(1000, socTenths(100f))
    }

    @Test
    fun `the line gives rated miles at a soc and at full`() {
        val scale = FullRangeScale(milesPerPercent = 3.0f, offsetMiles = 2f, standardErrorMiles = 0.1f, samples = 10, pinned = true)
        assertEquals(2f, scale.ratedMilesAt(0f), 0.001f)
        assertEquals(152f, scale.ratedMilesAt(50f), 0.001f)
        assertEquals(302f, scale.milesAtFull, 0.001f)
        assertEquals(50f, scale.socPercent(scale.ratedMilesAt(50f))!!, 0.001f)
    }

    @Test
    fun `the scale error is a share of the slope and counts only while pinned`() {
        val pinned = FullRangeScale(milesPerPercent = 3.0f, offsetMiles = 2f, standardErrorMiles = 1.5f, samples = 10, pinned = true)
        assertEquals(0.005, pinned.relativeError, 1e-6)
        assertEquals(0.005, pinned.levelScaleError()!!, 1e-6)
        assertNull(pinned.copy(pinned = false).levelScaleError())
        assertNull(null.levelScaleError())
    }
}
