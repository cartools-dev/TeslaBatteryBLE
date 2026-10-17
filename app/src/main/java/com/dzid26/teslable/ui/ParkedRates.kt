// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.dzid26.teslable.core.history.DischargeProjection
import java.util.Locale
import kotlin.math.roundToInt

/** The parked drain rates as shown, percent per day with one decimal. */
internal class ParkedRates(
    /** The rate the line follows: deep sleep when the car has slept enough to tell, else the mixed one. */
    val main: String,
    /** The mixed rate, only when [main] is the deep-sleep rate and the two differ once rounded. */
    val mixed: String?,
)

/**
 * The labels for a parked projection's rates. A rate above zero but under half a
 * tenth reads "<0.1" instead of a misleading "0.0"; when deep sleep and mixed
 * round to the same figure only one is kept.
 */
internal fun parkedRates(
    projection: DischargeProjection,
    locale: Locale,
): ParkedRates {
    val main = perDay(projection.percentPerHour, locale)
    val mixed = projection.mixedPercentPerHour?.let { perDay(it, locale) }
    return ParkedRates(main, mixed?.takeIf { it != main })
}

private fun perDay(
    percentPerHour: Double,
    locale: Locale,
): String {
    val rate = percentPerHour * HOURS_PER_DAY
    return if (rate > 0.0 && (rate * TENTHS).roundToInt() == 0) "<0.1" else "%.1f".format(locale, rate)
}

private const val HOURS_PER_DAY = 24
private const val TENTHS = 10
