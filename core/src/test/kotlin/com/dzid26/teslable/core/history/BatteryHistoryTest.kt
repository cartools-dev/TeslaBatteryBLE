// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryHistoryTest {
    private fun sample(
        minutes: Long,
        percent: Int,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
    ) = BatterySample(
        timestampMillis = minutes * 60_000L,
        batteryLevel = percent,
        chargingState = state,
        chargeLimit = null,
    )

    private fun rated(
        minutes: Long,
        percent: Int,
        miles: Float,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
    ) = sample(minutes, percent, state).copy(ratedRangeMiles = miles)

    @Test
    fun withinFiltersByRange() {
        val samples =
            listOf(
                sample(minutes = 0, percent = 80),
                sample(minutes = 60, percent = 79),
                sample(minutes = 600, percent = 70),
            )
        val now = 600 * 60_000L

        assertEquals(1, samples.within(HistoryRange.SIX_HOURS, now).size)
        assertEquals(3, samples.within(HistoryRange.DAY, now).size)
        assertEquals(3, samples.within(HistoryRange.ALL, now).size)
    }

    @Test
    fun chargeStatsNullWithoutCharging() {
        assertNull(chargeStats(listOf(sample(0, 80), sample(60, 79))))
        assertNull(chargeStats(emptyList()))
    }

    @Test
    fun chargeStatsNullWhileStillCharging() {
        val samples = listOf(sample(0, 80), sample(30, 81, ChargingStateKind.Charging))
        assertNull(chargeStats(samples))
    }

    @Test
    fun chargeStatsAnchorOnTheChargeEnd() {
        val samples =
            listOf(
                sample(0, 70),
                sample(30, 80, ChargingStateKind.Charging),
                sample(60, 85, ChargingStateKind.Charging),
                sample(90, 84),
                sample(120, 78),
            )

        val stats = chargeStats(samples)!!
        // The reference is the charge's own end (the 85 at minute 60), not the
        // first sample after it.
        assertEquals(60 * 60_000L, stats.sinceMillis)
        assertEquals(85f, stats.chargePercent(null))
        assertEquals(78f, stats.currentPercent(null))
        assertEquals(7f, stats.usedPercent(null))
    }

    @Test
    fun chargeStatsExpressTheDrainInRangeAndConvertAtTheEnd() {
        // A scale of 2.7 mi per percent: 3.0 rated miles drained is 1.11 points,
        // which the whole levels (80 -> 79) would round to 1 or 2.
        val scale =
            FullRangeScale(milesPerPercent = 2.7f, offsetMiles = 0f, standardErrorMiles = 0.1f, samples = 10, pinned = true)
        val samples =
            listOf(
                rated(0, 80, 216.0f, ChargingStateKind.Charging),
                rated(60, 79, 213.0f),
            )

        val stats = chargeStats(samples)!!

        assertEquals(3.0f, stats.usedMiles!!, 0.001f)
        assertEquals(1.11f, stats.usedPercent(scale), 0.01f)
        assertEquals(80f, stats.chargePercent(scale), 0.01f)
        assertEquals(78.89f, stats.currentPercent(scale), 0.01f)
        // Without a pinned scale the whole levels are the fallback.
        assertEquals(1f, stats.usedPercent(null), 0.001f)
        assertEquals(1f, stats.usedPercent(scale.copy(pinned = false)), 0.001f)
    }

    @Test
    fun chargeStatsFallBackToLevelsWithoutRatedRange() {
        val scale =
            FullRangeScale(milesPerPercent = 2.7f, offsetMiles = 0f, standardErrorMiles = 0.1f, samples = 10, pinned = true)
        val stats = chargeStats(listOf(sample(0, 80, ChargingStateKind.Charging), sample(60, 77)))!!

        assertEquals(null, stats.usedMiles)
        assertEquals(3f, stats.usedPercent(scale), 0.001f)
        assertEquals(80f, stats.chargePercent(scale), 0.001f)
    }

    @Test
    fun chargeStatsUseLastCharge() {
        val samples =
            listOf(
                sample(0, 60, ChargingStateKind.Charging),
                sample(30, 70),
                sample(60, 80, ChargingStateKind.Charging),
                sample(90, 75),
            )

        val stats = chargeStats(samples)!!
        assertEquals(60 * 60_000L, stats.sinceMillis)
        assertEquals(80f, stats.chargePercent(null))
        assertEquals(5f, stats.usedPercent(null))
    }

    @Test
    fun chargeStatsKeepTheChargeTimeAcrossAGap() {
        // The charge ends at 47% (minute 60); the app sees nothing until a
        // reading five days later at 28%. The charge is an hour ago, not five
        // days, and 19 points were used since it ended.
        val fiveDays = 5 * 24 * 60L
        val samples =
            listOf(
                sample(30, 46, ChargingStateKind.Charging),
                sample(60, 47, ChargingStateKind.Charging),
                sample(60 + fiveDays, 28),
            )

        val stats = chargeStats(samples)!!
        assertEquals(60 * 60_000L, stats.sinceMillis)
        assertEquals(47f, stats.chargePercent(null))
        assertEquals(28f, stats.currentPercent(null))
        assertEquals(19f, stats.usedPercent(null))
    }

    @Test
    fun chargeStatsIgnoreTimeOrder() {
        // A late reply may land out of order; the newest sample by time must
        // still win, both for the charge and for the current level.
        val samples =
            listOf(
                sample(60, 80, ChargingStateKind.Charging),
                sample(120, 75),
                sample(30, 70, ChargingStateKind.Charging),
            )

        val stats = chargeStats(samples)!!
        assertEquals(60 * 60_000L, stats.sinceMillis)
        assertEquals(80f, stats.chargePercent(null))
        assertEquals(75f, stats.currentPercent(null))
        assertEquals(5f, stats.usedPercent(null))
    }

    @Test
    fun chargeStatsCarryTheSessionEnergy() {
        val samples =
            listOf(
                sample(0, 70, ChargingStateKind.Charging).copy(chargeEnergyAdded = 10f),
                sample(30, 85, ChargingStateKind.Charging).copy(chargeEnergyAdded = 31.3f),
                sample(60, 84),
            )

        assertEquals(31.3f, chargeStats(samples)!!.energyKwh)
        assertNull(chargeStats(samples.map { it.copy(chargeEnergyAdded = 0f) })!!.energyKwh)
    }
}
