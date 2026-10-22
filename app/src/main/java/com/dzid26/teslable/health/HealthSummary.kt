// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.health

import com.dzid26.teslable.core.health.ChargeSession
import com.dzid26.teslable.core.health.EnergyDeltaEstimator
import com.dzid26.teslable.core.health.HealthFusion
import com.dzid26.teslable.core.health.MIN_SWING_PERCENT
import com.dzid26.teslable.core.health.chargeSessions
import com.dzid26.teslable.core.history.BatterySample
import com.dzid26.teslable.core.history.DeepSleep
import com.dzid26.teslable.core.history.DriveSample
import com.dzid26.teslable.core.history.ParkedSpan
import com.dzid26.teslable.core.history.StatusSample
import com.dzid26.teslable.core.history.longestDeepSleep
import com.dzid26.teslable.core.history.longestParked
import com.dzid26.teslable.history.FullRangeScales
import kotlin.math.roundToInt

// Battery-health glue: turns the recorded raw samples into the numbers the car
// view shows. All estimation and session segmentation live in `core/health`;
// this file only selects inputs, counts sessions for the learning gate, and
// flags thin data.

/** Confidence in the estimates, gated on how many charge sessions were seen. */
enum class HealthConfidence { LEARNING, LOW, MEDIUM, HIGH }

/**
 * The health card's data. [sohPercent] is null until the car's factory range
 * (and capacity) are configured, so the card never invents a baseline.
 */
data class HealthSummary(
    val sessions: Int,
    /** Sessions needed before the learning gate opens. */
    val learningTarget: Int,
    val sohPercent: Float?,
    val sohSpreadPoints: Float?,
    val sohMismatch: Boolean,
    val capacityKwh: Float?,
    /** The session's rated constant, kWh per rated mile. */
    val ratedKwhPerMile: Float?,
    val capacitySwingPercent: Float?,
    val fullRangeMiles: Float?,
    /** Level steps the full-range fit used. */
    val fullRangeSteps: Int,
    val confidence: HealthConfidence,
    /** A plain-language caveat when the inputs are thin, else null. */
    val qualityNote: String?,
    /** The longest stretch the car slept through in the logs, by the parked projection's definition; null when none. */
    val longestSleep: DeepSleep? = null,
    /** The longest stretch the car sat parked (odometer unchanged, not charged), holes in the reads included; null when none. */
    val longestParked: ParkedSpan? = null,
)

/**
 * Builds the card's data from one car's samples. [epaRatedRangeMiles] and
 * [newCapacityKwh] are the car's factory figures; while they are unknown the
 * card shows measured capacity only and no SoH percentage.
 */
fun healthSummary(
    samples: List<BatterySample>,
    epaRatedRangeMiles: Float? = null,
    newCapacityKwh: Float? = null,
    drives: List<DriveSample> = emptyList(),
    statuses: List<StatusSample> = emptyList(),
): HealthSummary {
    val sessions = chargeSessions(samples)
    val lastSession = sessions.lastOrNull()
    // Rated miles are the backbone (research: range-as-soc-signal section 6).
    // The one full-range definition is the fitted scale of the level steps
    // (`FullRangeScaleEstimator`); a charge session contributes the rated
    // constant (kWh per rated mile) from the energy and miles it added.
    val scale = FullRangeScales.of(samples)
    val backboneSession = sessions.lastOrNull { it.kwhPerMile != null }
    val energySession =
        sessions.lastOrNull { it.energyAddedKwh != null && it.swingPercent >= MIN_SWING_PERCENT }
    // The range at 100%: the fitted line's value at displayed SOC 100.
    val fullRangeMiles = scale?.milesAtFull
    val ratedKwhPerMile = backboneSession?.kwhPerMile
    val capacityKwh =
        ratedKwhPerMile?.let { constant -> fullRangeMiles?.let { constant * it } }
            ?: energySession?.let { session ->
                EnergyDeltaEstimator
                    .estimate(
                        energyAddedKwh = session.energyAddedKwh!!,
                        socStartPercent = session.startPercent,
                        socEndPercent = session.endPercent,
                    )?.usableCapacityKwh
            }
    val deltaSoh =
        energySession?.let { session ->
            newCapacityKwh?.let { baseline ->
                EnergyDeltaEstimator
                    .estimate(
                        energyAddedKwh = session.energyAddedKwh!!,
                        socStartPercent = session.startPercent,
                        socEndPercent = session.endPercent,
                        newCapacityKwh = baseline,
                    )?.sohPercent
            }
        }
    val ratedSoh =
        fullRangeMiles?.let { full -> epaRatedRangeMiles?.takeIf { it > 0f }?.let { full / it * 100f } }
    val fused = HealthFusion.fuse(ratedSoh, deltaSoh)
    return HealthSummary(
        sessions = sessions.size,
        learningTarget = LEARNING_SESSIONS,
        sohPercent = fused?.sohPercent,
        sohSpreadPoints = fused?.spreadPoints,
        sohMismatch = fused?.mismatch == true,
        capacityKwh = capacityKwh,
        ratedKwhPerMile = ratedKwhPerMile,
        capacitySwingPercent = (backboneSession ?: energySession)?.swingPercent,
        fullRangeMiles = fullRangeMiles,
        fullRangeSteps = scale?.samples ?: 0,
        confidence = confidenceFor(sessions.size),
        qualityNote = qualityNote(backboneSession ?: energySession ?: lastSession),
        longestSleep = longestDeepSleep(samples, drives, statuses, scale),
        longestParked = longestParked(samples, drives, scale),
    )
}

private fun confidenceFor(sessions: Int): HealthConfidence =
    when {
        sessions < LEARNING_SESSIONS -> HealthConfidence.LEARNING
        sessions < MEDIUM_SESSIONS -> HealthConfidence.LOW
        sessions < HIGH_SESSIONS -> HealthConfidence.MEDIUM
        else -> HealthConfidence.HIGH
    }

private fun qualityNote(lastSession: ChargeSession?): String? {
    val swing = lastSession?.swingPercent
    return when {
        swing != null && swing < QUALITY_SWING_PERCENT ->
            "the last charge only swung ${swing.roundToInt()}% SOC"

        else -> null
    }
}

/** Sessions before the "Learning" label gives way to low confidence. */
private const val LEARNING_SESSIONS = 3
private const val MEDIUM_SESSIONS = 10

/** High confidence waits for this many sessions. */
private const val HIGH_SESSIONS = 20

/** A charge swing under this widens the energy-delta error. */
private const val QUALITY_SWING_PERCENT = 20f
