// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

/** The car's rated-range step in miles, as seen in the logs. */
internal const val RANGE_STEP_MILES = 0.082f

/** sqrt(12): a uniform step's standard deviation is the step over this. */
internal const val UNIFORM_STEP_DIVISOR = 3.4641016

/** Standard deviation of one level reading, percent: a uniformly rounded step of the range, or of the whole level. */
internal fun readingResolution(scale: FullRangeScale?): Double {
    val stepPercent =
        if (scale != null && scale.pinned) {
            (RANGE_STEP_MILES / scale.milesPerPercent).toDouble()
        } else {
            1.0
        }
    return stepPercent / UNIFORM_STEP_DIVISOR
}
