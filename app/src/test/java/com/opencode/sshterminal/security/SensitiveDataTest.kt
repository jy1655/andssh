package com.opencode.sshterminal.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataTest {
    @Test
    fun `withZeroizedChars zeroizes allocated char array after use`() {
        var captured: CharArray? = null

        withZeroizedChars("secret") { chars ->
            captured = chars
            assertEquals("secret", String(requireNotNull(chars)))
        }

        val cleared = captured
        assertNotNull(cleared)
        assertTrue(requireNotNull(cleared).all { it == '\u0000' })
    }

    @Test
    fun `byte array zeroize fills array with zeros`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        bytes.zeroize()

        assertTrue(bytes.all { it == 0.toByte() })
    }
}
