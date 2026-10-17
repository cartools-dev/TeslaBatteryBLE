// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.dzid26.teslable.core.history.BatterySample
import com.dzid26.teslable.core.history.DischargeProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ParkedRatesTest {
    private fun projection(
        perDay: Double,
        mixedPerDay: Double? = null,
    ) = DischargeProjection(
        from = BatterySample(timestampMillis = 0, batteryLevel = 60, chargingState = null, chargeLimit = null),
        projectedPercent = 58,
        projectedAtMillis = 0,
        horizonMillis = 0,
        percentPerHour = perDay / 24,
        mixedPercentPerHour = mixedPerDay?.let { it / 24 },
    )

    @Test
    fun `with no sleep evidence there is one rate`() {
        val rates = parkedRates(projection(2.4), Locale.US)
        assertEquals("2.4", rates.main)
        assertNull(rates.mixed)
    }

    @Test
    fun `both rates show when they differ`() {
        val rates = parkedRates(projection(0.4, mixedPerDay = 2.4), Locale.US)
        assertEquals("0.4", rates.main)
        assertEquals("2.4", rates.mixed)
    }

    @Test
    fun `equal after rounding keeps one`() {
        val rates = parkedRates(projection(1.51, mixedPerDay = 1.54), Locale.US)
        assertEquals("1.5", rates.main)
        assertNull(rates.mixed)
    }

    @Test
    fun `a tiny drain reads under a tenth, not zero`() {
        val rates = parkedRates(projection(0.03, mixedPerDay = 1.0), Locale.US)
        assertEquals("<0.1", rates.main)
        assertEquals("1.0", rates.mixed)
    }
}
