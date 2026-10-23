// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.CurrentRest
import com.dzid26.teslable.core.history.RestKind
import com.dzid26.teslable.core.history.RestStats

/**
 * How the car rests: its state now (parked or sleeping, since when) and, over the whole log, the longest
 * parked, sleep and deep sleep stretches and the lowest drain. Shown only when there is anything to say.
 */
@Composable
internal fun DrainStatsCard(
    rest: RestStats,
    current: CurrentRest? = null,
) {
    if (rest.isEmpty && current == null) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(text = stringResource(R.string.stats_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            current?.let { NowRow(it) }
            LongestStretchRows(rest)
            if (rest.extrapolatesToADay()) {
                Text(
                    text = stringResource(R.string.stats_extrapolated_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A figure closing a row's detail line, in bold; with a [tip] it is underlined and explains itself on a tap. */
internal class Figure(
    val text: String,
    val tip: String? = null,
)

@Composable
private fun DetailLine(
    detail: String,
    figure: Figure?,
) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (figure?.tip == null) {
        Text(text = boldPart(detail, figure?.text), style = style, color = color)
    } else {
        Row {
            Text(text = "$detail \u00B7 ", style = style, color = color)
            HintText(figure.text, figure.tip, color, style = style.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** [detail], with the [bold] part (a figure the line carries) in bold; [detail] alone when there is none. */
private fun boldPart(
    detail: String,
    bold: String?,
): AnnotatedString =
    buildAnnotatedString {
        append(detail)
        if (bold != null) {
            append(" \u00B7 ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        }
    }

@Composable
internal fun StatLabel(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
internal fun StatRow(
    label: @Composable () -> Unit,
    value: String,
    detail: String,
    figure: Figure? = null,
    valueTip: String? = null,
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            label()
            DetailLine(detail, figure)
        }
        if (valueTip != null) {
            TipText(
                text = value,
                tip = valueTip,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Whether the car is parked or sleeping now and since when, with the rule for it on a tap of the state. */
@Composable
private fun NowRow(current: CurrentRest) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    val sleeping = current.kind == RestKind.SLEEPING
    StatRow(
        label = { StatLabel(stringResource(R.string.stats_now)) },
        value = stringResource(if (sleeping) R.string.stats_sleeping_now else R.string.stats_parked_now),
        detail = stringResource(R.string.stats_since, formatTime(current.sinceMillis, showDate = true, is24Hour = is24Hour)),
        valueTip = stringResource(if (sleeping) R.string.stats_sleep_hint else R.string.stats_parked_hint),
    )
}
