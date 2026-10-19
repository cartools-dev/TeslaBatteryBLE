// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.common.Fill

@Composable
internal fun rememberBatteryLines(
    lineColor: Color,
    chargingColor: Color,
    parkedMuted: Boolean,
): Map<SeriesKind, LineCartesianLayer.Line> {
    val measured = lineFor(lineColor)
    val measuredCharging = lineFor(chargingColor)
    val dots = dotsLine(lineColor)
    val dotsCharging = dotsLine(chargingColor)
    val chargeProjection = dashedLine(chargingColor.copy(alpha = CHARGE_LINE_ALPHA))
    val dischargeProjection =
        dashedLine(lineColor.copy(alpha = if (parkedMuted) DISCHARGE_LINE_MUTED_ALPHA else DISCHARGE_LINE_ALPHA))
    return remember(measured, measuredCharging, dots, dotsCharging, chargeProjection, dischargeProjection) {
        mapOf(
            SeriesKind.MEASURED to measured,
            SeriesKind.MEASURED_CHARGING to measuredCharging,
            SeriesKind.DOTS to dots,
            SeriesKind.DOTS_CHARGING to dotsCharging,
            SeriesKind.CHARGE_PROJECTION to chargeProjection,
            SeriesKind.DISCHARGE_PROJECTION to dischargeProjection,
        )
    }
}

@Composable
private fun lineFor(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
        stroke = remember { LineCartesianLayer.LineStroke.Continuous(thickness = lineWidth, cap = StrokeCap.Round) },
    )

/** A line-less series: only its points show, one per measurement. */
@Composable
private fun dotsLine(color: Color): LineCartesianLayer.Line {
    val point = rememberPoint(color, size = dotSize)
    return LineCartesianLayer.rememberLine(
        fill = remember { LineCartesianLayer.LineFill.single(Fill(Color.Transparent)) },
        pointProvider = remember(point) { LineCartesianLayer.PointProvider.single(point) },
    )
}

@Composable
private fun dashedLine(color: Color): LineCartesianLayer.Line =
    LineCartesianLayer.rememberLine(
        fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
        stroke =
            remember {
                LineCartesianLayer.LineStroke.Dashed(thickness = projectionWidth, dashLength = dashLength, gapLength = dashGap)
            },
    )

private val lineWidth = 1.5.dp
private val projectionWidth = 2.dp
private val dashLength = 8.dp
private val dashGap = 6.dp
private val dotSize = 4.dp

private const val CHARGE_LINE_ALPHA = 0.65f
private const val DISCHARGE_LINE_ALPHA = 0.65f
private const val DISCHARGE_LINE_MUTED_ALPHA = 0.35f
