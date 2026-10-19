// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import com.dzid26.teslable.core.health.chargeSessionCost
import java.util.Locale

/** The energy added in a session and its cost, like "12.5 kWh · $3.75"; the cost is dropped without a price, null without energy. */
internal fun sessionEnergyText(
    energyKwh: Float?,
    pricePerKwh: Float?,
    currencyCode: String? = null,
): String? {
    if (energyKwh == null || energyKwh <= 0f) return null
    val kwh = String.format(Locale.getDefault(), "%.1f kWh", energyKwh)
    val money = chargeSessionCost(energyKwh, pricePerKwh)?.let { currencyFormat(currencyCode).format(it.toDouble()) }
    return listOfNotNull(kwh, money).joinToString(" · ")
}
