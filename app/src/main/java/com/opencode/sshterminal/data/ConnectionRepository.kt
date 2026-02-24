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
                    .filter { (key, _) -> key.name.startsWith(PROFILE_KEY_PREFIX) }
                    .values
                    .mapNotNull { value -> (value as? String)?.let(::decodeProfile) }
                    .sortedByDescending { it.lastUsedEpochMillis }
            }

        val identities: Flow<List<ConnectionIdentity>> =
            dataStore.data.map { prefs ->
                prefs.asMap()
                    .filter { (key, _) -> key.name.startsWith(IDENTITY_KEY_PREFIX) }
                    .values
                    .mapNotNull { value -> (value as? String)?.let(::decodeIdentity) }
                    .sortedByDescending { it.lastUsedEpochMillis }
            }

        suspend fun save(profile: ConnectionProfile) {
            dataStore.edit { prefs ->
                prefs[keyForProfile(profile.id)] = encryptionManager.encrypt(json.encodeToString(profile))
            }
        }

        suspend fun delete(id: String) {
            dataStore.edit { prefs ->
                val profileKey = keyForProfile(id)
                val deletedProfile = prefs[profileKey]?.let(::decodeProfile)
                prefs.remove(profileKey)
                val identityId = deletedProfile?.identityId ?: return@edit
                if (!isIdentityReferencedInPreferences(prefs, identityId)) {
                    prefs.remove(keyForIdentity(identityId))
                }
            }
        }

        suspend fun get(id: String): ConnectionProfile? {
            val prefs = dataStore.data.first()
            val raw = prefs[keyForProfile(id)] ?: return null
            return decodeProfile(raw)
        }

        suspend fun touchLastUsed(id: String) {
            dataStore.edit { prefs ->
                val raw = prefs[keyForProfile(id)] ?: return@edit
                val profile = decodeProfile(raw) ?: return@edit
                val updated = profile.copy(lastUsedEpochMillis = System.currentTimeMillis())
                prefs[keyForProfile(id)] = encryptionManager.encrypt(json.encodeToString(updated))
            }
        }

        suspend fun getIdentity(id: String): ConnectionIdentity? {
            val prefs = dataStore.data.first()
            val raw = prefs[keyForIdentity(id)] ?: return null
            return decodeIdentity(raw)
        }

        suspend fun upsertIdentity(
            existingIdentityId: String?,
            displayName: String,
            username: String,
            password: String?,
            privateKeyPath: String?,
            privateKeyPassphrase: String?,
        ): ConnectionIdentity {
            val normalizedName = displayName.ifBlank { "$username credentials" }
            val normalizedPassword = password.normalizedOptional()
            val normalizedPrivateKeyPath = privateKeyPath.normalizedOptional()
            val normalizedPrivateKeyPassphrase = privateKeyPassphrase.normalizedOptional()
            val now = System.currentTimeMillis()

            existingIdentityId?.let { identityId ->
                val existingById = getIdentity(identityId)
                if (existingById != null) {
                    val updated =
                        existingById.copy(
                            name = normalizedName,
                            username = username,
                            password = normalizedPassword,
                            privateKeyPath = normalizedPrivateKeyPath,
                            privateKeyPassphrase = normalizedPrivateKeyPassphrase,
                            lastUsedEpochMillis = now,
                        )
                    saveIdentity(updated)
                    return updated
                }
            }

            val existingByAuth =
                identities.first().firstOrNull { identity ->
                    identity.username == username &&
                        identity.password == normalizedPassword &&
                        identity.privateKeyPath == normalizedPrivateKeyPath &&
                        identity.privateKeyPassphrase == normalizedPrivateKeyPassphrase
                }
            if (existingByAuth != null) {
                val touched = existingByAuth.copy(lastUsedEpochMillis = now)
                saveIdentity(touched)
                return touched
            }

            val created =
                ConnectionIdentity(
                    name = normalizedName,
                    username = username,
                    password = normalizedPassword,
                    privateKeyPath = normalizedPrivateKeyPath,
                    privateKeyPassphrase = normalizedPrivateKeyPassphrase,
                    lastUsedEpochMillis = now,
                )
            saveIdentity(created)
            return created
        }

        suspend fun replaceAll(
            profiles: List<ConnectionProfile>,
            identities: List<ConnectionIdentity>,
        ) {
            dataStore.edit { prefs ->
                val keysToRemove =
                    prefs.asMap().keys.filter { key ->
                        key.name.startsWith(PROFILE_KEY_PREFIX) || key.name.startsWith(IDENTITY_KEY_PREFIX)
                    }
                keysToRemove.forEach { key -> prefs.remove(key) }

                identities.forEach { identity ->
                    prefs[keyForIdentity(identity.id)] = encryptionManager.encrypt(json.encodeToString(identity))
                }
                profiles.forEach { profile ->
                    prefs[keyForProfile(profile.id)] = encryptionManager.encrypt(json.encodeToString(profile))
                }
            }
        }

        private suspend fun saveIdentity(identity: ConnectionIdentity) {
            dataStore.edit { prefs ->
                prefs[keyForIdentity(identity.id)] = encryptionManager.encrypt(json.encodeToString(identity))
            }
        }

        private fun decodeProfile(raw: String): ConnectionProfile? {
            return runCatching {
                val decrypted = encryptionManager.decrypt(raw)
                json.decodeFromString<ConnectionProfile>(decrypted)
            }.getOrNull()
        }

        private fun decodeIdentity(raw: String): ConnectionIdentity? {
            return runCatching {
                val decrypted = encryptionManager.decrypt(raw)
                json.decodeFromString<ConnectionIdentity>(decrypted)
            }.getOrNull()
        }

        private fun keyForProfile(id: String) = stringPreferencesKey("$PROFILE_KEY_PREFIX$id")

        private fun keyForIdentity(id: String) = stringPreferencesKey("$IDENTITY_KEY_PREFIX$id")

        private fun isIdentityReferencedInPreferences(
            prefs: Preferences,
            identityId: String,
        ): Boolean {
            return prefs.asMap()
                .filterKeys { it.name.startsWith(PROFILE_KEY_PREFIX) }
                .values
                .mapNotNull { value -> (value as? String)?.let(::decodeProfile) }
                .any { profile -> profile.identityId == identityId }
        }

        private fun String?.normalizedOptional(): String? {
            return this?.trim()?.takeIf { it.isNotEmpty() }
        }

        companion object {
            private const val PROFILE_KEY_PREFIX = "conn_"
            private const val IDENTITY_KEY_PREFIX = "identity_"
        }
    }
