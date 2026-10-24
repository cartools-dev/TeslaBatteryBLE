// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.dzid26.teslable.core.health.DriveStats
import com.dzid26.teslable.core.units.DistanceUnit
import java.util.Locale

/** The drive window's rates, formatted for the display unit; a rate the data cannot supply is null. */
internal data class DriveRates(
    /** Percent of the battery per distance, such as "0.82" per [symbol]. */
    val percent: String?,
    /** Money per 100 distance units at the home price, such as "$7.50" per 100 [symbol]. */
    val cost: String?,
    val symbol: String,
)

internal fun driveRates(
    stats: DriveStats,
    unit: DistanceUnit,
    currencyCode: String?,
): DriveRates =
    DriveRates(
        percent = stats.percentPerMile?.let { formatPercentHundredths(unit.ratePerUnit(it)) },
        cost = stats.costAtHomePrice?.let { formatMoney(unit.ratePerUnit(it / stats.milesDriven) * DISTANCE_BLOCK, currencyCode) },
        symbol = unit.symbol,
    )

private fun formatPercentHundredths(percent: Float): String = String.format(Locale.getDefault(), "%.2f", percent)

/** The cost is quoted per this many miles or km, so it reads in whole cents. */
private const val DISTANCE_BLOCK = 100f
