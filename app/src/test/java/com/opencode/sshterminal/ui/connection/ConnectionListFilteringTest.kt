package com.opencode.sshterminal.ui.connection

import com.opencode.sshterminal.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionListFilteringTest {
    private val alphaOps =
        ConnectionProfile(
            id = "1",
            name = "alpha",
            group = "ops",
            host = "10.0.0.1",
            username = "root",
            tags = listOf("prod", "db"),
            lastUsedEpochMillis = 10L,
        )
    private val betaOps =
        ConnectionProfile(
            id = "2",
            name = "beta",
            group = "ops",
            host = "10.0.0.2",
            username = "ubuntu",
            lastUsedEpochMillis = 20L,
        )
    private val gammaDev =
        ConnectionProfile(
            id = "3",
            name = "gamma",
            group = "dev",
            host = "10.0.1.1",
            username = "dev",
            lastUsedEpochMillis = 30L,
        )
    private val ungrouped =
        ConnectionProfile(
            id = "4",
            name = "misc",
            host = "example.com",
            username = "admin",
            lastUsedEpochMillis = 40L,
        )

    @Test
    fun `search matches group host username and tags`() {
        val profiles = listOf(alphaOps, betaOps, gammaDev, ungrouped)

        val byGroup =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "ops",
                selectedGroupFilter = null,
                sortOption = ConnectionSortOption.NAME,
            )
        val byHost =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "10.0.1",
                selectedGroupFilter = null,
                sortOption = ConnectionSortOption.NAME,
            )
        val byUser =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "admin",
                selectedGroupFilter = null,
                sortOption = ConnectionSortOption.NAME,
            )
        val byTag =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "prod",
                selectedGroupFilter = null,
                sortOption = ConnectionSortOption.NAME,
            )

        assertEquals(listOf("alpha", "beta"), byGroup.map { profile -> profile.name })
        assertEquals(listOf("gamma"), byHost.map { profile -> profile.name })
        assertEquals(listOf("misc"), byUser.map { profile -> profile.name })
        assertEquals(listOf("alpha"), byTag.map { profile -> profile.name })
    }

    @Test
    fun `filters ungrouped only`() {
        val profiles = listOf(alphaOps, gammaDev, ungrouped)

        val filtered =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "",
                selectedGroupFilter = UNGROUPED_FILTER_KEY,
                sortOption = ConnectionSortOption.NAME,
            )

        assertEquals(listOf("misc"), filtered.map { profile -> profile.name })
    }

    @Test
    fun `recent sort applies inside group`() {
        val profiles = listOf(alphaOps, betaOps, gammaDev)

        val filtered =
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = "",
                selectedGroupFilter = "ops",
                sortOption = ConnectionSortOption.RECENT,
            )

        assertEquals(listOf("beta", "alpha"), filtered.map { profile -> profile.name })
    }
}
