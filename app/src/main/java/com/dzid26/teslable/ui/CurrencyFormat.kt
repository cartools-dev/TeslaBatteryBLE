// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.util.Currency

/** The ISO 4217 code chosen in Settings; null follows the phone's locale. Provided once per car screen. */
internal val LocalCurrencyCode = compositionLocalOf<String?> { null }

/** The currency for an ISO 4217 [code] such as "eur", or null when it is blank or not a real code. */
internal fun currencyOrNull(code: String?): Currency? = runCatching { Currency.getInstance(code?.trim()?.uppercase()) }.getOrNull()

/** A money formatter for [code]; an unknown or missing code keeps the locale's own currency. */
internal fun currencyFormat(code: String?): NumberFormat =
    NumberFormat.getCurrencyInstance().apply { currencyOrNull(code)?.let { currency = it } }

/** The phone's own currency code, shown until the owner picks one. */
internal fun localeCurrencyCode(): String =
    NumberFormat
        .getCurrencyInstance()
        .currency
        ?.currencyCode
        .orEmpty()
