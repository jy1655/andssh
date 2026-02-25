package com.opencode.sshterminal.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockManagerTest {
    @Test
    fun `returns false when app lock is disabled`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = false,
                timeoutSeconds = 60,
                backgroundTimestamp = 1_000L,
                nowMillis = 70_000L,
            )

        assertFalse(shouldLock)
    }

    @Test
    fun `returns false when timeout is zero`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = true,
                timeoutSeconds = 0,
                backgroundTimestamp = 1_000L,
                nowMillis = 2_000L,
            )

        assertFalse(shouldLock)
    }

    @Test
    fun `returns false when background timestamp is missing`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = true,
                timeoutSeconds = 30,
                backgroundTimestamp = 0L,
                nowMillis = 60_000L,
            )

        assertFalse(shouldLock)
    }

    @Test
    fun `returns false when elapsed time is below timeout`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = true,
                timeoutSeconds = 60,
                backgroundTimestamp = 10_000L,
                nowMillis = 69_999L,
            )

        assertFalse(shouldLock)
    }

    @Test
    fun `returns true when elapsed time meets timeout`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = true,
                timeoutSeconds = 60,
                backgroundTimestamp = 10_000L,
                nowMillis = 70_000L,
            )

        assertTrue(shouldLock)
    }

    @Test
    fun `returns false when clock moved backwards`() {
        val shouldLock =
            shouldAutoLockAfterResume(
                appLockEnabled = true,
                timeoutSeconds = 60,
                backgroundTimestamp = 10_000L,
                nowMillis = 9_000L,
            )

        assertFalse(shouldLock)
    }
}
