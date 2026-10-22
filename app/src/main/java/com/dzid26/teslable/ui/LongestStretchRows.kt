// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.RestStats
import com.dzid26.teslable.core.history.compactDuration

/** The longest parked, sleep and deep sleep stretches each with its hint, when there are any. */
@Composable
internal fun LongestStretchRows(rest: RestStats) {
    if (rest.isEmpty) return
    rest.parked?.let { LongestStretchRow(Stretch(R.string.health_parked_label, it.startMillis, it.endMillis), it.percentPerHour) }
    rest.sleep?.let { LongestStretchRow(Stretch(R.string.health_sleep_label, it.startMillis, it.endMillis), it.percentPerHour) }
    rest.deepSleep?.let { LongestStretchRow(Stretch(R.string.health_deep_sleep_label, it.startMillis, it.endMillis), it.percentPerHour) }
    listOfNotNull(
        R.string.health_parked_hint.takeIf { rest.parked != null },
        R.string.health_sleep_hint.takeIf { rest.sleep != null },
        R.string.health_deep_sleep_hint.takeIf { rest.deepSleep != null },
    ).forEach {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What a "Longest ..." row says: its label and the stretch's first and last moments. */
private class Stretch(
    @StringRes val label: Int,
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
        label = { LabelText(stringResource(stretch.label)) },
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
