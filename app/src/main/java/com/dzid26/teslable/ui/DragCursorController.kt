// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Point
import kotlin.math.abs

/**
 * Where the cursor stays after the finger lifts. Vico hides its live cursor on
 * every lift, so the chart draws this one as a persistent marker.
 */
@Stable
internal class CursorPin {
    var targets by mutableStateOf<List<CartesianMarker.Target>?>(null)
}

/**
 * When the chart's live cursor shows. A finger held or dragged sideways on the
 * chart brings it up after a short delay (so a page scroll that starts on the
 * chart never flashes it) and moves it under the finger; on the lift it is
 * handed to [pin]. A tap that does not bring the cursor up clears the pin. A
 * drag that turns out mostly vertical is the page being scrolled and never
 * activates the cursor. Move events are not consumed, so the page's own scroll
 * still takes a vertical drag.
 */
internal class DragCursorController(
    private val pin: CursorPin,
    private val touchSlop: Float,
    private val nowNanos: () -> Long = System::nanoTime,
) : CartesianMarkerController {
    private var pressedAt: Point? = null
    private var pressedNanos = 0L
    private var scrolling = false
    private var active = false
    private var lastTargets: List<CartesianMarker.Target>? = null

    override val lock: CartesianMarkerController.Lock = CartesianMarkerController.Lock.Position

    // A held, still finger never sends moves; Vico's long press brings the cursor up for it.
    override val acceptsLongPress: Boolean = true

    // Only accepted interactions reach shouldShowMarker, and Vico keeps its own state of pressing here.
    override fun shouldAcceptInteraction(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean {
        if (targets.isNotEmpty()) lastTargets = targets
        return when (interaction) {
            is Interaction.Press -> press(interaction.point)
            is Interaction.Move -> move(interaction.point)
            is Interaction.LongPress -> activate()
            is Interaction.Release -> release()
            else -> false
        }
    }

    override fun shouldShowMarker(
        interaction: Interaction,
        targets: List<CartesianMarker.Target>,
    ): Boolean = active

    private fun press(point: Point): Boolean {
        pressedAt = point
        pressedNanos = nowNanos()
        scrolling = false
        active = false
        return false
    }

    private fun move(point: Point): Boolean {
        if (pressedAt == null) return false
        scrolling = scrolling || isVerticalDrag(point)
        active = !scrolling && (active || delayPassed())
        return active
    }

    private fun activate(): Boolean {
        active = pressedAt != null && !scrolling
        return active
    }

    // The live cursor goes with the finger; where it was becomes the standing one. A tap that
    // never brought it up dismisses the standing one.
    private fun release(): Boolean {
        // A drag that turned into a page scroll is not a tap: it leaves the standing cursor alone.
        if (active || !scrolling) pin.targets = if (active) lastTargets else null
        pressedAt = null
        scrolling = false
        active = false
        return false
    }

    private fun delayPassed(): Boolean = nowNanos() - pressedNanos >= SHOW_DELAY_NANOS

    private fun isVerticalDrag(point: Point): Boolean {
        val start = pressedAt ?: return false
        val dy = abs(point.y - start.y)
        return dy > touchSlop && dy > abs(point.x - start.x)
    }
}

@Composable
internal fun rememberCursorPin(): CursorPin = remember { CursorPin() }

@Composable
internal fun rememberCursorController(pin: CursorPin): CartesianMarkerController {
    val slop = LocalViewConfiguration.current.touchSlop
    return remember(pin, slop) { DragCursorController(pin, slop) }
}

private const val SHOW_DELAY_NANOS = 150_000_000L

/** Redraws the cursor where the last drag left it: Vico hides its own on every lift. */
internal class PinnedCursor(
    private val pin: CursorPin,
    private val marker: CartesianMarker,
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        pin.targets?.let { marker.drawOverLayers(context, it) }
    }
}
