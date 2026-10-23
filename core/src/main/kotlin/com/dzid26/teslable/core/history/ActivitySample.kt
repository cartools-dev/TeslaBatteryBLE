// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.core.history

import com.dzid26.teslable.protocol.climateOn
import com.dzid26.teslable.protocol.sentryOn

/** What keeps a locked car busy: Sentry mode or the climate. */
enum class ActivityKind { SENTRY, CLIMATE }

/**
 * One reading of whether [kind] was running, derived from a logged closures or climate [BleRecord] (ADR-0008).
 * Like [StatusSample] the time is the phone's clock at receipt.
 */
data class ActivitySample(
    val timestampMillis: Long,
    val vehicleId: String,
    val kind: ActivityKind,
    val on: Boolean,
)

/** The activity reading a closures or climate record carries; null for any other record or one with no time. */
fun BleRecord.toActivitySample(vehicleId: String): ActivitySample? {
    val time = device_timestamp?.toEpochMilli() ?: return null
    val closures = closures_state
    val climate = climate_state
    return when {
        closures != null -> ActivitySample(time, vehicleId, ActivityKind.SENTRY, closures.sentryOn)
        climate != null -> ActivitySample(time, vehicleId, ActivityKind.CLIMATE, climate.climateOn)
        else -> null
    }
}
