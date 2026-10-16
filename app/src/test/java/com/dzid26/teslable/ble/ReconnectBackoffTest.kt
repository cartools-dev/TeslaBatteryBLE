// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconnectBackoffTest {
    @Test
    fun `the delay doubles from five seconds up to the ceiling`() {
        val seconds = (1..7).map { ReconnectBackoff.delayMs(it) / 1000 }

        assertEquals(listOf(5L, 10L, 20L, 40L, 80L, 160L, 300L), seconds)
    }

    @Test
    fun `the delay stays at the ceiling however many attempts fail`() {
        assertEquals(ReconnectBackoff.CEILING_MS, ReconnectBackoff.delayMs(8))
        assertEquals(ReconnectBackoff.CEILING_MS, ReconnectBackoff.delayMs(10_000))
    }

    @Test
    fun `an attempt number below one is treated as the first`() {
        assertEquals(ReconnectBackoff.FIRST_DELAY_MS, ReconnectBackoff.delayMs(0))
        assertEquals(ReconnectBackoff.FIRST_DELAY_MS, ReconnectBackoff.delayMs(-3))
    }
}
