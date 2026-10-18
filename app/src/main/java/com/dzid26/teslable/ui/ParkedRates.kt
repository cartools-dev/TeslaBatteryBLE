// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.dzid26.teslable.core.history.DischargeProjection
import java.util.Locale
import kotlin.math.roundToInt

/** The parked drain rates as shown, percent per day with one decimal. */
internal class ParkedRates(
    /** The rate the line follows: deep sleep when the car has slept enough to tell, else the mixed one. */
    val main: String,
    /** True when [main] is the deep-sleep rate. */
    val asleep: Boolean,
)

/**
 * The label for a parked projection's rate: only the deep-sleep rate when the car
 * has slept enough to tell, else the mixed one. A rate above zero but under half
 * a tenth reads "<0.1" instead of a misleading "0.0".
 */
internal fun parkedRates(
    projection: DischargeProjection,
    locale: Locale,
): ParkedRates = ParkedRates(perDay(projection.percentPerHour, locale), projection.mixedPercentPerHour != null)

/** How far ahead a projection looks, as a whole number of hours or days. */
internal class ParkedHorizon(
    val amount: Int,
    val days: Boolean,
)

/** The horizon in whole days from a day on, else whole hours (it is already a round figure). */
internal fun parkedHorizon(horizonMillis: Long): ParkedHorizon =
    if (horizonMillis >= DAY_MILLIS) {
        ParkedHorizon((horizonMillis / DAY_MILLIS).toInt(), days = true)
    } else {
        ParkedHorizon((horizonMillis / HOUR_MILLIS).toInt(), days = false)
    }

private fun perDay(
    percentPerHour: Double,
    locale: Locale,
): String {
    val rate = percentPerHour * HOURS_PER_DAY
    return if (rate > 0.0 && (rate * TENTHS).roundToInt() == 0) "<0.1" else "%.1f".format(locale, rate)
}

private const val HOURS_PER_DAY = 24
private const val HOUR_MILLIS = 60 * 60_000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS
private const val TENTHS = 10
