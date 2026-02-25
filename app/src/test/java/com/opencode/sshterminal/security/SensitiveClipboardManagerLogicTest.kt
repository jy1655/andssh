package com.opencode.sshterminal.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveClipboardManagerLogicTest {
    @Test
    fun `normalizeClipboardTimeoutSeconds clamps negative values to zero`() {
        assertEquals(0, normalizeClipboardTimeoutSeconds(-10))
        assertEquals(0, normalizeClipboardTimeoutSeconds(0))
        assertEquals(30, normalizeClipboardTimeoutSeconds(30))
    }

    @Test
    fun `shouldScheduleClipboardAutoClear is true only for positive timeout`() {
        assertFalse(shouldScheduleClipboardAutoClear(0))
        assertFalse(shouldScheduleClipboardAutoClear(-1))
        assertTrue(shouldScheduleClipboardAutoClear(1))
    }
}
