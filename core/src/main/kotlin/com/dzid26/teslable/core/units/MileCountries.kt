// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.core.units

private val MILE_COUNTRIES = setOf("US", "GB", "LR", "MM")

/** True for the ISO 3166 [country] codes whose roads use miles; false for anything else, including blank. */
fun countryUsesMiles(country: String): Boolean = country.uppercase() in MILE_COUNTRIES
