// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable

import android.content.Context
import android.icu.util.LocaleData
import android.icu.util.ULocale
import android.os.Build
import androidx.annotation.StringRes
import com.dzid26.teslable.core.units.DistanceUnit
import com.dzid26.teslable.core.units.EfficiencyUnit
import com.dzid26.teslable.core.units.countryUsesMiles
import java.util.Locale

/** Which distance unit the owner wants: follow the phone's region, or force one. */
enum class UnitPreference(
    @StringRes val label: Int,
) {
    SYSTEM(R.string.units_system),
    METRIC(R.string.units_metric),
    IMPERIAL(R.string.units_imperial),
    ;

    /** The unit to display; [SYSTEM] follows the locale's measurement system (SI uses km, US and UK use miles). */
    fun resolve(): DistanceUnit =
        when (this) {
            METRIC -> DistanceUnit.KILOMETERS
            IMPERIAL -> DistanceUnit.MILES
            SYSTEM -> if (localeUsesMiles()) DistanceUnit.MILES else DistanceUnit.KILOMETERS
        }
}

/** ICU knows the measurement system from API 28; older phones fall back to the countries that drive in miles. */
private fun localeUsesMiles(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        LocaleData.getMeasurementSystem(ULocale.getDefault()) != LocaleData.MeasurementSystem.SI
    } else {
        countryUsesMiles(Locale.getDefault().country)
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

    /** How energy per distance reads; Wh per distance (the car's own display) until chosen. */
    fun efficiency(): EfficiencyUnit =
        EfficiencyUnit.entries.firstOrNull { it.name == prefs.getString(KEY_EFFICIENCY, null) }
            ?: EfficiencyUnit.ENERGY_PER_DISTANCE

    fun setEfficiency(unit: EfficiencyUnit) {
        prefs.edit().putString(KEY_EFFICIENCY, unit.name).apply()
    }

    private companion object {
        const val PREFS = "teslable_units"
        const val KEY_UNIT = "distance_unit"
        const val KEY_EFFICIENCY = "efficiency_unit"
    }
}
