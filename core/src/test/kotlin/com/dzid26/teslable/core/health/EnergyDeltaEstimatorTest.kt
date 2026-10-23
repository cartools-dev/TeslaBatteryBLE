// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnergyDeltaEstimatorTest {
    @Test
    fun `computes capacity from a charge session`() {
        val result = EnergyDeltaEstimator.estimate(26.25f, 20f, 60f)!!
        assertEquals(65.625f, result.usableCapacityKwh, 0.001f)
        assertEquals(40f, result.socDeltaPercent, 0.001f)
    }

    @Test
    fun `accepts a delta of exactly five percent`() {
        val result = EnergyDeltaEstimator.estimate(3f, 20f, 25f)!!
        assertEquals(60f, result.usableCapacityKwh, 0.001f)
    }

    @Test
    fun `rejects invalid sessions`() {
        assertNull(EnergyDeltaEstimator.estimate(0f, 20f, 60f))
        assertNull(EnergyDeltaEstimator.estimate(26.25f, 20f, 23f))
        assertNull(EnergyDeltaEstimator.estimate(26.25f, 60f, 20f))
        assertNull(EnergyDeltaEstimator.estimate(26.25f, -1f, 60f))
        assertNull(EnergyDeltaEstimator.estimate(26.25f, 20f, 101f))
        assertNull(EnergyDeltaEstimator.estimate(Float.NaN, 20f, 60f))
    }
}
