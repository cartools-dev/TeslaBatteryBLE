// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.units

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MileCountriesTest {
    @Test
    fun `the countries that drive in miles`() {
        listOf("US", "GB", "LR", "MM").forEach { assertTrue(countryUsesMiles(it), it) }
    }

    @Test
    fun `other and blank countries use kilometres`() {
        listOf("DE", "FR", "CA", "").forEach { assertFalse(countryUsesMiles(it), it) }
    }

    @Test
    fun `case does not matter`() {
        assertTrue(countryUsesMiles("us"))
    }
}
