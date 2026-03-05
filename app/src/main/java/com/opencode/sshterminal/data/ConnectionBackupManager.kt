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
    val privateKeyRelinkRequiredProfileCount: Int = 0,
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
            val sanitizedImport = sanitizeImportedPayload(payload)
            connectionRepository.replaceAll(
                profiles = sanitizedImport.profiles,
                identities = sanitizedImport.identities,
            )
            return ConnectionBackupImportSummary(
                profileCount = sanitizedImport.profiles.size,
                identityCount = sanitizedImport.identities.size,
                privateKeyRelinkRequiredProfileCount = sanitizedImport.privateKeyRelinkRequiredProfileCount,
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

        private fun sanitizeImportedPayload(payload: ConnectionBackupPayload): SanitizedImportPayload {
            val identitiesById =
                payload.identities.associateBy(ConnectionIdentity::id) { identity ->
                    sanitizeImportedIdentity(identity)
                }
            val identities = identitiesById.values.toList()
            val relinkIdentityIds =
                identitiesById
                    .filterValues { identity -> identity.requiresPrivateKeyRelink }
                    .keys
            val profiles =
                payload.profiles.map { profile ->
                    sanitizeImportedProfile(
                        profile = profile,
                        relinkIdentityIds = relinkIdentityIds,
                    )
                }
            return SanitizedImportPayload(
                profiles = profiles,
                identities = identities,
                privateKeyRelinkRequiredProfileCount = profiles.count { profile -> profile.requiresPrivateKeyRelink },
            )
        }

        private fun sanitizeImportedIdentity(identity: ConnectionIdentity): ConnectionIdentity {
            val privateKeyPath = identity.privateKeyPath.normalizedOptional()
            val hadPrivateKeyPath = privateKeyPath != null
            val relinkRequired = identity.requiresPrivateKeyRelink || hadPrivateKeyPath
            return identity.copy(
                privateKeyPath = if (relinkRequired) null else privateKeyPath,
                certificatePath =
                    if (relinkRequired) {
                        null
                    } else {
                        identity.certificatePath.normalizedOptional()?.takeIf { privateKeyPath != null }
                    },
                privateKeyPassphrase = if (relinkRequired) null else identity.privateKeyPassphrase.normalizedOptional(),
                requiresPrivateKeyRelink = relinkRequired,
            )
        }

        private fun sanitizeImportedProfile(
            profile: ConnectionProfile,
            relinkIdentityIds: Set<String>,
        ): ConnectionProfile {
            val privateKeyPath = profile.privateKeyPath.normalizedOptional()
            val profileHasPrivateKeyPath = privateKeyPath != null
            val identityRequiresRelink = profile.identityId?.let(relinkIdentityIds::contains) == true
            val proxyJumpRequiresRelink = profile.proxyJumpIdentityIds.values.any(relinkIdentityIds::contains)
            val relinkRequired =
                profile.requiresPrivateKeyRelink ||
                    profileHasPrivateKeyPath ||
                    identityRequiresRelink ||
                    proxyJumpRequiresRelink
            return profile.copy(
                privateKeyPath = if (relinkRequired) null else privateKeyPath,
                certificatePath =
                    if (relinkRequired) {
                        null
                    } else {
                        profile.certificatePath.normalizedOptional()?.takeIf { privateKeyPath != null }
                    },
                privateKeyPassphrase = if (relinkRequired) null else profile.privateKeyPassphrase.normalizedOptional(),
                requiresPrivateKeyRelink = relinkRequired,
            )
        }

        private fun String?.normalizedOptional(): String? {
            return this?.trim()?.takeIf { value -> value.isNotEmpty() }
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

private data class SanitizedImportPayload(
    val profiles: List<ConnectionProfile>,
    val identities: List<ConnectionIdentity>,
    val privateKeyRelinkRequiredProfileCount: Int,
)
