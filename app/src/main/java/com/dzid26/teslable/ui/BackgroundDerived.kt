// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [compute] run off the main thread whenever [keys] change, for figures derived from the sample logs (up to tens of
 * thousands of samples): on the main thread they stall the frames of every read and scroll. Until the first result
 * arrives this is [initial]; after that the previous result stays on screen while the next one is computed. When [keys]
 * change again before a computation ends, its result is dropped for the newer one.
 *
 * In inspection mode (previews and the Paparazzi snapshots) it computes inline, so a render shows the figures at once.
 */
@Composable
internal fun <T> rememberInBackground(
    initial: T,
    vararg keys: Any?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    compute: () -> T,
): T {
    if (LocalInspectionMode.current) return remember(*keys) { compute() }
    val latest by rememberUpdatedState(compute)
    val result by produceState(initial, *keys) { value = withContext(dispatcher) { latest() } }
    return result
}
