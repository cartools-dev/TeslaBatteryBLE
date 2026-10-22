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
import com.dzid26.teslable.core.history.RestStats
import com.dzid26.teslable.core.history.compactDuration

/** The longest parked, sleep and deep sleep stretches, each with its term explained on a tap, when there are any. */
@Composable
internal fun LongestStretchRows(rest: RestStats) {
    rest.parked?.let {
        LongestStretchRow(
            Stretch(R.string.health_parked_term, R.string.health_parked_hint, it.startMillis, it.endMillis),
            it.percentPerHour,
        )
    }
    rest.sleep?.let {
        LongestStretchRow(Stretch(R.string.health_sleep_term, R.string.health_sleep_hint, it.startMillis, it.endMillis), it.percentPerHour)
    }
    rest.deepSleep?.let {
        LongestStretchRow(
            Stretch(R.string.health_deep_sleep_term, R.string.health_deep_sleep_hint, it.startMillis, it.endMillis),
            it.percentPerHour,
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
) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    EstimateRow(
        label = {
            Row {
                LabelText(stringResource(R.string.health_longest))
                Spacer(Modifier.width(4.dp))
                HintText(
                    text = stringResource(stretch.term),
                    hint = stringResource(stretch.hint),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        value = compactDuration(stretch.endMillis - stretch.startMillis).orEmpty(),
        detail = stringResource(R.string.health_ended, formatTime(stretch.endMillis, showDate = true, is24Hour = is24Hour)),
        detailBold = drainPerDay(perHour),
        valueTip = stringResource(R.string.health_started, formatTime(stretch.startMillis, showDate = true, is24Hour = is24Hour)),
    )
}

/** The drain as "0.7%/day"; null when the battery lost nothing measurable. */
@Composable
private fun drainPerDay(percentPerHour: Double?): String? {
    val perHour = percentPerHour?.takeIf { it > 0.0 } ?: return null
    val perDay = perDay(perHour, LocalLocale.current.platformLocale)
    return perDay?.let { stringResource(R.string.history_parked_rate, it) } ?: stringResource(R.string.history_parked_rate_tiny)
}
