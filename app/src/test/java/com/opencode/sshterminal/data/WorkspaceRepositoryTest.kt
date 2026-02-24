package com.opencode.sshterminal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun createDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_workspace.preferences_pb") },
        )

    @Test
    fun `default workspace snapshot is empty`() =
        runTest(testDispatcher) {
            val repo = WorkspaceRepository(createDataStore())
            assertEquals(WorkspaceSnapshot(), repo.snapshot.first())
        }

    @Test
    fun `save persists tab order and active index`() =
        runTest(testDispatcher) {
            val repo = WorkspaceRepository(createDataStore())
            repo.save(
                connectionIds = listOf("conn-a", "conn-b", "conn-c"),
                activeTabIndex = 1,
            )

            assertEquals(
                WorkspaceSnapshot(
                    connectionIds = listOf("conn-a", "conn-b", "conn-c"),
                    activeTabIndex = 1,
                ),
                repo.snapshot.first(),
            )
        }

    @Test
    fun `save clamps out of bounds active index`() =
        runTest(testDispatcher) {
            val repo = WorkspaceRepository(createDataStore())
            repo.save(
                connectionIds = listOf("conn-a", "conn-b"),
                activeTabIndex = 99,
            )

            assertEquals(
                WorkspaceSnapshot(
                    connectionIds = listOf("conn-a", "conn-b"),
                    activeTabIndex = 1,
                ),
                repo.snapshot.first(),
            )
        }

    @Test
    fun `save with empty tabs clears snapshot`() =
        runTest(testDispatcher) {
            val repo = WorkspaceRepository(createDataStore())
            repo.save(connectionIds = listOf("conn-a"), activeTabIndex = 0)
            repo.save(connectionIds = emptyList(), activeTabIndex = -1)

            assertEquals(WorkspaceSnapshot(), repo.snapshot.first())
        }
}
