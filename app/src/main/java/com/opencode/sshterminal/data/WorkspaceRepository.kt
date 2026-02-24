package com.opencode.sshterminal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WorkspaceSnapshot(
    val connectionIds: List<String> = emptyList(),
    val activeTabIndex: Int = -1,
)

@Singleton
class WorkspaceRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        val snapshot: Flow<WorkspaceSnapshot> =
            dataStore.data.map { prefs ->
                prefs[WORKSPACE_SNAPSHOT_KEY]
                    ?.let(::decodeSnapshot)
                    ?: WorkspaceSnapshot()
            }

        suspend fun save(
            connectionIds: List<String>,
            activeTabIndex: Int,
        ) {
            val normalizedIds = connectionIds.filter { id -> id.isNotBlank() }
            val normalizedIndex =
                if (normalizedIds.isEmpty()) {
                    -1
                } else {
                    activeTabIndex.coerceIn(0, normalizedIds.lastIndex)
                }
            dataStore.edit { prefs ->
                if (normalizedIds.isEmpty()) {
                    prefs.remove(WORKSPACE_SNAPSHOT_KEY)
                } else {
                    val value =
                        json.encodeToString(
                            WorkspaceSnapshot(
                                connectionIds = normalizedIds,
                                activeTabIndex = normalizedIndex,
                            ),
                        )
                    prefs[WORKSPACE_SNAPSHOT_KEY] = value
                }
            }
        }

        suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(WORKSPACE_SNAPSHOT_KEY)
            }
        }

        private fun decodeSnapshot(raw: String): WorkspaceSnapshot {
            return runCatching {
                json.decodeFromString<WorkspaceSnapshot>(raw)
            }.getOrDefault(WorkspaceSnapshot())
        }

        companion object {
            private val WORKSPACE_SNAPSHOT_KEY = stringPreferencesKey("pref_workspace_snapshot")
        }
    }
