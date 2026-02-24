package com.opencode.sshterminal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigImportParserTest {
    @Test
    fun `parses concrete host with default host name`() {
        val content =
            """
            Host my-server
              User ubuntu
              Port 2222
              IdentityFile ~/.ssh/id_ed25519
            """.trimIndent()

        val result = parseSshConfig(content)

        assertEquals(1, result.hosts.size)
        val host = result.hosts.first()
        assertEquals("my-server", host.alias)
        assertEquals("my-server", host.hostName)
        assertEquals("ubuntu", host.user)
        assertEquals(2222, host.port)
        assertEquals("~/.ssh/id_ed25519", host.identityFile)
        assertEquals(0, result.skippedHostEntries)
    }

    @Test
    fun `skips wildcard host and missing user entries`() {
        val content =
            """
            Host *
              User fallback

            Host app-1
              HostName 10.0.0.10
            """.trimIndent()

        val result = parseSshConfig(content)

        assertTrue(result.hosts.isEmpty())
        assertEquals(2, result.skippedHostEntries)
    }

    @Test
    fun `applies global defaults to hosts`() {
        val content =
            """
            User root
            Port 10022

            Host node-a
              HostName 192.168.0.10
            """.trimIndent()

        val result = parseSshConfig(content)

        assertEquals(1, result.hosts.size)
        val host = result.hosts.first()
        assertEquals("root", host.user)
        assertEquals(10022, host.port)
    }

    @Test
    fun `parses proxy jump and forwarding rules`() {
        val content =
            """
            Host lab
              HostName 10.10.10.20
              User dev
              ProxyJump bastion
              LocalForward 127.0.0.1:8080 10.10.10.20:80
              RemoteForward 0.0.0.0:9000 127.0.0.1:9000
              DynamicForward 1080
            """.trimIndent()

        val result = parseSshConfig(content)

        assertEquals(1, result.hosts.size)
        val host = result.hosts.first()
        assertEquals("bastion", host.proxyJump)
        assertEquals(3, host.portForwards.size)

        val local = host.portForwards.first { it.type == PortForwardType.LOCAL }
        assertEquals("127.0.0.1", local.bindHost)
        assertEquals(8080, local.bindPort)
        assertEquals("10.10.10.20", local.targetHost)
        assertEquals(80, local.targetPort)

        val remote = host.portForwards.first { it.type == PortForwardType.REMOTE }
        assertEquals("0.0.0.0", remote.bindHost)
        assertEquals(9000, remote.bindPort)
        assertEquals("127.0.0.1", remote.targetHost)
        assertEquals(9000, remote.targetPort)

        val dynamic = host.portForwards.first { it.type == PortForwardType.DYNAMIC }
        assertNull(dynamic.targetHost)
        assertNull(dynamic.targetPort)
        assertEquals(1080, dynamic.bindPort)
    }

    @Test
    fun `global ForwardAgent applies to hosts`() {
        val content =
            """
            ForwardAgent yes

            Host dev
              HostName dev.example.com
              User alice
            """.trimIndent()

        val result = parseSshConfig(content)
        val host = result.hosts.single()

        assertTrue(host.forwardAgent)
    }

    @Test
    fun `host ForwardAgent overrides global default`() {
        val content =
            """
            ForwardAgent yes

            Host prod
              HostName prod.example.com
              User root
              ForwardAgent no
            """.trimIndent()

        val result = parseSshConfig(content)
        val host = result.hosts.single()

        assertFalse(host.forwardAgent)
    }

    @Test
    fun `invalid ForwardAgent value is ignored`() {
        val content =
            """
            Host misc
              HostName misc.example.com
              User me
              ForwardAgent maybe
            """.trimIndent()

        val result = parseSshConfig(content)
        val host = result.hosts.single()

        assertFalse(host.forwardAgent)
    }
}
