// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

    /** Bumped to rebuild the chart, which drops a live Vico cursor that no interaction will hide. */
    var reset by mutableIntStateOf(0)

    /** Whether a cursor may be on screen: the standing one, or a live one Vico has not hidden. */
    var shown = false

    /** The screen gesture that last landed on this chart, so the screen-wide clear skips it. */
    var insideGesture = -1

    /**
     * Where a lift that did not drag the cursor would put it. The chart cannot tell a tap from a
     * page scroll (it moves with the finger), so the screen-wide tap check decides whether it lands.
     */
    var tapTargets: List<CartesianMarker.Target>? = null
}

/**
 * When the chart's live cursor shows. A finger dragged sideways on the chart
 * (past the touch slop, so a jittery tap never counts) or held still for a long
 * press brings it up and moves it under the finger; on the lift it is handed to
 * [pin]. Any other touch on the chart clears the pin at once. A drag that turns
 * out mostly vertical is the page being scrolled and never activates the
 * cursor. Move events are not consumed, so the page's own scroll still takes a
 * vertical drag.
 */
internal class DragCursorController(
    private val pin: CursorPin,
    private val touchSlop: Float,
) : CartesianMarkerController {
    private var pressedAt: Point? = null
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
        pin.tapTargets = null
        scrolling = false
        active = false
        return false
    }

    private fun move(point: Point): Boolean {
        if (pressedAt == null) return false
        scrolling = scrolling || isDrag(point, vertical = true)
        active = !scrolling && (active || isDrag(point, vertical = false))
        pin.shown = pin.shown || active
        return active
    }

    private fun activate(): Boolean {
        active = pressedAt != null && !scrolling
        pin.shown = pin.shown || active
        return active
    }

    // A drag leaves the cursor where it ended. Anything else may be a tap or a page scroll, which
    // the screen-wide check tells apart; it puts the cursor at [CursorPin.tapTargets] on a tap.
    // Accepting the lift makes Vico re-ask shouldShowMarker, which hides its live cursor now that
    // the standing one takes over.
    private fun release(): Boolean {
        if (active) {
            pin.targets = lastTargets
            pin.shown = true
        } else if (!scrolling) {
            pin.tapTargets = lastTargets
        }
        pressedAt = null
        scrolling = false
        active = false
        return true
    }

    /** True once the finger is past the slop and moved mostly along the asked axis. */
    private fun isDrag(
        point: Point,
        vertical: Boolean,
    ): Boolean {
        val start = pressedAt ?: return false
        val dx = abs(point.x - start.x)
        val dy = abs(point.y - start.y)
        val (along, across) = if (vertical) dy to dx else dx to dy
        return along > touchSlop && along > across
    }
}

@Composable
internal fun rememberCursorPin(): CursorPin {
    val pin = remember { CursorPin() }
    val taps = CursorDismiss.count
    LaunchedEffect(taps) {
        if (pin.insideGesture == CursorDismiss.gesture) {
            // A tap on this chart puts the cursor where the finger was.
            pin.tapTargets?.let {
                pin.targets = it
                pin.shown = true
            }
        } else if (pin.shown) {
            // A tap anywhere else clears both cursors; the rebuild drops a live one Vico still shows.
            pin.targets = null
            pin.shown = false
            pin.reset++
        }
        pin.tapTargets = null
    }
    return pin
}

@Composable
internal fun rememberCursorController(pin: CursorPin): CartesianMarkerController {
    val slop = LocalViewConfiguration.current.touchSlop
    return remember(pin, slop) { DragCursorController(pin, slop) }
}

/** Redraws the cursor where the last drag left it: Vico hides its own on every lift. */
internal class PinnedCursor(
    private val pin: CursorPin,
    private val marker: CartesianMarker,
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        pin.targets?.let { marker.drawOverLayers(context, it) }
    }
}

/**
 * Counts taps anywhere on the screen, so a standing cursor goes when the finger
 * taps outside its chart too. Drags (a page scroll) do not count.
 */
@Stable
internal object CursorDismiss {
    var count by mutableIntStateOf(0)

    /** Bumped at every touch down; a chart notes the one that landed on it. */
    var gesture = 0
}

/** Counts every tap on this subtree without consuming anything. */
internal fun Modifier.countingTouches(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            CursorDismiss.gesture++
            if (isTap(down.position)) CursorDismiss.count++
        }
    }

/** Follows the gesture to its lift: true when the finger never left the touch slop. */
private suspend fun AwaitPointerEventScope.isTap(start: Offset): Boolean {
    val slop = viewConfiguration.touchSlop
    var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
    while (change != null && change.pressed && (change.position - start).getDistance() <= slop) {
        change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull()
    }
    return change != null && !change.pressed && (change.position - start).getDistance() <= slop
}

/** Notes the screen gesture that landed on the chart, so the screen-wide clear leaves it to the controller. */
internal fun Modifier.markingTouches(pin: CursorPin): Modifier =
    pointerInput(pin) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pin.insideGesture = CursorDismiss.gesture
        }
    }
