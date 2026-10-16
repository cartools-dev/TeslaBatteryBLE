// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChargeCostTest {
    @Test
    fun `the cost is the energy added times the price per kWh`() {
        assertEquals(3.75f, chargeSessionCost(energyAddedKwh = 12.5f, pricePerKwh = 0.30f)!!, 0.0001f)
    }

    @Test
    fun `no energy or no price means no cost`() {
        assertNull(chargeSessionCost(null, 0.30f))
        assertNull(chargeSessionCost(12.5f, null))
    }

    @Test
    fun `a session that added nothing, or a free price, shows no cost`() {
        assertNull(chargeSessionCost(0f, 0.30f))
        assertNull(chargeSessionCost(12.5f, 0f))
    }

    @Test
    fun `values that are not real numbers show no cost`() {
        assertNull(chargeSessionCost(Float.NaN, 0.30f))
        assertNull(chargeSessionCost(12.5f, Float.POSITIVE_INFINITY))
        assertNull(chargeSessionCost(-1f, 0.30f))
    }
}
