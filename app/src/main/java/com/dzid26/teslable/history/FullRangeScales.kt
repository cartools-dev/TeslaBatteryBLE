package com.dzid26.teslable.history

import com.dzid26.teslable.core.history.BatterySample
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.FullRangeScaleEstimator

/**
 * The one fitted [FullRangeScale] of each car, shared by every card, the health summary and the trip
 * notification. The fit reads a car's whole level history, so it runs once per history change and is
 * reused until the car's samples change.
 */
object FullRangeScales {
    /** Which history a cached fit was made from: the car's samples are appended, so these three tell it apart. */
    private data class Fingerprint(
        val size: Int,
        val firstMillis: Long,
        val lastMillis: Long,
    )

    private class Entry(
        val fingerprint: Fingerprint,
        val scale: FullRangeScale?,
    )

    private val entries = mutableMapOf<String, Entry>()

    /** The scale fitted from one car's [samples], oldest first; null while the levels say too little. */
    @Synchronized
    fun of(samples: List<BatterySample>): FullRangeScale? {
        val first = samples.firstOrNull() ?: return null
        val fingerprint = Fingerprint(samples.size, first.timestampMillis, samples.last().timestampMillis)
        val cached = entries[first.vehicleId]?.takeIf { it.fingerprint == fingerprint }
        return if (cached != null) {
            cached.scale
        } else {
            FullRangeScaleEstimator.estimate(samples).also { entries[first.vehicleId] = Entry(fingerprint, it) }
        }
    }
}
