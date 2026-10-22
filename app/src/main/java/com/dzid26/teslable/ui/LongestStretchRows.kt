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
import com.dzid26.teslable.core.history.compactDuration
import com.dzid26.teslable.health.HealthSummary

/** The longest parked, sleep and deep sleep stretches, each shown when the logs have one. */
@Composable
internal fun LongestStretchRows(summary: HealthSummary) {
    summary.longestParked?.let { LongestParkedRow(it) }
    summary.longestQuiet?.let { LongestQuietRow(it) }
    summary.longestSleep?.let { LongestSleepRow(it) }
}

@Composable
private fun LongestParkedRow(span: ParkedSpan) {
    LongestStretchRow(
        stretch = Stretch(R.string.health_parked_term, R.string.health_parked_hint, span.startMillis, span.endMillis),
        perHour = span.percentPerHour,
    )
}

@Composable
private fun LongestQuietRow(span: ParkedSpan) {
    LongestStretchRow(
        stretch = Stretch(R.string.health_sleep_term, R.string.health_sleep_hint, span.startMillis, span.endMillis),
        perHour = span.percentPerHour,
    )
}

@Composable
private fun LongestSleepRow(sleep: DeepSleep) {
    LongestStretchRow(
        stretch = Stretch(R.string.health_deep_sleep_term, R.string.health_deep_sleep_hint, sleep.startMillis, sleep.endMillis),
        perHour = sleep.percentPerHour,
    )
}

/** What a "Longest ..." row says: the tapped term, its hint and the stretch's first and last moments. */
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
) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    EstimateRow(
        label = {
            Row {
                LabelText(stringResource(R.string.health_longest))
                Spacer(Modifier.width(4.dp))
                TipText(
                    text = stringResource(stretch.term),
                    tip = stringResource(stretch.hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        value = compactDuration(stretch.endMillis - stretch.startMillis).orEmpty(),
        detail = stringResource(R.string.health_started, formatTime(stretch.startMillis, showDate = true, is24Hour = is24Hour)),
        detailBold = drainPerDay(perHour),
        valueTip = stringResource(R.string.health_ended, formatTime(stretch.endMillis, showDate = true, is24Hour = is24Hour)),
    )
}

/** The drain as "0.7%/day"; null when the battery lost nothing measurable. */
@Composable
private fun drainPerDay(percentPerHour: Double?): String? {
    val perHour = percentPerHour?.takeIf { it > 0.0 } ?: return null
    val perDay = perDay(perHour, LocalLocale.current.platformLocale)
    return perDay?.let { stringResource(R.string.history_parked_rate, it) } ?: stringResource(R.string.history_parked_rate_tiny)
}
