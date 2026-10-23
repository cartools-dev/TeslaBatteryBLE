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

/**
 * Whether [next] is worth keeping after [last], the last one kept for the same car and kind: the first reading, one
 * that changes the state, or one a thinning interval after [last]. While Sentry or the climate runs the car is read
 * every ten seconds, so keeping them all would fill the history with copies of one state; every state change and a
 * reading each [ACTIVITY_KEEP_MILLIS] still cover a run, more densely than the carried-in reading a gap looks back
 * for, and the oldest readings are never pushed out by the newest copies.
 */
fun keepActivity(
    last: ActivitySample?,
    next: ActivitySample,
): Boolean = last == null || next.on != last.on || next.timestampMillis - last.timestampMillis >= ACTIVITY_KEEP_MILLIS

/** The readings of [this], oldest first, thinned by [keepActivity] per car and kind. */
fun List<ActivitySample>.thinned(): List<ActivitySample> {
    val lastKept = mutableMapOf<Pair<String, ActivityKind>, ActivitySample>()
    return filter { sample ->
        val key = sample.vehicleId to sample.kind
        keepActivity(lastKept[key], sample).also { if (it) lastKept[key] = sample }
    }
}

/** Kept readings of one state are at most this far apart: under the 16 minutes a reading may stand for a gap. */
private const val ACTIVITY_KEEP_MILLIS = 10 * 60_000L
