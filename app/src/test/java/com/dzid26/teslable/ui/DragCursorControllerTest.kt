// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragCursorControllerTest {
    private val pin = CursorPin()
    private val controller = DragCursorController(pin, touchSlop = 8f)

    private class FakeTarget(
        override val x: Double,
    ) : CartesianMarker.Target {
        override val canvasX = x.toFloat()
    }

    private fun accept(
        interaction: Interaction,
        x: Double = 1.0,
    ) = controller.shouldAcceptInteraction(interaction, listOf(FakeTarget(x)))

    private fun drag(
        x: Float,
        y: Float = 100f,
    ) = accept(Interaction.Move(Point(x, y)), x.toDouble())

    private fun List<CartesianMarker.Target>?.x() = this?.single()?.x

    @Test
    fun aSidewaysDragPastTheSlopShowsTheLiveCursor() {
        accept(Interaction.Press(Point(10f, 100f)))
        assertFalse(drag(14f))
        assertTrue(drag(60f))
        assertTrue(controller.shouldShowMarker(Interaction.Move(Point(60f, 100f)), emptyList()))
    }

    @Test
    fun aDragLeavesTheCursorWhereItEnded() {
        accept(Interaction.Press(Point(10f, 100f)))
        drag(60f)
        assertTrue(accept(Interaction.Release(Point(60f, 100f)), x = 60.0))
        assertEquals(60.0, pin.targets.x()!!, 0.0)
        assertNull(pin.tapTargets)
    }

    @Test
    fun aJitteryTapOffersWhereTheFingerWasWithoutMovingTheStandingCursor() {
        accept(Interaction.Press(Point(10f, 100f)))
        drag(60f)
        accept(Interaction.Release(Point(60f, 100f)), x = 60.0)

        accept(Interaction.Press(Point(20f, 100f)), x = 20.0)
        assertFalse(drag(23f, 102f))
        assertTrue(accept(Interaction.Release(Point(23f, 102f)), x = 23.0))
        assertEquals(23.0, pin.tapTargets.x()!!, 0.0)
        assertEquals(60.0, pin.targets.x()!!, 0.0)
    }

    @Test
    fun aVerticalDragOnTheChartOffersNothing() {
        accept(Interaction.Press(Point(40f, 100f)), x = 40.0)
        assertFalse(drag(40f, 140f))
        assertFalse(drag(60f, 180f))
        accept(Interaction.Release(Point(60f, 180f)), x = 60.0)
        assertNull(pin.tapTargets)
        assertNull(pin.targets)
    }
}
