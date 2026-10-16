// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Android 14 stock "blue" Material You scheme, which the app renders with
 * when dynamic color is on and the wallpaper is the default one (the old
 * emulator screenshots). Paparazzi has no wallpaper, so the scheme is spelled
 * out; the values are sampled from `website/images/02-scanning*.png`.
 */
internal fun demoColorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) demoDark else demoLight

private val demoLight =
    lightColorScheme(
        primary = Color(0xFF445E91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        inversePrimary = Color(0xFFADC6FF),
        secondary = Color(0xFF575E71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDBE2F9),
        onSecondaryContainer = Color(0xFF141B2C),
        tertiary = Color(0xFF715573),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFBD7FC),
        onTertiaryContainer = Color(0xFF29132D),
        background = Color(0xFFFAF9FD),
        onBackground = Color(0xFF1A1B20),
        surface = Color(0xFFFAF9FD),
        onSurface = Color(0xFF1A1B20),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44474F),
        surfaceTint = Color(0xFF445E91),
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF1F0F4),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        surfaceBright = Color(0xFFFAF9FD),
        surfaceDim = Color(0xFFDAD9DD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F3F7),
        surfaceContainer = Color(0xFFEEEDF1),
        surfaceContainerHigh = Color(0xFFE8E7EB),
        surfaceContainerHighest = Color(0xFFE3E2E6),
    )

private val demoDark =
    darkColorScheme(
        primary = Color(0xFFADC6FF),
        onPrimary = Color(0xFF102F60),
        primaryContainer = Color(0xFF2B4678),
        onPrimaryContainer = Color(0xFFD8E2FF),
        inversePrimary = Color(0xFF445E91),
        secondary = Color(0xFFBFC6DC),
        onSecondary = Color(0xFF293041),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFDBE2F9),
        tertiary = Color(0xFFDEBCDF),
        onTertiary = Color(0xFF402843),
        tertiaryContainer = Color(0xFF583E5B),
        onTertiaryContainer = Color(0xFFFBD7FC),
        background = Color(0xFF121316),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF121316),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceTint = Color(0xFFADC6FF),
        inverseSurface = Color(0xFFE3E2E6),
        inverseOnSurface = Color(0xFF2F3036),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474F),
        surfaceBright = Color(0xFF38393C),
        surfaceDim = Color(0xFF121316),
        surfaceContainerLowest = Color(0xFF0D0E11),
        surfaceContainerLow = Color(0xFF1A1B1F),
        surfaceContainer = Color(0xFF1E1F23),
        surfaceContainerHigh = Color(0xFF292A2D),
        surfaceContainerHighest = Color(0xFF333538),
    )

private const val STATUS_BAR_DP = 40
private const val NAV_BAR_DP = 24
private const val PILL_WIDTH_DP = 134
private const val PILL_HEIGHT_DP = 4

/**
 * The screen between a fake status bar (clock and signal glyphs) and gesture
 * nav pill, as the app sits on a phone; Paparazzi draws no system bars of its
 * own in the render mode the cards need.
 */
@Composable
internal fun DemoSystemBars(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = demoColorScheme(darkTheme)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(STATUS_BAR_DP.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "9:00", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        glyph(width = 14, height = 10)
                        glyph(width = 14, height = 10)
                        glyph(width = 8, height = 14)
                    }
                }
                Box(modifier = Modifier.weight(1f)) { content() }
                Box(
                    modifier = Modifier.fillMaxWidth().height(NAV_BAR_DP.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(PILL_WIDTH_DP.dp)
                                .height(PILL_HEIGHT_DP.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun glyph(
    width: Int,
    height: Int,
) {
    Box(
        modifier =
            Modifier
                .size(width = width.dp, height = height.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface),
    )
}
