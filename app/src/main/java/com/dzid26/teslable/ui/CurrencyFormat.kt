// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** The ISO 4217 code chosen in Settings; null follows the phone's locale. Provided once per car screen. */
internal val LocalCurrencyCode = compositionLocalOf<String?> { null }

private const val FALLBACK_CODE = "USD"

/**
 * The currency for an ISO 4217 [code] such as "eur" or " usd ", or null when it is blank, not a code, or
 * not real money: pseudo codes (XXX, XTS) and funds or metals (XAU) have no fraction digits (negative).
 */
internal fun currencyOrNull(code: String?): Currency? =
    runCatching { Currency.getInstance(code?.trim()?.uppercase()) }.getOrNull()?.takeIf { it.defaultFractionDigits >= 0 }

/** The phone's own currency, or null for a locale without a region (plain "en", root). */
internal fun localeCurrencyOrNull(): Currency? =
    runCatching { Currency.getInstance(Locale.getDefault()) }.getOrNull()?.takeIf { it.defaultFractionDigits >= 0 }

/** A money formatter for [code]; blank or invalid follows the phone's currency, and USD when it has none. */
internal fun currencyFormat(code: String?): NumberFormat =
    NumberFormat.getCurrencyInstance().apply {
        val chosen = currencyOrNull(code) ?: localeCurrencyOrNull() ?: Currency.getInstance(FALLBACK_CODE)
        currency = chosen
        // Pinned explicitly: not every platform formatter resets its digits when the currency changes.
        minimumFractionDigits = chosen.defaultFractionDigits
        maximumFractionDigits = chosen.defaultFractionDigits
    }
