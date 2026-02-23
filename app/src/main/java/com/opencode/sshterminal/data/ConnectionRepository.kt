package com.opencode.sshterminal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.opencode.sshterminal.security.EncryptionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val encryptionManager: EncryptionManager,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        val profiles: Flow<List<ConnectionProfile>> =
            dataStore.data.map { prefs ->
                prefs.asMap()
                    .filter { (key, _) -> key.name.startsWith(KEY_PREFIX) }
                    .values
                    .mapNotNull { value -> (value as? String)?.let(::decodeProfile) }
                    .sortedByDescending { it.lastUsedEpochMillis }
            }

        suspend fun save(profile: ConnectionProfile) {
            dataStore.edit { prefs ->
                prefs[keyFor(profile.id)] = encryptionManager.encrypt(json.encodeToString(profile))
            }
        }

        suspend fun delete(id: String) {
            dataStore.edit { prefs ->
                prefs.remove(keyFor(id))
            }
        }

        suspend fun get(id: String): ConnectionProfile? {
            val prefs = dataStore.data.first()
            val raw = prefs[keyFor(id)] ?: return null
            return decodeProfile(raw)
        }

        suspend fun touchLastUsed(id: String) {
            dataStore.edit { prefs ->
                val raw = prefs[keyFor(id)] ?: return@edit
                val profile = decodeProfile(raw) ?: return@edit
                val updated = profile.copy(lastUsedEpochMillis = System.currentTimeMillis())
                prefs[keyFor(id)] = encryptionManager.encrypt(json.encodeToString(updated))
            }
        }

        private fun decodeProfile(raw: String): ConnectionProfile? {
            return runCatching {
                val decrypted = encryptionManager.decrypt(raw)
                json.decodeFromString<ConnectionProfile>(decrypted)
            }.getOrNull()
        }

        private fun keyFor(id: String) = stringPreferencesKey("$KEY_PREFIX$id")

        companion object {
            private const val KEY_PREFIX = "conn_"
        }
    }
