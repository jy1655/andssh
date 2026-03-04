package com.opencode.sshterminal.data

import com.opencode.sshterminal.security.PasswordBasedEncryptionManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

class BackupV1IncompatibleException : IllegalStateException("Backup format v1 is not supported on this device")

class BackupPasswordRequiredException : IllegalArgumentException("Backup password is required")

class BackupDecryptionException : IllegalArgumentException("Incorrect password or corrupted backup")

data class ConnectionBackupImportSummary(
    val profileCount: Int,
    val identityCount: Int,
)

@Singleton
class ConnectionBackupManager
    @Inject
    constructor(
        private val connectionRepository: ConnectionRepository,
        private val passwordBasedEncryptionManager: PasswordBasedEncryptionManager,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun exportPasswordEncryptedBackup(password: CharArray): String {
            val payload =
                ConnectionBackupPayload(
                    exportedAtEpochMillis = System.currentTimeMillis(),
                    profiles = connectionRepository.profiles.first(),
                    identities = connectionRepository.identities.first(),
                )
            val ciphertext =
                passwordBasedEncryptionManager.encrypt(
                    plaintext = json.encodeToString(payload),
                    password = password,
                )
            return json.encodeToString(
                ConnectionBackupEnvelopeV2(
                    format = BACKUP_FORMAT_V2,
                    ciphertext = ciphertext,
                    kdf = BACKUP_KDF,
                    iterations = PasswordBasedEncryptionManager.PBKDF2_ITERATIONS,
                ),
            )
        }

        suspend fun importBackup(
            backupJson: String,
            password: CharArray?,
        ): ConnectionBackupImportSummary {
            return when (parseEnvelopeFormat(backupJson)) {
                BACKUP_FORMAT_V1 -> throw BackupV1IncompatibleException()
                BACKUP_FORMAT_V2 -> importV2(backupJson, password)
                else -> error("Unsupported backup format")
            }
        }

        private suspend fun importV2(
            rawJson: String,
            password: CharArray?,
        ): ConnectionBackupImportSummary {
            val envelope = parseEnvelopeV2(rawJson)
            require(envelope.kdf == BACKUP_KDF) { "Unsupported backup kdf" }
            require(envelope.iterations == PasswordBasedEncryptionManager.PBKDF2_ITERATIONS) {
                "Unsupported backup kdf iterations"
            }
            val backupPassword = password ?: throw BackupPasswordRequiredException()
            val payload = decryptAndParsePayload(envelope.ciphertext, backupPassword)
            connectionRepository.replaceAll(
                profiles = payload.profiles,
                identities = payload.identities,
            )
            return ConnectionBackupImportSummary(
                profileCount = payload.profiles.size,
                identityCount = payload.identities.size,
            )
        }

        private fun parseEnvelopeFormat(rawJson: String): String {
            return requireNotNull(
                runCatching {
                    json.decodeFromString<ConnectionBackupEnvelopeHeader>(rawJson)
                }.getOrNull(),
            ) {
                "Invalid backup file"
            }.format
        }

        private fun parseEnvelopeV2(rawJson: String): ConnectionBackupEnvelopeV2 {
            return requireNotNull(
                runCatching {
                    json.decodeFromString<ConnectionBackupEnvelopeV2>(rawJson)
                }.getOrNull(),
            ) {
                "Invalid backup file"
            }
        }

        private fun decryptAndParsePayload(
            ciphertext: String,
            password: CharArray,
        ): ConnectionBackupPayload {
            return runCatching {
                val payloadJson = passwordBasedEncryptionManager.decrypt(ciphertext, password)
                json.decodeFromString<ConnectionBackupPayload>(payloadJson)
            }.getOrElse {
                throw BackupDecryptionException()
            }
        }

        companion object {
            internal const val BACKUP_FORMAT_V1 = "andssh_backup_v1"
            internal const val BACKUP_FORMAT_V2 = "andssh_backup_v2"

            private const val BACKUP_KDF = "PBKDF2-HMAC-SHA256"
        }
    }

@Serializable
private data class ConnectionBackupEnvelopeHeader(
    val format: String,
)

@Serializable
data class ConnectionBackupEnvelopeV2(
    val format: String,
    val ciphertext: String,
    val kdf: String,
    val iterations: Int,
)

@Serializable
private data class ConnectionBackupPayload(
    val exportedAtEpochMillis: Long,
    val profiles: List<ConnectionProfile>,
    val identities: List<ConnectionIdentity>,
)
