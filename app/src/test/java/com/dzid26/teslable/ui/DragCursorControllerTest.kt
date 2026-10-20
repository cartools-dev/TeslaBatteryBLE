// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.common.Point
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragCursorControllerTest {
    private var now = 0L
    private val pin = CursorPin()
    private val controller = DragCursorController(pin, touchSlop = 8f, nowNanos = { now })
    private val targets = listOf<CartesianMarker.Target>(FakeTarget)

    private object FakeTarget : CartesianMarker.Target {
        override val x = 3.0
        override val canvasX = 30f
    }

    private fun accept(interaction: Interaction) = controller.shouldAcceptInteraction(interaction, targets)

    private fun drag(
        x: Float,
        y: Float = 100f,
    ) = accept(Interaction.Move(Point(x, y)))

    @Test
    fun aSidewaysDragShowsTheCursorOnlyAfterTheDelay() {
        accept(Interaction.Press(Point(10f, 100f)))
        assertFalse(drag(30f))
        now = 200_000_000L
        assertTrue(drag(60f))
        assertTrue(controller.shouldShowMarker(Interaction.Move(Point(60f, 100f)), targets))
    }

    @Test
    fun theCursorStandsAfterTheLiftAndATapClearsIt() {
        accept(Interaction.Press(Point(10f, 100f)))
        now = 200_000_000L
        drag(60f)
        accept(Interaction.Release(Point(60f, 100f)))
        assertNotNull(pin.targets)

        accept(Interaction.Press(Point(10f, 100f)))
        accept(Interaction.Release(Point(10f, 100f)))
        assertNull(pin.targets)
    }

    @Test
    fun aVerticalDragNeverShowsTheCursorAndKeepsTheStandingOne() {
        accept(Interaction.Press(Point(10f, 100f)))
        now = 200_000_000L
        drag(60f)
        accept(Interaction.Release(Point(60f, 100f)))

        accept(Interaction.Press(Point(10f, 100f)))
        now = 400_000_000L
        assertFalse(drag(10f, 140f))
        assertFalse(drag(30f, 140f))
        accept(Interaction.Release(Point(30f, 140f)))
        assertNotNull(pin.targets)
    }
}
