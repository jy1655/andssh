package com.opencode.sshterminal.ui.terminal

import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType
import com.opencode.sshterminal.session.SessionId
import com.opencode.sshterminal.session.SessionSnapshot
import com.opencode.sshterminal.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalConnectionInfoFormatterTest {
    @Test
    fun `returns null for blank host`() {
        val snapshot =
            SessionSnapshot(
                sessionId = SessionId("session-1"),
                state = SessionState.CONNECTED,
                host = "",
                port = 22,
                username = "dev",
            )

        val info = buildTerminalConnectionInfo(snapshot, profile = null)

        assertNull(info)
    }

    @Test
    fun `builds endpoint and counts`() {
        val snapshot =
            SessionSnapshot(
                sessionId = SessionId("session-2"),
                state = SessionState.CONNECTED,
                host = "example.com",
                port = 2222,
                username = "alice",
            )
        val profile =
            ConnectionProfile(
                id = "conn-1",
                name = "test",
                host = "example.com",
                port = 2222,
                username = "alice",
                proxyJump = "jump-a,jump-b",
                portForwards =
                    listOf(
                        PortForwardRule(
                            type = PortForwardType.DYNAMIC,
                            bindPort = 1080,
                        ),
                    ),
            )

        val info = buildTerminalConnectionInfo(snapshot, profile)

        assertEquals("alice@example.com:2222", info?.endpoint)
        assertEquals(2, info?.proxyJumpHopCount)
        assertEquals(1, info?.forwardCount)
        assertEquals(listOf("D 127.0.0.1:1080"), info?.forwardPreviewLines)
        assertEquals(0, info?.remainingForwardCount)
    }

    @Test
    fun `limits forward preview and tracks remaining count`() {
        val snapshot =
            SessionSnapshot(
                sessionId = SessionId("session-3"),
                state = SessionState.CONNECTED,
                host = "example.com",
                port = 22,
                username = "root",
            )
        val profile =
            ConnectionProfile(
                id = "conn-2",
                name = "test-2",
                host = "example.com",
                port = 22,
                username = "root",
                portForwards =
                    listOf(
                        PortForwardRule(PortForwardType.DYNAMIC, bindPort = 1080),
                        PortForwardRule(PortForwardType.DYNAMIC, bindPort = 1081),
                        PortForwardRule(PortForwardType.DYNAMIC, bindPort = 1082),
                        PortForwardRule(PortForwardType.DYNAMIC, bindPort = 1083),
                    ),
            )

        val info = buildTerminalConnectionInfo(snapshot, profile)

        assertEquals(4, info?.forwardCount)
        assertEquals(3, info?.forwardPreviewLines?.size)
        assertEquals(1, info?.remainingForwardCount)
    }

    @Test
    fun `formats metadata lines with forward previews`() {
        val text =
            TerminalConnectionInfo(
                endpoint = "u@h:22",
                proxyJumpHopCount = 1,
                forwardCount = 2,
                forwardPreviewLines = listOf("D 127.0.0.1:1080"),
                remainingForwardCount = 1,
            ).toDisplayText(
                proxyJumpFormatter = { count -> "PJ $count" },
                forwardFormatter = { count -> "FWD $count" },
                moreForwardFormatter = { count -> "+$count more" },
            )

        assertEquals("u@h:22\nPJ 1\nFWD 2\n  - D 127.0.0.1:1080\n+1 more", text)
    }
}
