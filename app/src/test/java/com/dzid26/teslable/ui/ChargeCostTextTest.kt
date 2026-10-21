// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ChargeCostTextTest {
    private lateinit var locale: Locale

    @Before
    fun pinLocale() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(locale)

    @Test
    fun energyAndCostWithAPrice() {
        assertEquals("31.3kWh · \$6.26", sessionEnergyText(31.3f, 0.2f))
    }

    @Test
    fun energyAloneWithoutAPrice() {
        assertEquals("31.3kWh", sessionEnergyText(31.3f, null))
    }

    @Test
    fun nothingWithoutEnergy() {
        assertNull(sessionEnergyText(0f, 0.2f))
        assertNull(sessionEnergyText(null, 0.2f))
    }

    @Test
    fun costInTheChosenCurrency() {
        val text = sessionEnergyText(31.3f, 0.2f, "EUR")!!
        assertTrue(text, text.startsWith("31.3kWh · ") && text.contains("€") && text.contains("6.26"))
    }
}
