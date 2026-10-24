// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorPinTest {
    private val scope = SaverScope { true }

    @Test
    fun theSnappedPointSurvivesTheSaverRoundTrip() {
        val pin = CursorPin().apply { shownX = 1_700_000_000_000.0 }
        val saved = with(CursorPinSaver) { scope.save(pin) }
        val restored = CursorPinSaver.restore(saved!!)!!
        assertEquals(1_700_000_000_000.0, restored.restoredX!!, 0.0)
        assertNull(restored.canvasX)
    }

    @Test
    fun noCursorSavesNothing() {
        assertNull(with(CursorPinSaver) { scope.save(CursorPin()) })
    }

    @Test
    fun aSavedPointInsideTheWindowIsDrawn() {
        assertEquals(5.0, restoredCursorX(5.0, minX = 0.0, maxX = 10.0)!!, 0.0)
    }

    @Test
    fun aSavedPointOutsideTheWindowIsNot() {
        assertNull(restoredCursorX(12.0, minX = 0.0, maxX = 10.0))
        assertNull(restoredCursorX(null, minX = 0.0, maxX = 10.0))
    }
}
