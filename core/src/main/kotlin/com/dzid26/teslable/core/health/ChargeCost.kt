// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

/**
 * What a charge session cost: the energy the car reports it added times the owner's price per
 * kWh. Null when either is missing, not finite or not above zero, so nothing is shown rather
 * than a zero or a wrong number.
 */
fun chargeSessionCost(
    energyAddedKwh: Float?,
    pricePerKwh: Float?,
): Float? {
    val energy = energyAddedKwh?.takeIf { it.isFinite() && it > 0f }
    val price = pricePerKwh?.takeIf { it.isFinite() && it > 0f }
    return if (energy != null && price != null) energy * price else null
}
