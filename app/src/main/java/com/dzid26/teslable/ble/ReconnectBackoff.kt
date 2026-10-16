// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

/**
 * How long to wait before reconnect attempt number `attempt` (1 for the first retry): the first
 * delay doubled each time up to a ceiling, so a car that is out of range for hours costs a
 * handful of attempts an hour rather than one a minute. A car not seen for [IDLE_AFTER_MS] is
 * treated as away for a long stretch (at work, on a trip) and gets the higher [IDLE_CEILING_MS].
 */
internal object ReconnectBackoff {
    const val FIRST_DELAY_MS = 5_000L
    const val CEILING_MS = 300_000L

    /** No sign of the car for this long: the link is idle. */
    const val IDLE_AFTER_MS = 1_800_000L
    const val IDLE_CEILING_MS = 900_000L

    /** Enough doublings to pass either ceiling: 5 s shifted this often is 5120 s. */
    private const val MAX_DOUBLINGS = 10

    /**
     * How long the car has been out of sight at [now]: since the later of the last advert and the last
     * time its link was alive. A car connected for hours does not advertise, so the advert alone would
     * make it look gone the moment it drops. Zero when neither is known.
     */
    fun unseenForMs(
        now: Long,
        lastSeenMillis: Long,
        lastAliveMillis: Long,
    ): Long {
        val lastSign = maxOf(lastSeenMillis, lastAliveMillis)
        return if (lastSign > 0L) (now - lastSign).coerceAtLeast(0L) else 0L
    }

    /** [unseenForMs] is how long since the car last showed any sign of life: connected, or advertising. */
    fun delayMs(
        attempt: Int,
        unseenForMs: Long = 0L,
    ): Long {
        val ceiling = if (unseenForMs >= IDLE_AFTER_MS) IDLE_CEILING_MS else CEILING_MS
        return (FIRST_DELAY_MS shl (attempt - 1).coerceIn(0, MAX_DOUBLINGS)).coerceAtMost(ceiling)
    }
}
