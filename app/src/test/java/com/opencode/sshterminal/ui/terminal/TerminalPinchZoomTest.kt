package com.opencode.sshterminal.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalPinchZoomTest {
    @Test
    fun `pinch out increases font size`() {
        val result =
            computePinchFontSize(
                initialFontSizeSp = 16,
                initialDistancePx = 120f,
                currentDistancePx = 180f,
                minFontSizeSp = 8,
                maxFontSizeSp = 48,
            )
        assertEquals(24, result)
    }

    @Test
    fun `pinch in decreases font size`() {
        val result =
            computePinchFontSize(
                initialFontSizeSp = 16,
                initialDistancePx = 120f,
                currentDistancePx = 90f,
                minFontSizeSp = 8,
                maxFontSizeSp = 48,
            )
        assertEquals(12, result)
    }

    @Test
    fun `font size clamps to configured bounds`() {
        val maxClamped =
            computePinchFontSize(
                initialFontSizeSp = 40,
                initialDistancePx = 100f,
                currentDistancePx = 200f,
                minFontSizeSp = 8,
                maxFontSizeSp = 48,
            )
        val minClamped =
            computePinchFontSize(
                initialFontSizeSp = 10,
                initialDistancePx = 100f,
                currentDistancePx = 10f,
                minFontSizeSp = 8,
                maxFontSizeSp = 48,
            )

        assertEquals(48, maxClamped)
        assertEquals(8, minClamped)
    }

    @Test
    fun `invalid distance keeps initial font size`() {
        val result =
            computePinchFontSize(
                initialFontSizeSp = 18,
                initialDistancePx = 0f,
                currentDistancePx = 200f,
                minFontSizeSp = 8,
                maxFontSizeSp = 48,
            )
        assertEquals(18, result)
    }
}
