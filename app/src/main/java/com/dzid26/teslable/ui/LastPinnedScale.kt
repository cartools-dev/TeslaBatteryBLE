// SPDX-License-Identifier: AGPL-3.0-only

package com.dzid26.teslable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dzid26.teslable.core.history.FullRangeScale

private class LastPinned {
    var scale: FullRangeScale? = null
}

/**
 * The newest pinned full-range [scale] this screen has seen, or null before the first one. The fit
 * pins and unpins as readings arrive (its error hovers near the pin tolerance), so a figure that
 * needs a pinned scale would appear and vanish between readings; keeping the last pinned fit
 * keeps it steady, and a newer pinned fit replaces it.
 */
@Composable
internal fun rememberLastPinned(scale: FullRangeScale?): FullRangeScale? {
    val last = remember { LastPinned() }
    if (scale?.pinned == true) last.scale = scale
    return last.scale
}
