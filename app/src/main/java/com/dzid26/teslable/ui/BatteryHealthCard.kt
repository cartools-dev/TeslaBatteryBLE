// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.DeepSleep
import com.dzid26.teslable.core.history.compactDuration
import com.dzid26.teslable.health.HealthConfidence
import com.dzid26.teslable.health.HealthSummary
import java.text.DecimalFormatSymbols
import kotlin.math.roundToInt

/**
 * Battery health, first cut: measured capacity and full range from the car's
 * own readings, with a confidence pill and the session-count learning gate.
 * No SoH percentage until the factory range is configured, and never any
 * cell-imbalance, pack-temperature, or lifespan claims.
 */
@Composable
internal fun BatteryHealthCard(summary: HealthSummary) {
    val unit = LocalDistanceUnit.current
    val efficiency = LocalEfficiencyUnit.current
    val separator = remember { DecimalFormatSymbols.getInstance().decimalSeparator }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Battery health",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                ConfidencePill(summary.confidence)
            }
            Spacer(Modifier.height(8.dp))

            summary.sohPercent?.let { soh ->
                EstimateRow(
                    label = { LabelText("Capacity (SoH)") },
                    value = "${soh.roundToInt()}%",
                    detail =
                        if (summary.sohMismatch) {
                            "rated-range and energy-delta sources differ"
                        } else {
                            "rated-range + energy-delta estimate"
                        },
                )
            }
            summary.capacityKwh?.let { kwh ->
                EstimateRow(
                    label = { LabelText("Usable capacity") },
                    value = "~${(kwh * 10).roundToInt() / 10f}kWh",
                    detail =
                        summary.ratedKwhPerMile?.let { constant ->
                            efficiency.format(constant, unit, separator)?.let { "rated constant $it" }
                        } ?: "energy added over the last charge",
                )
            }
            summary.fullRangeMiles?.let { miles ->
                EstimateRow(
                    label = { LabelText("Full range") },
                    value = "~${unit.format(miles)}",
                    detail = "fitted from ${summary.fullRangeSteps} percent steps",
                )
            }
            summary.longestSleep?.let { LongestSleepRow(it) }
            if (summary.capacityKwh == null && summary.fullRangeMiles == null && summary.sohPercent == null) {
                Text(
                    text = "No readings yet. Charge once with the app open, then read the battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = confidenceLine(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary.qualityNote?.let { note ->
                Text(
                    text = "Thin data: $note.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    "Measured on this phone from your own readings. A state-of-health " +
                        "percentage appears once the car's factory range is set.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LongestSleepRow(sleep: DeepSleep) {
    val is24Hour = LocalIs24Hour.current ?: DateFormat.is24HourFormat(LocalContext.current)
    EstimateRow(
        label = {
            Row {
                LabelText(stringResource(R.string.health_longest))
                Spacer(Modifier.width(4.dp))
                TipText(
                    text = stringResource(R.string.health_deep_sleep_term),
                    tip = stringResource(R.string.health_deep_sleep_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        value = compactDuration(sleep.durationMillis).orEmpty(),
        detail = stringResource(R.string.health_deep_sleep_detail, formatTime(sleep.startMillis, showDate = true, is24Hour = is24Hour)),
        detailBold = sleepRate(sleep),
        valueTip = stringResource(R.string.health_deep_sleep_ended, formatTime(sleep.endMillis, showDate = true, is24Hour = is24Hour)),
    )
}

/** The drain over the sleep as "0.7%/day"; null when the battery lost nothing measurable. */
@Composable
private fun sleepRate(sleep: DeepSleep): String? {
    val perHour = sleep.percentPerHour?.takeIf { it > 0.0 } ?: return null
    val perDay = perDay(perHour, LocalLocale.current.platformLocale)
    return perDay?.let { stringResource(R.string.history_parked_rate, it) } ?: stringResource(R.string.history_parked_rate_tiny)
}

/** [detail], with the [bold] part (a figure the line carries) in bold; [detail] alone when there is none. */
private fun boldPart(
    detail: String,
    bold: String?,
): AnnotatedString =
    buildAnnotatedString {
        append(detail)
        if (bold != null) {
            append(" · ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        }
    }

@Composable
private fun LabelText(text: String) = Text(text, style = MaterialTheme.typography.bodySmall)

@Composable
private fun EstimateRow(
    label: @Composable () -> Unit,
    value: String,
    detail: String,
    detailBold: String? = null,
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
            Text(
                text = boldPart(detail, detailBold),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

@Composable
private fun ConfidencePill(confidence: HealthConfidence) {
    val (container, content, label) =
        when (confidence) {
            HealthConfidence.LEARNING ->
                Triple(
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                    "Learning",
                )

            HealthConfidence.LOW ->
                Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "Low confidence",
                )

            HealthConfidence.MEDIUM ->
                Triple(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    "Medium confidence",
                )

            HealthConfidence.HIGH ->
                Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    "High confidence",
                )
        }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun confidenceLine(summary: HealthSummary): String =
    when (summary.confidence) {
        HealthConfidence.LEARNING ->
            "Learning — ${summary.sessions} of ${summary.learningTarget} charge sessions recorded."

        HealthConfidence.LOW ->
            "${summary.sessions} charge sessions recorded; confidence stays low until more."

        HealthConfidence.MEDIUM -> "${summary.sessions} charge sessions recorded."

        HealthConfidence.HIGH -> "${summary.sessions} charge sessions recorded."
    }
