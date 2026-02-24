package com.opencode.sshterminal.ui.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionTagsTest {
    @Test
    fun `parse tags supports comma and newline separators`() {
        val parsed = parseConnectionTagsInput("prod, db\nk8s")
        assertEquals(listOf("prod", "db", "k8s"), parsed)
    }

    @Test
    fun `parse tags trims and removes empty values`() {
        val parsed = parseConnectionTagsInput("  prod , , db  ,  \n \n")
        assertEquals(listOf("prod", "db"), parsed)
    }

    @Test
    fun `parse tags keeps insertion order and deduplicates exact values`() {
        val parsed = parseConnectionTagsInput("prod,db,prod,staging")
        assertEquals(listOf("prod", "db", "staging"), parsed)
    }

    @Test
    fun `format tags joins with comma space`() {
        assertEquals("prod, db", formatConnectionTagsInput(listOf("prod", "db")))
    }
}
