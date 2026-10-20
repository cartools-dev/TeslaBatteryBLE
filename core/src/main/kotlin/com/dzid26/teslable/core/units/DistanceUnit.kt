// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.units

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A distance unit for display. The car reports, and the app stores, rated
 * range in miles; conversion happens only at the display edge.
 */
enum class DistanceUnit(
    /** Short label shown after a distance, such as "km". */
    val symbol: String,
    private val perMile: Float,
) {
    MILES("mi", 1f),
    KILOMETERS("km", KM_PER_MILE),
    ;

    /** [miles] expressed in this unit. */
    fun fromMiles(miles: Float): Float = miles * perMile

    /** A rate per mile (percent per mile, say) expressed per this unit. */
    fun ratePerUnit(perMileRate: Float): Float = perMileRate / perMile

    /** An energy rate in kWh per mile expressed in kWh per this unit. */
    fun energyPerUnit(kwhPerMile: Float): Float = kwhPerMile / perMile

    /**
     * [miles] shown in this unit with its symbol, such as "342.0 km" or "212 mi".
     * [decimals] is 0 or 1; the separator is the caller's, so the app can pass the locale's.
     * The gap is a no-break space so the number never wraps from its unit.
     */
    fun format(
        miles: Float,
        decimals: Int = 0,
        decimalSeparator: Char = '.',
    ): String {
        val value = fromMiles(miles)
        val number =
            if (decimals <= 0) {
                value.roundToInt().toString()
            } else {
                val tenths = abs((value * 10f).roundToInt())
                val sign = if (value < 0f) "-" else ""
                "$sign${tenths / 10}$decimalSeparator${tenths % 10}"
            }
        return "$number\u00A0$symbol"
    }
}

private const val KM_PER_MILE = 1.609344f
