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

private fun perDay(
    percentPerHour: Double,
    locale: Locale,
): String {
    val rate = percentPerHour * HOURS_PER_DAY
    return if (rate > 0.0 && (rate * TENTHS).roundToInt() == 0) "<0.1" else "%.1f".format(locale, rate)
}

private const val HOURS_PER_DAY = 24
private const val TENTHS = 10
