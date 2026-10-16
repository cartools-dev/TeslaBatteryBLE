// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import com.dzid26.teslable.core.health.chargeSessionCost
import com.tesla.generated.carserver.vehicle.ChargeState
import java.text.NumberFormat
import java.util.Locale

/** The energy added this session and its cost, like "12.5 kWh, $3.75"; null without a price. */
internal fun sessionCostText(
    charge: ChargeState,
    pricePerKwh: Float?,
): String? {
    val energy = charge.charge_energy_added
    val cost = chargeSessionCost(energy, pricePerKwh)
    if (energy == null || cost == null) return null
    val money = NumberFormat.getCurrencyInstance().format(cost.toDouble())
    return String.format(Locale.getDefault(), "%.1f kWh · %s", energy, money)
}
