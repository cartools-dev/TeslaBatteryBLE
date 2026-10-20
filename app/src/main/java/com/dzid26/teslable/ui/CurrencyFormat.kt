// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** The ISO 4217 code chosen in Settings; null follows the phone's locale. Provided once per car screen. */
internal val LocalCurrencyCode = compositionLocalOf<String?> { null }

/** The owner's price per kWh, for the finished charge's cost on the history card; null shows the kWh alone. */
internal val LocalPricePerKwh = compositionLocalOf<Float?> { null }

internal const val FALLBACK_CODE = "USD"

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

/** The currencies offered in Settings; the phone's own and any already-saved one are added when missing. */
private val COMMON_CURRENCY_CODES =
    (
        "USD EUR GBP CAD AUD NZD CHF SEK NOK DKK PLN CZK HUF RON BGN UAH TRY ILS AED SAR ZAR INR CNY HKD TWD JPY KRW SGD THB MYR IDR PHP MXN BRL ARS CLP COP"
    ).split(' ')

/**
 * The picker's choices: the common currencies, then the [selected] one (a saved or phone currency outside
 * the list) so it can still be shown, each once and in that order.
 */
internal fun currencyChoices(selected: Currency?): List<Currency> =
    (COMMON_CURRENCY_CODES.mapNotNull { currencyOrNull(it) } + listOfNotNull(selected)).distinct()

/** "EUR (€)": the code and the symbol the phone's locale gives it. */
internal fun currencyLabel(currency: Currency): String = "${currency.currencyCode} (${currency.symbol})"
