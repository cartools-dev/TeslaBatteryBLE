// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable

import android.content.Context
import android.icu.util.LocaleData
import android.icu.util.ULocale
import com.dzid26.teslable.core.units.DistanceUnit

/** Which distance unit the owner wants: follow the phone's region, or force one. */
enum class UnitPreference(
    val label: String,
) {
    SYSTEM("System default"),
    METRIC("Metric (km)"),
    IMPERIAL("Imperial (mi)"),
    ;

    /** The unit to display; [SYSTEM] follows the locale's measurement system (SI uses km, US and UK use miles). */
    fun resolve(): DistanceUnit =
        when (this) {
            METRIC -> DistanceUnit.KILOMETERS
            IMPERIAL -> DistanceUnit.MILES
            SYSTEM ->
                if (LocaleData.getMeasurementSystem(ULocale.getDefault()) == LocaleData.MeasurementSystem.SI) {
                    DistanceUnit.KILOMETERS
                } else {
                    DistanceUnit.MILES
                }
        }
}

/** The owner's unit preference. Display only: stored and computed values stay in miles. */
class UnitStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun preference(): UnitPreference =
        UnitPreference.entries.firstOrNull { it.name == prefs.getString(KEY_UNIT, null) } ?: UnitPreference.SYSTEM

    fun setPreference(preference: UnitPreference) {
        prefs.edit().putString(KEY_UNIT, preference.name).apply()
    }

    private companion object {
        const val PREFS = "teslable_units"
        const val KEY_UNIT = "distance_unit"
    }
}
