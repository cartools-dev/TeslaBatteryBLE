// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

/**
 * A busy indicator that moves in steps: a ring of spokes whose bright spoke advances [SPOKES] times a second. A smooth
 * spinner draws a frame on every display refresh (120 a second on many phones), and each frame redraws and composites the
 * whole window; this one draws only when the step changes, so it costs about a tenth. The step is read only while
 * drawing, in a layer of its own, so a step redraws the spinner and recomposes nothing.
 *
 * In inspection mode (the Paparazzi snapshots) it stands still on its first step.
 */
@Composable
internal fun SteppedSpinner(
    size: Dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    var step by remember { mutableIntStateOf(0) }
    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(STEP_MS)
                step = (step + 1) % SPOKES
            }
        }
    }
    Canvas(Modifier.size(size).graphicsLayer()) {
        val stroke = this.size.minDimension * SPOKE_WIDTH
        val outer = this.size.minDimension / 2 - stroke / 2
        val inner = outer * SPOKE_INNER
        for (spoke in 0 until SPOKES) {
            // The lead spoke is full strength; the ones behind it fade out like a trail.
            val behind = (step - spoke + SPOKES) % SPOKES
            val alpha = 1f - behind * (1f - MIN_ALPHA) / (SPOKES - 1)
            rotate(degrees = spoke * FULL_TURN / SPOKES) {
                drawLine(
                    color = color.copy(alpha = color.alpha * alpha),
                    start = Offset(center.x, center.y - inner),
                    end = Offset(center.x, center.y - outer),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Spokes in the ring, which is also the steps per turn: one turn a second. */
private const val SPOKES = 12
private const val STEP_MS = 1000L / SPOKES
private const val FULL_TURN = 360f

/** Spoke thickness and inner end, as fractions of the spinner's size and outer radius. */
private const val SPOKE_WIDTH = 0.12f
private const val SPOKE_INNER = 0.45f

/** The faintest spoke, at the end of the trail. */
private const val MIN_ALPHA = 0.2f
