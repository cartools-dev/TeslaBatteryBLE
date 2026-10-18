// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable

import android.content.Context

/** The owner's electricity price per kWh and the currency it is in; used to price charge sessions. */
class EnergyPriceStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The price per kWh, or null while none is set. */
    fun pricePerKwh(): Float? = if (prefs.contains(KEY_PRICE)) prefs.getFloat(KEY_PRICE, 0f).takeIf { it > 0f } else null

    /** Saves [price]; null or a non-positive value clears it. */
    fun setPricePerKwh(price: Float?) {
        prefs
            .edit()
            .apply {
                if (price != null && price.isFinite() && price > 0f) putFloat(KEY_PRICE, price) else remove(KEY_PRICE)
            }.apply()
    }

    /** The ISO 4217 code the owner chose, or null to follow the phone's locale. */
    fun currencyCode(): String? = prefs.getString(KEY_CURRENCY, null)

    /** Saves [code]; null clears it back to the locale's currency. */
    fun setCurrencyCode(code: String?) {
        prefs.edit().apply { if (code != null) putString(KEY_CURRENCY, code) else remove(KEY_CURRENCY) }.apply()
    }

    private companion object {
        const val PREFS = "teslable_energy_price"
        const val KEY_PRICE = "price_per_kwh"
        const val KEY_CURRENCY = "currency_code"
    }
}
