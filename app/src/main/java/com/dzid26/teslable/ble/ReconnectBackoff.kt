// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

/**
 * How long to wait before reconnect attempt number [attempt] (1 for the first retry): the first
 * delay doubled each time up to a ceiling, so a car that is out of range for hours costs a
 * handful of attempts an hour rather than one a minute.
 */
internal object ReconnectBackoff {
    const val FIRST_DELAY_MS = 5_000L
    const val CEILING_MS = 300_000L

    /** [FIRST_DELAY_MS] doubled this often reaches the ceiling: 5, 10, 20, 40, 80, 160, 300 s. */
    private const val MAX_DOUBLINGS = 6

    fun delayMs(attempt: Int): Long = (FIRST_DELAY_MS shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(CEILING_MS)
}
