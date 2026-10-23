// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.DeepSleep
import com.dzid26.teslable.core.history.RestStats
import com.dzid26.teslable.core.history.SleepDrop
import com.dzid26.teslable.core.history.compactDuration
import kotlin.math.roundToInt

/** The longest parked, sleep and deep sleep stretches, each with its term explained on a tap, when there are any. */
@Composable
internal fun LongestStretchRows(rest: RestStats) {
    rest.parked?.let {
        LongestStretchRow(
            Stretch(R.string.stats_parked_term, R.string.stats_parked_hint, it.startMillis, it.endMillis),
            it.percentPerHour,
        )
    }
    rest.sleep?.let {
        LongestStretchRow(Stretch(R.string.stats_sleep_term, R.string.stats_sleep_hint, it.startMillis, it.endMillis), it.percentPerHour)
    }
    rest.deepSleep?.let {
        LongestStretchRow(
            Stretch(R.string.stats_deep_sleep_term, R.string.stats_deep_sleep_hint, it.startMillis, it.endMillis),
            it.percentPerHour,
            ceiling = it.drop?.ceiling == true,
            drainTip = it.drop?.let { drop -> drainTip(it, drop) },
        )
    }
}

/** What a "Longest ..." row says: the underlined term, its hint and the stretch's first and last moments. */
private class Stretch(
    @StringRes val term: Int,
    @StringRes val hint: Int,
    val startMillis: Long,
    val endMillis: Long,
)

@Composable
private fun LongestStretchRow(
    stretch: Stretch,
    perHour: Double?,
    ceiling: Boolean = false,
    drainTip: String? = null,
) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    StatRow(
        label = {
            Row {
                StatLabel(stringResource(R.string.stats_longest))
                Spacer(Modifier.width(4.dp))
                HintText(
                    text = stringResource(stretch.term),
                    hint = stringResource(stretch.hint),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        value = compactDuration(stretch.endMillis - stretch.startMillis).orEmpty(),
        detail = stringResource(R.string.stats_ended, formatTime(stretch.endMillis, showDate = true, is24Hour = is24Hour)),
        figure = drainLabel(perHour, ceiling)?.let { Figure(it, drainTip) },
        valueTip = stringResource(R.string.stats_started, formatTime(stretch.startMillis, showDate = true, is24Hour = is24Hour)),
    )
}

/** Where a deep sleep's drain figure comes from: the two levels, the span between them and how much of it was not deep sleep. */
@Composable
private fun drainTip(
    sleep: DeepSleep,
    drop: SleepDrop,
): String {
    val locale = LocalLocale.current.platformLocale
    return stringResource(
        R.string.stats_drain_hint,
        "%.1f".format(locale, drop.fromPercent),
        "%.1f".format(locale, drop.toPercent),
        compactDuration(drop.spanMillis).orEmpty(),
        compactDuration((drop.spanMillis - sleep.durationMillis).coerceAtLeast(0L)).orEmpty(),
    )
}

/**
 * The drain as "0.7%/day", or per week when a day's worth would round to nothing. A level that did not fall
 * over the stretch (within the readings' noise) reads as under 0.1%/week; null when there is no figure to give.
 */
@Composable
private fun drainLabel(
    percentPerHour: Double?,
    ceiling: Boolean,
): String? {
    val perHour = percentPerHour ?: return null
    val locale = LocalLocale.current.platformLocale
    val perDay = perDay(perHour, locale)
    val perWeek = perHour * HOURS_PER_WEEK
    val tiny = perHour <= 0.0 || (perWeek * TENTHS).roundToInt() == 0
    val figure =
        if (perDay != null) {
            stringResource(R.string.history_parked_rate, perDay)
        } else {
            stringResource(R.string.stats_drain_week, "%.1f".format(locale, perWeek))
        }
    return when {
        tiny -> stringResource(R.string.stats_drain_tiny_week)
        ceiling -> stringResource(R.string.stats_drain_ceiling, figure)
        else -> figure
    }
}

private const val HOURS_PER_WEEK = 24 * 7
private const val TENTHS = 10
