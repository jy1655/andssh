package com.opencode.sshterminal.data

import com.opencode.sshterminal.security.EncryptionManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectionBackupImportSummary(
    val profileCount: Int,
    val identityCount: Int,
)

@Singleton
class ConnectionBackupManager
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
        private val encryptionManager: EncryptionManager,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun exportEncryptedBackup(): String {
            val payload =
                ConnectionBackupPayload(
                    exportedAtEpochMillis = System.currentTimeMillis(),
                    profiles = connectionRepository.profiles.first(),
                    identities = connectionRepository.identities.first(),
                )
            val ciphertext = encryptionManager.encrypt(json.encodeToString(payload))
            return json.encodeToString(ConnectionBackupEnvelope(format = BACKUP_FORMAT, ciphertext = ciphertext))
        }

        suspend fun importEncryptedBackup(encryptedBackupJson: String): ConnectionBackupImportSummary {
            val envelope = parseEnvelope(encryptedBackupJson)
            require(envelope.format == BACKUP_FORMAT) { "Unsupported backup format" }
            val decryptedPayload = decryptPayload(envelope.ciphertext)
            val payload = parsePayload(decryptedPayload)
            connectionRepository.replaceAll(
                profiles = payload.profiles,
                identities = payload.identities,
            )
            return ConnectionBackupImportSummary(
                profileCount = payload.profiles.size,
                identityCount = payload.identities.size,
            )
        }

        private fun parseEnvelope(rawJson: String): ConnectionBackupEnvelope {
            return requireNotNull(
                runCatching {
                    json.decodeFromString<ConnectionBackupEnvelope>(rawJson)
                }.getOrNull(),
            ) {
                "Invalid backup file"
            }
        }

        private fun decryptPayload(ciphertext: String): String {
            return requireNotNull(
                runCatching {
                    encryptionManager.decrypt(ciphertext)
                }.getOrNull(),
            ) {
                "Backup decryption failed"
            }
        }

        private fun parsePayload(rawJson: String): ConnectionBackupPayload {
            return requireNotNull(
                runCatching {
                    json.decodeFromString<ConnectionBackupPayload>(rawJson)
                }.getOrNull(),
            ) {
                "Corrupted backup payload"
            }
        }

        companion object {
            internal const val BACKUP_FORMAT = "andssh_backup_v1"
        }
    }

@Serializable
private data class ConnectionBackupEnvelope(
    val format: String,
    val ciphertext: String,
)

@Serializable
private data class ConnectionBackupPayload(
    val exportedAtEpochMillis: Long,
    val profiles: List<ConnectionProfile>,
    val identities: List<ConnectionIdentity>,
)
