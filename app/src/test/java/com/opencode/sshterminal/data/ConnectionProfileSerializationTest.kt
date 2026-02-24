package com.opencode.sshterminal.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionProfileSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes legacy profile json without protocol as ssh`() {
        val legacyJson =
            """
            {
              "id": "legacy-1",
              "name": "legacy",
              "host": "10.0.0.10",
              "port": 22,
              "username": "root"
            }
            """.trimIndent()

        val decoded = json.decodeFromString<ConnectionProfile>(legacyJson)

        assertEquals(ConnectionProtocol.SSH, decoded.protocol)
        assertEquals("legacy-1", decoded.id)
        assertEquals("legacy", decoded.name)
    }

    @Test
    fun `preserves mosh protocol across serialization`() {
        val original =
            ConnectionProfile(
                id = "mosh-1",
                name = "mosh-profile",
                protocol = ConnectionProtocol.MOSH,
                host = "example.com",
                username = "devops",
            )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ConnectionProfile>(encoded)

        assertEquals(ConnectionProtocol.MOSH, decoded.protocol)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(original.host, decoded.host)
        assertEquals(original.username, decoded.username)
    }
}
