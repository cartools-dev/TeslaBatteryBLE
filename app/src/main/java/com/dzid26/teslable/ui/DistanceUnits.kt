// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.runtime.compositionLocalOf
import com.dzid26.teslable.core.units.DistanceUnit
import com.dzid26.teslable.core.units.EfficiencyUnit

/** The unit distances are shown in; stored values stay in miles. Provided once per car screen. */
internal val LocalDistanceUnit = compositionLocalOf { DistanceUnit.MILES }

/** How energy per distance reads; provided once per car screen next to [LocalDistanceUnit]. */
internal val LocalEfficiencyUnit = compositionLocalOf { EfficiencyUnit.ENERGY_PER_DISTANCE }
