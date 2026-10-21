// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubStepTest {
    @Test
    fun aJitteryTapIsUndecided() {
        assertEquals(ScrubStep.UNDECIDED, scrubStep(Offset(3f, -4f), touchSlop = 8f))
    }

    @Test
    fun aSidewaysDragScrubs() {
        assertEquals(ScrubStep.SCRUB, scrubStep(Offset(-20f, 6f), touchSlop = 8f))
    }

    @Test
    fun aMostlyVerticalDragScrolls() {
        assertEquals(ScrubStep.SCROLL, scrubStep(Offset(10f, 12f), touchSlop = 8f))
    }

    @Test
    fun anExactDiagonalLeavesItToThePage() {
        assertEquals(ScrubStep.SCROLL, scrubStep(Offset(12f, 12f), touchSlop = 8f))
    }
}
