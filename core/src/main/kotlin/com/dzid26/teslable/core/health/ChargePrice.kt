// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.health

import com.dzid26.teslable.core.history.BatterySample

private const val EDIT_WINDOW_MILLIS = 5 * 60_000L
private const val MAX_CHANGES = 50

/**
 * [changes] plus [change]. Typing a price changes it with every key, so the earlier changes of that edit,
 * those within five minutes before [change], are replaced rather than kept. Only the newest fifty stay.
 */
fun recordPriceChange(
    changes: List<PriceChange>,
    change: PriceChange,
): List<PriceChange> =
    (changes.filter { it.atMillis < change.atMillis - EDIT_WINDOW_MILLIS } + change)
        .sortedBy { it.atMillis }
        .takeLast(MAX_CHANGES)

/**
 * The price a charge that ended at [chargeEndMillis] was billed at. A price change takes effect from the
 * start of the latest charge when it was made: the current one while charging, else the last finished one.
 * So a price set mid-charge covers that whole charge, and one set while parked also re-prices the charge
 * just finished. The newest change in effect by the charge's start wins; null when none is, or it cleared
 * the price.
 */
fun priceForCharge(
    changes: List<PriceChange>,
    samples: List<BatterySample>,
    chargeEndMillis: Long,
): Float? {
    val chargeStart = chargeStartOf(samples, chargeEndMillis)
    return changes
        .filter { change -> (chargeStartsBy(samples, change.atMillis).lastOrNull() ?: Long.MIN_VALUE) <= chargeStart }
        .maxByOrNull { it.atMillis }
        ?.pricePerKwh
}
