// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Point
import kotlin.math.abs

/**
 * When the chart's cursor shows: while a finger is down on it, following the
 * finger sideways. A drag that turns out mostly vertical is the page being
 * scrolled, so the cursor hides for the rest of that gesture instead of
 * flickering over the scrolling page. Move events are not consumed, so the
 * page's own scroll still takes a vertical drag.
 */
internal class DragCursorController(
    private val touchSlop: Float,
) : CartesianMarkerController {
    private var pressedAt: Point? = null
    private var scrolling = false

    override val lock: CartesianMarkerController.Lock = CartesianMarkerController.Lock.Position

    // Vico's own press controller keeps its state here, not in shouldShowMarker.
    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean =
        when (interaction) {
            is Interaction.Press -> {
                pressedAt = interaction.point
                scrolling = false
                true
            }
            is Interaction.Move -> {
                scrolling = scrolling || isVerticalDrag(interaction.point)
                pressedAt != null
            }
            is Interaction.Release -> {
                pressedAt = null
                scrolling = false
                true
            }
            else -> false
        }

    override fun shouldShowMarker(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean = interaction !is Interaction.Release && !scrolling

    private fun isVerticalDrag(point: Point): Boolean {
        val start = pressedAt ?: return false
        val dy = abs(point.y - start.y)
        return dy > touchSlop && dy > abs(point.x - start.x)
    }
}

@Composable
internal fun rememberCursorController(): CartesianMarkerController {
    val slop = LocalViewConfiguration.current.touchSlop
    return remember(slop) { DragCursorController(slop) }
}
