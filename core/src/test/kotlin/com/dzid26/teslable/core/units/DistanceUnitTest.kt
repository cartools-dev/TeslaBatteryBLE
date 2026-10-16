// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.units

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceUnitTest {
    @Test
    fun `miles are the identity`() {
        assertEquals(212.5f, DistanceUnit.MILES.fromMiles(212.5f), 0.0001f)
    }

    @Test
    fun `a mile is 1609344 metres`() {
        assertEquals(1.609344f, DistanceUnit.KILOMETERS.fromMiles(1f), 0.000001f)
        assertEquals(342.0f, DistanceUnit.KILOMETERS.fromMiles(212.5f), 0.1f)
    }

    @Test
    fun `an energy rate per mile becomes a smaller rate per kilometre`() {
        assertEquals(0.17f, DistanceUnit.KILOMETERS.energyPerUnit(0.2736f), 0.0005f)
        assertEquals(0.2736f, DistanceUnit.MILES.energyPerUnit(0.2736f), 0.0001f)
    }

    @Test
    fun `symbols`() {
        assertEquals("mi", DistanceUnit.MILES.symbol)
        assertEquals("km", DistanceUnit.KILOMETERS.symbol)
    }

    @Test
    fun `format shows the converted value and the unit`() {
        assertEquals("342.0\u00A0km", DistanceUnit.KILOMETERS.format(212.5f, decimals = 1))
        assertEquals("212.5\u00A0mi", DistanceUnit.MILES.format(212.5f, decimals = 1))
        assertEquals("342\u00A0km", DistanceUnit.KILOMETERS.format(212.5f))
        assertEquals("342,0\u00A0km", DistanceUnit.KILOMETERS.format(212.5f, decimals = 1, decimalSeparator = ','))
    }

    @Test
    fun `a negative distance keeps its sign and its tenths`() {
        assertEquals("-3.5\u00A0mi", DistanceUnit.MILES.format(-3.5f, decimals = 1))
        assertEquals("-0.5\u00A0mi", DistanceUnit.MILES.format(-0.5f, decimals = 1))
    }
}
