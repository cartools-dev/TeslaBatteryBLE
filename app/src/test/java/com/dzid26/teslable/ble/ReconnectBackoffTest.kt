// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `a car unseen for half an hour is retried less often, up to fifteen minutes`() {
        val unseen = ReconnectBackoff.IDLE_AFTER_MS
        val seconds = (6..10).map { ReconnectBackoff.delayMs(it, unseen) / 1000 }

        assertEquals(listOf(160L, 320L, 640L, 900L, 900L), seconds)
    }

    @Test
    fun `a car seen recently keeps the five minute ceiling`() {
        assertEquals(ReconnectBackoff.CEILING_MS, ReconnectBackoff.delayMs(9, ReconnectBackoff.IDLE_AFTER_MS - 1))
    }

    @Test
    fun `a long connection followed by a drop does not make the car look gone`() {
        val now = 100_000_000L
        val connectedAt = now - 5 * 3_600_000L
        val droppedAt = now - 10_000L

        // Alive at the drop: seen only before the connection, but the link was alive until just now.
        val unseen = ReconnectBackoff.unseenForMs(now, lastSeenMillis = connectedAt - 60_000L, lastAliveMillis = droppedAt)

        assertEquals(10_000L, unseen)
        assertEquals(ReconnectBackoff.FIRST_DELAY_MS, ReconnectBackoff.delayMs(1, unseen))
        assertEquals(ReconnectBackoff.CEILING_MS, ReconnectBackoff.delayMs(9, unseen))
    }

    @Test
    fun `a car that was neither seen nor connected is not treated as idle`() {
        assertEquals(0L, ReconnectBackoff.unseenForMs(100_000_000L, lastSeenMillis = 0L, lastAliveMillis = 0L))
    }

    @Test
    fun `the later of the last advert and the last connection counts`() {
        val now = 100_000_000L

        assertEquals(1_000L, ReconnectBackoff.unseenForMs(now, lastSeenMillis = now - 1_000L, lastAliveMillis = now - 9_000_000L))
        assertEquals(2_000L, ReconnectBackoff.unseenForMs(now, lastSeenMillis = now - 9_000_000L, lastAliveMillis = now - 2_000L))
    }

    @Test
    fun `an advert after a minute of silence is an arrival`() {
        val now = 100_000_000L

        assertTrue(ReconnectBackoff.isArrival(now, previousSeenMillis = now - 61_000L))
        assertTrue(ReconnectBackoff.isArrival(now, previousSeenMillis = 0L))
    }

    @Test
    fun `a car that keeps advertising is not an arrival each time`() {
        val now = 100_000_000L

        assertFalse(ReconnectBackoff.isArrival(now, previousSeenMillis = now - 2_000L))
        assertFalse(ReconnectBackoff.isArrival(now, previousSeenMillis = now - ReconnectBackoff.ARRIVAL_SILENCE_MS))
    }

    @Test
    fun `an attempt number below one is treated as the first`() {
        assertEquals(ReconnectBackoff.FIRST_DELAY_MS, ReconnectBackoff.delayMs(0))
        assertEquals(ReconnectBackoff.FIRST_DELAY_MS, ReconnectBackoff.delayMs(-3))
    }
}
