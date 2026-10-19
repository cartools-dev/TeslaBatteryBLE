// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.core.history.BatterySample
import com.dzid26.teslable.core.history.ChargeProjection
import com.dzid26.teslable.core.history.DischargeProjection
import com.dzid26.teslable.core.history.FullRangeScale
import com.dzid26.teslable.core.history.soc
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlin.math.roundToLong

/** What a series of the battery chart draws; the line provider maps each kind to its look. */
private enum class SeriesKind { MEASURED, MEASURED_CHARGING, DOTS, DOTS_CHARGING, CHARGE_PROJECTION, DISCHARGE_PROJECTION }

/** One series of the model: x in hours since the window start, y in percent. */
private class SeriesSpec(
    val kind: SeriesKind,
    val xs: List<Double>,
    val ys: List<Double>,
)

/** A projected point: the time it refers to and the percent there. */
private class TimePoint(
    val millis: Long,
    val percent: Float,
)

/** A wedge between three data points, drawn under the lines. */
private class Beam(
    val xs: List<Double>,
    val ys: List<Double>,
    val color: Color,
)

/**
 * Battery percent over time: the measured line (green while charging) with a
 * dot per measurement, and the dashed charge or parked-drain projection with
 * its beam. Pressing the chart shows a cursor with the value and the time at
 * the nearest point. Scroll and zoom are off and the window is fixed, like
 * the drive chart.
 */
@Composable
internal fun BatteryChart(
    samples: List<BatterySample>,
    charge: ChargeProjection?,
    discharge: DischargeProjection?,
    scale: FullRangeScale?,
    windowStart: Long,
    windowEnd: Long,
    showDate: Boolean,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val chargingColor = chargingChartColor
    val spanHours = (windowEnd - windowStart).coerceAtLeast(1L).toDouble() / MILLIS_PER_HOUR

    val shape =
        remember(samples, charge, discharge, scale, windowStart, windowEnd, lineColor) {
            batteryShape(samples, charge, discharge, scale, windowStart, windowEnd, spanHours, lineColor, chargingColor)
        }
    val lines = rememberBatteryLines(lineColor, chargingColor, parkedMuted = charge != null)
    val timeFormatter =
        remember(windowStart, showDate, is24Hour) {
            CartesianValueFormatter { _, value, _ -> formatTime(windowStart + (value * MILLIS_PER_HOUR).roundToLong(), showDate, is24Hour) }
        }
    val yFormatter = remember { CartesianValueFormatter { _, value, _ -> formatWholePercent(value.toFloat()) } }
    val xStep = spanHours / X_LABEL_STEPS
    val lastMeasuredMillis = samples.last().timestampMillis

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val chart =
            rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider =
                        remember(
                            shape.specs,
                            lines,
                        ) { LineCartesianLayer.LineProvider.series(shape.specs.map { lines.getValue(it.kind) }) },
                    rangeProvider =
                        remember(shape) {
                            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = spanHours, minY = shape.yMin, maxY = shape.yMax)
                        },
                ),
                startAxis =
                    VerticalAxis.rememberStart(
                        valueFormatter = yFormatter,
                        itemPlacer = remember(shape.yStep) { VerticalAxis.ItemPlacer.step({ shape.yStep }) },
                    ),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = timeFormatter),
                marker =
                    rememberDefaultCartesianMarker(
                        label = rememberTextComponent(style = MaterialTheme.typography.labelSmall),
                        valueFormatter =
                            remember(windowStart, showDate, is24Hour, lastMeasuredMillis) {
                                CursorFormatter(windowStart, showDate, is24Hour, lastMeasuredMillis)
                            },
                    ),
                decorations = remember(shape.beams) { listOf(Beams(shape.beams)) },
                getXStep = { _, _, _ -> xStep },
            )
        CartesianChartHost(
            chart = chart,
            model = remember(shape.specs) { batteryModel(shape.specs) },
            modifier = modifier,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

/** The data behind one chart: its series, wedges and axis range. */
private class BatteryShape(
    val specs: List<SeriesSpec>,
    val beams: List<Beam>,
    val yMin: Double,
    val yMax: Double,
    val yStep: Double,
)

private fun batteryShape(
    samples: List<BatterySample>,
    charge: ChargeProjection?,
    discharge: DischargeProjection?,
    scale: FullRangeScale?,
    windowStart: Long,
    windowEnd: Long,
    spanHours: Double,
    lineColor: Color,
    chargingColor: Color,
): BatteryShape {
    fun hours(millis: Long): Double = ((millis - windowStart).toDouble() / MILLIS_PER_HOUR).coerceIn(0.0, spanHours)

    val socs = samples.map { it.soc(scale) }
    val minPercent = minOf(socs.min(), discharge?.let { it.lowSoc ?: it.projectedSoc } ?: PERCENT_FULL)
    val maxPercent = maxOf(socs.max(), (charge?.targetPercent ?: 0).toFloat())
    var yMin = ((minPercent - Y_MARGIN).toInt().coerceAtLeast(0) / AXIS_ROUND) * AXIS_ROUND
    var yMax = (((maxPercent + Y_MARGIN).toInt().coerceAtMost(PERCENT_FULL.toInt()) + AXIS_ROUND - 1) / AXIS_ROUND) * AXIS_ROUND
    if (yMax <= yMin) {
        yMin = (yMin - AXIS_ROUND).coerceAtLeast(0)
        yMax = (yMin + AXIS_ROUND).coerceAtMost(PERCENT_FULL.toInt())
    }

    val xs = samples.map { hours(it.timestampMillis) }
    val ys = socs.map { it.toDouble() }
    val specs = mutableListOf<SeriesSpec>()

    // A segment takes the colour of the sample it ends at, so each run starts at the sample before it.
    var index = 1
    while (index < samples.size) {
        val charging = samples[index].isCharging
        var end = index
        while (end + 1 < samples.size && samples[end + 1].isCharging == charging) end++
        specs +=
            SeriesSpec(
                if (charging) SeriesKind.MEASURED_CHARGING else SeriesKind.MEASURED,
                xs.slice(index - 1..end),
                ys.slice(
                    index - 1..end,
                ),
            )
        index = end + 1
    }
    // One dot per measurement, no decimation: dense samples overlap into a thick band.
    listOf(false to SeriesKind.DOTS, true to SeriesKind.DOTS_CHARGING).forEach { (charging, kind) ->
        val picked = samples.indices.filter { samples[it].isCharging == charging }
        if (picked.isNotEmpty()) specs += SeriesSpec(kind, picked.map { xs[it] }, picked.map { ys[it] })
    }

    val beams = mutableListOf<Beam>()
    charge?.let { target ->
        val from = TimePoint(target.from.timestampMillis, target.from.percent.toFloat())
        val end = chargeEnd(target, windowEnd, target.completionMillis)
        specs += projectionSpec(SeriesKind.CHARGE_PROJECTION, from, end, ::hours)
        target.beam?.let { (earliest, latest) ->
            val early = chargeEnd(target, windowEnd, earliest)
            val late = chargeEnd(target, windowEnd, latest)
            beams +=
                Beam(
                    xs = listOf(from, early, late).map { hours(it.millis) },
                    ys = listOf(from, early, late).map { it.percent.toDouble() },
                    color = chargingColor.copy(alpha = CHARGE_BEAM_ALPHA),
                )
        }
    }
    discharge?.let { target ->
        val from = TimePoint(target.from.timestampMillis, target.fromSoc)
        val end = TimePoint(target.projectedAtMillis, target.projectedSoc)
        specs += projectionSpec(SeriesKind.DISCHARGE_PROJECTION, from, end, ::hours)
        val low = target.lowSoc
        val high = target.highSoc
        if (low != null && high != null) {
            beams +=
                Beam(
                    xs = listOf(from, end, end).map { hours(it.millis) },
                    ys = listOf(from.percent, low, high).map { it.toDouble() },
                    color = lineColor.copy(alpha = if (charge != null) DISCHARGE_BEAM_MUTED_ALPHA else DISCHARGE_BEAM_ALPHA),
                )
        }
    }
    return BatteryShape(
        specs = specs,
        beams = beams,
        yMin = yMin.toDouble(),
        yMax = yMax.toDouble(),
        yStep = maxOf(niceStep((yMax - yMin).toDouble() / Y_LABEL_STEPS), AXIS_ROUND.toDouble()),
    )
}

private fun projectionSpec(
    kind: SeriesKind,
    from: TimePoint,
    to: TimePoint,
    hours: (Long) -> Double,
) = SeriesSpec(kind, listOf(hours(from.millis), hours(to.millis)), listOf(from.percent.toDouble(), to.percent.toDouble()))

/**
 * Where the charge line, or an edge of its beam, ends for [completionMillis].
 * A completion past the chart's right edge is cut where its line crosses the
 * edge, so the line and the wedge keep their slope instead of flattening onto
 * the edge.
 */
private fun chargeEnd(
    projection: ChargeProjection,
    windowEnd: Long,
    completionMillis: Long,
): TimePoint {
    if (completionMillis <= windowEnd) return TimePoint(completionMillis, projection.targetPercent.toFloat())
    val fromMillis = projection.from.timestampMillis
    val fromPercent = projection.from.percent
    val share = (windowEnd - fromMillis).toFloat() / (completionMillis - fromMillis)
    // Not rounded: far from the limit the two sides differ by well under a point.
    return TimePoint(windowEnd, fromPercent + (projection.targetPercent - fromPercent) * share)
}

private fun batteryModel(specs: List<SeriesSpec>): CartesianChartModel =
    CartesianChartModel(
        LineCartesianLayerModel.build {
            specs.forEach { series(x = it.xs, y = it.ys) }
        },
    )

@Composable
private fun rememberBatteryLines(
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
        stroke = remember { LineCartesianLayer.LineStroke.Continuous(thickness = LINE_WIDTH, cap = StrokeCap.Round) },
    )

/** A line-less series: only its points show, one per measurement. */
@Composable
private fun dotsLine(color: Color): LineCartesianLayer.Line {
    val point = rememberPoint(color, size = DOT_SIZE)
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
                LineCartesianLayer.LineStroke.Dashed(thickness = PROJECTION_WIDTH, dashLength = DASH_LENGTH, gapLength = DASH_GAP)
            },
    )

/** The cursor's text: the percent at the pressed point and when it was (or is projected to be) reached. */
private class CursorFormatter(
    private val windowStart: Long,
    private val showDate: Boolean,
    private val is24Hour: Boolean,
    private val lastMeasuredMillis: Long,
) : DefaultCartesianMarker.ValueFormatter {
    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ): CharSequence {
        val target = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull() ?: return ""
        val point = target.points.firstOrNull() ?: return ""
        val millis = windowStart + (target.x * MILLIS_PER_HOUR).roundToLong()
        val prefix = if (millis > lastMeasuredMillis) "Projected " else ""
        return "$prefix${formatPercent(point.entry.y.toFloat())}% · ${formatTime(millis, showDate, is24Hour)}"
    }
}

/** The projection beams: translucent wedges under the lines. */
private class Beams(
    private val beams: List<Beam>,
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        with(context) {
            beams.forEach { beam ->
                val path =
                    Path().apply {
                        moveTo(canvasX(beam.xs[0]), canvasY(beam.ys[0]))
                        lineTo(canvasX(beam.xs[1]), canvasY(beam.ys[1]))
                        lineTo(canvasX(beam.xs[2]), canvasY(beam.ys[2]))
                        close()
                    }
                val fill = beam.color
                canvas.drawPath(path, Paint().apply { color = fill })
            }
        }
    }
}

private const val MILLIS_PER_HOUR = 3_600_000.0
private const val X_LABEL_STEPS = 2
private const val Y_LABEL_STEPS = 4
private const val PERCENT_FULL = 100f
private const val Y_MARGIN = 2
private const val AXIS_ROUND = 5

private val LINE_WIDTH = 1.5.dp
private val PROJECTION_WIDTH = 2.dp
private val DASH_LENGTH = 8.dp
private val DASH_GAP = 6.dp
private val DOT_SIZE = 4.dp

private const val CHARGE_LINE_ALPHA = 0.65f
private const val CHARGE_BEAM_ALPHA = 0.25f
private const val DISCHARGE_LINE_ALPHA = 0.65f
private const val DISCHARGE_LINE_MUTED_ALPHA = 0.35f
private const val DISCHARGE_BEAM_ALPHA = 0.2f
private const val DISCHARGE_BEAM_MUTED_ALPHA = 0.1f
