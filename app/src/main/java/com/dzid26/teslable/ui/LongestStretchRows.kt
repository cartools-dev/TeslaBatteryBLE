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
import com.dzid26.teslable.core.history.ParkedSpan
import com.dzid26.teslable.core.history.RestStats
import com.dzid26.teslable.core.history.compactDuration
import kotlin.math.roundToInt

/** The longest parked, power down and deep sleep stretches and the lowest drain, each term explained on a tap. */
@Composable
internal fun LongestStretchRows(rest: RestStats) {
    rest.lowestDrain?.let { LowestDrainRow(it) }
    rest.parked?.let {
        LongestStretchRow(
            Stretch(R.string.stats_parked_term, R.string.stats_parked_hint, it.startMillis, it.endMillis),
            rateFigure(it.percentPerHour, it.durationMillis),
        )
    }
    rest.powerDown?.let {
        LongestStretchRow(
            Stretch(R.string.stats_power_down_term, R.string.stats_power_down_hint, it.startMillis, it.endMillis),
            rateFigure(it.percentPerHour, it.durationMillis),
        )
    }
    rest.deepSleep?.let {
        LongestStretchRow(
            Stretch(R.string.stats_deep_sleep_term, R.string.stats_deep_sleep_hint, it.startMillis, it.endMillis),
            deepSleepFigure(it),
        )
    }
}

/** True when a rate shown in the stats was measured over less than a day, so its per-day figure carries a star. */
internal fun RestStats.extrapolatesToADay(): Boolean =
    listOfNotNull(parked?.durationMillis, powerDown?.durationMillis, lowestDrain?.durationMillis, deepSleep?.drop?.spanMillis)
        .any { it < DAY_MILLIS }

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
    figure: Figure?,
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
        figure = figure,
        valueTip = stringResource(R.string.stats_started, formatTime(stretch.startMillis, showDate = true, is24Hour = is24Hour)),
    )
}

/**
 * A deep sleep's drain rate. With no battery read near its ends it is a ceiling (the nearest readings' span holds
 * awake time too), with a tap hint on how long that span was and how much of it may not have been deep sleep.
 */
@Composable
private fun deepSleepFigure(sleep: DeepSleep): Figure? {
    val drop = sleep.drop ?: return null
    val tip =
        if (drop.ceiling) {
            stringResource(
                R.string.stats_drain_hint,
                compactDuration(drop.spanMillis).orEmpty(),
                compactDuration((drop.spanMillis - sleep.durationMillis).coerceAtLeast(0L)).orEmpty(),
            )
        } else {
            null
        }
    return rateFigure(sleep.percentPerHour, drop.spanMillis, tip, drop.ceiling)
}

/** The drain rate as a figure, starred when it was measured over [spanMillis] under a day; null when there is none to give. */
@Composable
private fun rateFigure(
    percentPerHour: Double?,
    spanMillis: Long,
    tip: String? = null,
    ceiling: Boolean = false,
): Figure? {
    val label = drainLabel(percentPerHour, ceiling) ?: return null
    val text = if (spanMillis < DAY_MILLIS) stringResource(R.string.stats_drain_extrapolated, label) else label
    return Figure(text, tip)
}

/**
 * The drain as "0.7%/day", or per week when a day's worth would round to nothing. A level that did not fall
 * over the stretch (within the readings' noise) reads as under 0.1%/week; null when there is no figure to give.
 */
@Composable
private fun drainLabel(
    percentPerHour: Double?,
    ceiling: Boolean = false,
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

private const val DAY_MILLIS = 24 * 60 * 60_000L

private const val HOURS_PER_WEEK = 24 * 7
private const val TENTHS = 10

/** The slowest drain between two readings: the rate leads, with the span it was measured over and when it ended. */
@Composable
private fun LowestDrainRow(span: ParkedSpan) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    StatRow(
        label = {
            Row {
                StatLabel(stringResource(R.string.stats_lowest))
                Spacer(Modifier.width(4.dp))
                HintText(
                    text = stringResource(R.string.stats_lowest_drain_term),
                    hint = stringResource(R.string.stats_lowest_drain_hint),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        value = drainLabel(span.percentPerHour.coerceAtLeast(0.0)).orEmpty() + if (span.durationMillis < DAY_MILLIS) "*" else "",
        detail =
            stringResource(
                R.string.stats_lowest_detail,
                compactDuration(span.durationMillis).orEmpty(),
                formatTime(span.endMillis, showDate = true, is24Hour = is24Hour),
            ),
    )
}
