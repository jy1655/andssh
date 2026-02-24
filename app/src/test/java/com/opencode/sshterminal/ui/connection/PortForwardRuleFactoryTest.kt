package com.opencode.sshterminal.ui.connection

import com.opencode.sshterminal.data.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PortForwardRuleFactoryTest {
    @Test
    fun `builds local forward rule`() {
        val rule =
            buildPortForwardRule(
                type = PortForwardType.LOCAL,
                bindHostInput = "0.0.0.0",
                bindPortInput = "8080",
                targetHostInput = "127.0.0.1",
                targetPortInput = "80",
            )

        assertNotNull(rule)
        assertEquals(PortForwardType.LOCAL, rule?.type)
        assertEquals("0.0.0.0", rule?.bindHost)
        assertEquals(8080, rule?.bindPort)
        assertEquals("127.0.0.1", rule?.targetHost)
        assertEquals(80, rule?.targetPort)
    }

    @Test
    fun `returns null for invalid bind port`() {
        val rule =
            buildPortForwardRule(
                type = PortForwardType.LOCAL,
                bindHostInput = "127.0.0.1",
                bindPortInput = "70000",
                targetHostInput = "localhost",
                targetPortInput = "22",
            )

        assertNull(rule)
    }

    @Test
    fun `returns null for missing target on remote forward`() {
        val rule =
            buildPortForwardRule(
                type = PortForwardType.REMOTE,
                bindHostInput = "",
                bindPortInput = "2222",
                targetHostInput = "",
                targetPortInput = "22",
            )

        assertNull(rule)
    }

    @Test
    fun `builds dynamic forward without target`() {
        val rule =
            buildPortForwardRule(
                type = PortForwardType.DYNAMIC,
                bindHostInput = "",
                bindPortInput = "1080",
                targetHostInput = "ignored",
                targetPortInput = "9999",
            )

        assertNotNull(rule)
        assertEquals(PortForwardType.DYNAMIC, rule?.type)
        assertEquals(null, rule?.targetHost)
        assertEquals(null, rule?.targetPort)
    }
}
