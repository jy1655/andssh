package com.opencode.sshterminal.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScrollTest {
    private val charHeight = 30f
    private val maxScroll = 100

    @Test
    fun `swipe up decreases scrollOffset toward live view`() {
        val dragAmount = -90f
        val result =
            computeScrollUpdate(
                dragAmount = dragAmount,
                currentScrollOffset = 10,
                pixelAccumulator = 0f,
                charHeightPx = charHeight,
                maxScroll = maxScroll,
            )
        assertTrue(
            "Swipe up (dragAmount<0) must decrease scrollOffset, got ${result.newScrollOffset}",
            result.newScrollOffset < 10,
        )
    }

    @Test
    fun `swipe down increases scrollOffset toward history`() {
        val dragAmount = 90f
        val result =
            computeScrollUpdate(
                dragAmount = dragAmount,
                currentScrollOffset = 0,
                pixelAccumulator = 0f,
                charHeightPx = charHeight,
                maxScroll = maxScroll,
            )
        assertTrue(
            "Swipe down (dragAmount>0) must increase scrollOffset, got ${result.newScrollOffset}",
            result.newScrollOffset > 0,
        )
    }

    @Test
    fun `scrollOffset clamped to zero at bottom`() {
        val result =
            computeScrollUpdate(
                dragAmount = -300f,
                currentScrollOffset = 2,
                pixelAccumulator = 0f,
                charHeightPx = charHeight,
                maxScroll = maxScroll,
            )
        assertEquals(0, result.newScrollOffset)
    }

    @Test
    fun `scrollOffset clamped to maxScroll at top`() {
        val result =
            computeScrollUpdate(
                dragAmount = 9000f,
                currentScrollOffset = 0,
                pixelAccumulator = 0f,
                charHeightPx = charHeight,
                maxScroll = maxScroll,
            )
        assertEquals(maxScroll, result.newScrollOffset)
    }

    @Test
    fun `sub-row drag accumulates without changing offset`() {
        val result =
            computeScrollUpdate(
                dragAmount = -10f,
                currentScrollOffset = 5,
                pixelAccumulator = 0f,
                charHeightPx = charHeight,
                maxScroll = maxScroll,
            )
        assertEquals(5, result.newScrollOffset)
        assertEquals(-10f, result.newPixelAccumulator, 0.01f)
    }

    @Test
    fun `accumulated pixels carry over across calls`() {
        val first = computeScrollUpdate(20f, 0, 0f, charHeight, maxScroll)
        assertEquals(0, first.newScrollOffset)

        val second = computeScrollUpdate(15f, first.newScrollOffset, first.newPixelAccumulator, charHeight, maxScroll)
        assertTrue(
            "Accumulated 35px at charHeight=30 should produce offset 1, got ${second.newScrollOffset}",
            second.newScrollOffset == 1,
        )
    }
}
