// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.dzid26.teslable.core.health.DriveStats
import com.dzid26.teslable.core.units.DistanceUnit
import java.util.Locale

/** The rate on the footer's first line: what the figure is and its parts, formatted for the display unit. */
internal sealed interface DriveRate {
    /** Money per 100 distance units, such as "$7.50" per "100km". */
    data class Cost(
        val money: String,
        val symbol: String,
    ) : DriveRate

    /** Percent of the battery per distance, such as "0.82" per "km". */
    data class Percent(
        val percent: String,
        val symbol: String,
    ) : DriveRate
}

/** The cost per distance at the home price; without a price the percent per distance; null when neither is known. */
internal fun driveRate(
    stats: DriveStats,
    unit: DistanceUnit,
    currencyCode: String?,
): DriveRate? {
    val costPerMile = stats.costAtHomePrice?.let { it / stats.milesDriven }
    val percentPerMile = stats.percentPerMile
    return when {
        costPerMile != null -> DriveRate.Cost(formatMoney(unit.ratePerUnit(costPerMile) * DISTANCE_BLOCK, currencyCode), unit.symbol)
        percentPerMile != null -> DriveRate.Percent(formatPercentHundredths(unit.ratePerUnit(percentPerMile)), unit.symbol)
        else -> null
    }
}

private fun formatPercentHundredths(percent: Float): String = String.format(Locale.getDefault(), "%.2f", percent)

/** The cost is quoted per this many miles or km, so it reads in whole cents. */
private const val DISTANCE_BLOCK = 100f
