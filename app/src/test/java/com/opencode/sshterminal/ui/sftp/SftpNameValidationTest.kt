package com.opencode.sshterminal.ui.sftp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpNameValidationTest {
    @Test
    fun `accepts plain filename`() {
        assertTrue(isValidRemoteName("hello.txt"))
    }

    @Test
    fun `rejects path separators`() {
        assertFalse(isValidRemoteName("dir/file.txt"))
        assertFalse(isValidRemoteName("dir\\file.txt"))
    }

    @Test
    fun `rejects dot names`() {
        assertFalse(isValidRemoteName("."))
        assertFalse(isValidRemoteName(".."))
    }

    @Test
    fun `rejects blank names`() {
        assertFalse(isValidRemoteName(""))
        assertFalse(isValidRemoteName("   "))
    }
}
