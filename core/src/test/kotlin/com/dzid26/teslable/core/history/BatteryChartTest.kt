// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.ChargingStateKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryChartTest {
    private fun sample(
        minutes: Long,
        percent: Int,
        state: ChargingStateKind? = ChargingStateKind.Disconnected,
        limit: Int? = null,
    ) = BatterySample(
        timestampMillis = minutes * 60_000L,
        batteryLevel = percent,
        chargingState = state,
        chargeLimit = limit,
    )

    @Test
    fun projectionNullWhenLastSampleIsNotCharging() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80),
                sample(30, 60),
            )
        assertNull(chargeProjection(samples))
    }

    @Test
    fun projectionNullWithSingleChargingSample() {
        assertNull(chargeProjection(listOf(sample(0, 50, ChargingStateKind.Charging, limit = 80))))
    }

    @Test
    fun projectionNullWithoutChargeLimit() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging),
                sample(30, 60, ChargingStateKind.Charging),
            )
        assertNull(chargeProjection(samples))
    }

    @Test
    fun projectionNullWhenLimitAlreadyReached() {
        val atLimit =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80),
                sample(30, 80, ChargingStateKind.Charging, limit = 80),
            )
        assertNull(chargeProjection(atLimit))

        val aboveLimit =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 70),
                sample(30, 80, ChargingStateKind.Charging, limit = 70),
            )
        assertNull(chargeProjection(aboveLimit))
    }

    @Test
    fun projectionNullWhenPercentStalls() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80),
                sample(30, 50, ChargingStateKind.Charging, limit = 80),
            )
        assertNull(chargeProjection(samples))
    }

    @Test
    fun projectionNullWhenRateSpanIsTooShort() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80),
                sample(1, 55, ChargingStateKind.Charging, limit = 80),
            )
        assertNull(chargeProjection(samples))
    }

    @Test
    fun projectionComputesCompletionFromTheCurrentRun() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 90),
                sample(30, 60, ChargingStateKind.Charging, limit = 90),
                sample(60, 70, ChargingStateKind.Charging, limit = 90),
            )

        val projection = chargeProjection(samples)!!
        assertEquals(samples.last(), projection.from)
        assertEquals(90, projection.targetPercent)
        // 20% gained over 60 min -> the last 20% takes another 60 min.
        assertEquals(120 * 60_000L, projection.completionMillis)
    }

    @Test
    fun projectionUsesOnlyTheTrailingChargingRun() {
        val samples =
            listOf(
                sample(0, 30, ChargingStateKind.Charging, limit = 80),
                sample(30, 40, ChargingStateKind.Charging, limit = 80),
                sample(60, 39),
                sample(90, 50, ChargingStateKind.Charging, limit = 80),
                sample(120, 60, ChargingStateKind.Charging, limit = 80),
            )

        val projection = chargeProjection(samples)!!
        // 10% gained over 30 min -> the last 20% takes another 60 min.
        assertEquals(180 * 60_000L, projection.completionMillis)
    }

    @Test
    fun projectionUsesTheLiveRateAndTheRunsMiles() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80).copy(ratedRangeMiles = 150f),
                sample(10, 60, ChargingStateKind.Charging, limit = 80).copy(ratedRangeMiles = 180f),
            )

        // 20% left at 3 mi/% = 60 mi; at the live 90 mph that is 40 min,
        // not the 20 min the integer-percent average would give.
        val projection = chargeProjection(samples, chargeRateMph = 90f)!!
        assertEquals(10 * 60_000L + 40 * 60_000L, projection.completionMillis)
    }

    @Test
    fun projectionUsesTheLearnedScaleWithTheLiveRate() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80),
                sample(10, 60, ChargingStateKind.Charging, limit = 80),
            )

        // 20% x 2.4 mi/% = 48 mi; at 90 mph that is 32 min.
        val projection = chargeProjection(samples, chargeRateMph = 90f, fullRangeMiles = 240f)!!
        assertEquals(10 * 60_000L + 32 * 60_000L, projection.completionMillis)
    }

    @Test
    fun projectionKeepsThePercentFallbackWithoutARate() {
        val samples =
            listOf(
                sample(0, 50, ChargingStateKind.Charging, limit = 80).copy(ratedRangeMiles = 150f),
                sample(10, 60, ChargingStateKind.Charging, limit = 80).copy(ratedRangeMiles = 180f),
            )

        // No live rate: the run average still gives 20 min for the last 20%.
        val projection = chargeProjection(samples, fullRangeMiles = 240f)!!
        assertEquals(10 * 60_000L + 20 * 60_000L, projection.completionMillis)
    }

    @Test
    fun projectionFollowsTheRatedRangeWhileTheLevelSitsStill() {
        // 31% for 30 min, rated range +3 mi: 6 mi/h. 84 mi at 31% implies ~271 mi
        // full range, so the 54 points left are ~146 mi, about 24 h away.
        val samples =
            listOf(
                sample(0, 31, ChargingStateKind.Charging, limit = 85).copy(ratedRangeMiles = 81f),
                sample(30, 31, ChargingStateKind.Charging, limit = 85).copy(ratedRangeMiles = 84f),
            )

        val projection = chargeProjection(samples)!!
        assertEquals(85, projection.targetPercent)
        assertEquals((30 * 60_000L + 24 * 3_600_000L).toDouble(), projection.completionMillis.toDouble(), 30 * 60_000.0)
    }

    private fun powered(
        minutes: Long,
        percent: Int,
        volts: Int,
    ) = sample(minutes, percent, ChargingStateKind.Charging, limit = 80)
        .copy(chargerVoltage = volts)

    @Test
    fun beamSpansTheSupplyVoltageSeenThisRun() {
        // 10% per 30 min; the supply sagged from 250 V to 230 V.
        val samples =
            listOf(
                powered(0, 50, 250),
                powered(15, 55, 240),
                powered(30, 60, 230),
            )
        val projection = chargeProjection(samples)!!
        // 20% left at 1% per 3 min = 60 min.
        assertEquals(90 * 60_000L, projection.completionMillis)
        // Mean 240 V against a 250 V peak and a 230 V floor: earlier and later than the line.
        assertEquals(true, projection.earliestMillis!! < projection.completionMillis)
        assertEquals(true, projection.latestMillis!! > projection.completionMillis)
    }

    @Test
    fun noBeamWithoutAVaryingSupply() {
        val samples =
            listOf(
                powered(0, 50, 240),
                powered(30, 60, 240),
            )
        val projection = chargeProjection(samples)!!
        assertNull(projection.earliestMillis)
        assertNull(projection.latestMillis)
    }

    private val pinnedScale =
        FullRangeScale(fullRangeMiles = 300f, offsetMiles = 0f, standardErrorMiles = 0.3f, samples = 20, pinned = true)

    private fun ranged(
        minutes: Long,
        level: Int,
        miles: Float,
    ) = sample(minutes, level, ChargingStateKind.Charging, limit = 80).copy(ratedRangeMiles = miles)

    @Test
    fun theFittedScaleTurnsAStuckLevelIntoARate() {
        // The level sits at 50 while the range climbs 1.5 mi (0.5 points at 3 mi per
        // point) every 10 min: 3 points per hour.
        val samples =
            listOf(
                ranged(0, 50, 150f),
                ranged(10, 50, 151.5f),
                ranged(20, 50, 153f),
                ranged(30, 50, 154.5f),
            )
        val projection = chargeProjection(samples, scale = pinnedScale)!!
        // 154.5 mi reads 51.5%: 28.5 points to the 80% limit at 3 points per hour is 9.5 h.
        val expected = 30 * 60_000L + (9.5 * 3_600_000L).toLong()
        assertEquals(expected.toDouble(), projection.completionMillis.toDouble(), 60_000.0)
    }

    @Test
    fun aNoisyRateWidensTheBeamAndATidyOneNarrowsIt() {
        val tidy =
            listOf(
                ranged(0, 50, 150f),
                ranged(10, 52, 156f),
                ranged(20, 54, 162f),
                ranged(30, 56, 168f),
                ranged(40, 58, 174f),
            )
        val noisy =
            listOf(
                ranged(0, 50, 150f),
                ranged(10, 53, 159f),
                ranged(20, 53, 159.5f),
                ranged(30, 57, 171f),
                ranged(40, 58, 174f),
            )
        val tidyBeam = chargeProjection(tidy, scale = pinnedScale)!!
        val noisyBeam = chargeProjection(noisy, scale = pinnedScale)!!
        val tidyWidth = tidyBeam.latestMillis!! - tidyBeam.earliestMillis!!
        val noisyWidth = noisyBeam.latestMillis!! - noisyBeam.earliestMillis!!
        assertEquals(true, noisyWidth > tidyWidth)
    }
}
