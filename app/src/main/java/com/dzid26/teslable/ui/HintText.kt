// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.history.DischargeProjection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A value with a dotted underline that opens an explanation on a tap (or a
 * long press): the unit stays next to the number, the words live in the hint.
 * Screen readers get both in the description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HintText(
    text: String,
    hint: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val dash = with(LocalDensity.current) { floatArrayOf(2.dp.toPx(), 2.dp.toPx()) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } },
        state = state,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier =
                Modifier
                    .semantics { contentDescription = "$text. $hint" }
                    .drawBehind {
                        val y = size.height - 1.dp.toPx()
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(dash),
                        )
                    }.clickable { scope.launch { state.show() } },
        )
    }
}

/**
 * The parked projection as one line of value-and-unit pieces: "Parked · projected 66%",
 * the worst and best case and the rate (the date is the chart's right edge). The range and the rate carry the
 * hints; the line wraps only when the screen is too narrow for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParkedProjectionLine(
    projection: DischargeProjection,
    color: Color,
) {
    val rates = parkedRates(projection, androidx.compose.ui.text.intl.Locale.current.platformLocale)
    val beam = projection.beam?.takeIf { (low, high) -> low.roundToInt() != high.roundToInt() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.history_parked_projected, projection.projectedPercent),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
        if (beam != null) {
            HintText(
                text = stringResource(R.string.history_parked_range, beam.first.roundToInt(), beam.second.roundToInt()),
                hint = stringResource(R.string.history_parked_range_hint),
                color = color,
            )
        }
        HintText(
            text = stringResource(R.string.history_parked_rate, rates.main),
            hint = stringResource(if (rates.asleep) R.string.history_parked_rate_hint_asleep else R.string.history_parked_rate_hint_mixed),
            color = color,
        )
    }
}
