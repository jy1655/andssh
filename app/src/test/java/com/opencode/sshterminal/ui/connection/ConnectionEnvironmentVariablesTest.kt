package com.opencode.sshterminal.ui.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionEnvironmentVariablesTest {
    @Test
    fun `parse reads key value lines`() {
        val parsed =
            parseEnvironmentVariablesInput(
                """
                APP_ENV=prod
                TZ=UTC
                """.trimIndent(),
            )

        assertEquals(
            mapOf(
                "APP_ENV" to "prod",
                "TZ" to "UTC",
            ),
            parsed,
        )
    }

    @Test
    fun `parse ignores invalid lines and keeps last duplicate`() {
        val parsed =
            parseEnvironmentVariablesInput(
                """
                INVALID
                =missing
                APP_ENV=dev
                APP_ENV=prod
                TOKEN=a=b=c
                """.trimIndent(),
            )

        assertEquals(
            mapOf(
                "APP_ENV" to "prod",
                "TOKEN" to "a=b=c",
            ),
            parsed,
        )
    }

    @Test
    fun `format returns sorted key value lines`() {
        val formatted =
            formatEnvironmentVariablesInput(
                mapOf(
                    "TZ" to "UTC",
                    "APP_ENV" to "prod",
                ),
            )

        assertEquals(
            "APP_ENV=prod\nTZ=UTC",
            formatted,
        )
    }
}
