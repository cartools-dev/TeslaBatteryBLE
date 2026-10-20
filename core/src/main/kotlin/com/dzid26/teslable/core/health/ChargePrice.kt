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
 * The price a charge that ended at [chargeEndMillis] was billed at. A price change takes effect from the end
 * of the last charge that had finished when it was made, so one made mid-charge covers that whole charge,
 * and one made while parked starts with the next charge and leaves the finished ones alone. The newest
 * change in effect by [chargeEndMillis] wins; null when none is, or it cleared the price.
 */
fun priceForCharge(
    changes: List<PriceChange>,
    samples: List<BatterySample>,
    chargeEndMillis: Long,
): Float? =
    changes
        .filter { change -> (chargeEndsBy(samples, change.atMillis).lastOrNull() ?: Long.MIN_VALUE) < chargeEndMillis }
        .maxByOrNull { it.atMillis }
        ?.pricePerKwh
