// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

/** The owner set the electricity price to [pricePerKwh] (null clears it) at [atMillis]. */
data class PriceChange(
    val atMillis: Long,
    val pricePerKwh: Float?,
)
