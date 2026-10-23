// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import kotlin.test.Test
import kotlin.test.assertEquals

class ActivitySampleTest {
    private fun reading(
        seconds: Long,
        kind: ActivityKind = ActivityKind.CLIMATE,
        on: Boolean = true,
    ) = ActivitySample(seconds * 1000, "car", kind, on)

    @Test
    fun `a long run keeps a reading every ten minutes and every change`() {
        val run = (0L..3600L step 10).map { reading(it) } + reading(3610, on = false) + reading(3620, on = false)
        val kept = run.thinned()
        // The first, one at each ten minutes, the change to off; the repeat of off is dropped.
        assertEquals(listOf(0L, 600L, 1200L, 1800L, 2400L, 3000L, 3600L, 3610L), kept.map { it.timestampMillis / 1000 })
    }

    @Test
    fun `each car and kind is thinned apart`() {
        val mixed =
            listOf(reading(0), reading(5, ActivityKind.SENTRY, on = false), reading(10), reading(15, ActivityKind.SENTRY, on = false))
        assertEquals(listOf(0L, 5L), mixed.thinned().map { it.timestampMillis / 1000 })
    }
}
