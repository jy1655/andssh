package com.opencode.sshterminal.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.BuildConfig
import com.opencode.sshterminal.data.ConnectionIdentity
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.ConnectionProtocol
import com.opencode.sshterminal.data.ConnectionRepository
import com.opencode.sshterminal.data.parseSshConfig
import com.opencode.sshterminal.security.Fido2SecurityKeyPocManager
import com.opencode.sshterminal.security.U2fSecurityKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SshConfigImportSummary(
    val importedCount: Int,
    val skippedCount: Int,
)

data class QuickConnectInput(
    val host: String,
    val port: Int,
    val username: String,
    val password: String?,
    val protocol: ConnectionProtocol,
)

data class SecurityKeyEnrollmentResult(
    val application: String,
    val keyHandleBase64: String,
    val publicKeyBase64: String,
    val authorizedKey: String,
)

@HiltViewModel
class ConnectionListViewModel
    @Inject
    constructor(
        private val repository: ConnectionRepository,
        private val u2fSecurityKeyManager: U2fSecurityKeyManager,
        private val fido2SecurityKeyPocManager: Fido2SecurityKeyPocManager,
    ) : ViewModel() {
        val profiles: StateFlow<List<ConnectionProfile>> =
            repository.profiles
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    emptyList(),
                )

        val identities: StateFlow<List<ConnectionIdentity>> =
            repository.identities
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    emptyList(),
                )

        fun save(profile: ConnectionProfile) {
            viewModelScope.launch {
                val identity =
                    repository.upsertIdentity(
                        existingIdentityId = profile.identityId,
                        displayName = profile.name,
                        username = profile.username,
                        password = profile.password,
                        privateKeyPath = profile.privateKeyPath,
                        certificatePath = profile.certificatePath,
                        privateKeyPassphrase = profile.privateKeyPassphrase,
                    )
                repository.save(
                    profile.copy(
                        identityId = identity.id,
                        username = identity.username,
                        password = identity.password,
                        privateKeyPath = identity.privateKeyPath,
                        certificatePath = identity.certificatePath,
                        privateKeyPassphrase = identity.privateKeyPassphrase,
                    ),
                )
            }
        }

        fun quickConnect(
            input: QuickConnectInput,
            onComplete: (String) -> Unit,
        ) {
            viewModelScope.launch {
                val normalizedPort = input.port.takeIf { it in 1..65535 } ?: 22
                val displayName = "Quick: ${input.username}@${input.host}"
                val identity =
                    repository.upsertIdentity(
                        existingIdentityId = null,
                        displayName = displayName,
                        username = input.username,
                        password = input.password,
                        privateKeyPath = null,
                        certificatePath = null,
                        privateKeyPassphrase = null,
                    )
                val profile =
                    ConnectionProfile(
                        id = UUID.randomUUID().toString(),
                        name = displayName,
                        protocol = input.protocol,
                        host = input.host,
                        port = normalizedPort,
                        username = identity.username,
                        password = identity.password,
                        privateKeyPath = identity.privateKeyPath,
                        certificatePath = identity.certificatePath,
                        privateKeyPassphrase = identity.privateKeyPassphrase,
                        identityId = identity.id,
                        lastUsedEpochMillis = System.currentTimeMillis(),
                    )
                repository.save(profile)
                onComplete(profile.id)
            }
        }

        fun delete(id: String) {
            viewModelScope.launch { repository.delete(id) }
        }

        fun enrollHardwareSecurityKey(
            application: String,
            displayName: String,
            onComplete: (SecurityKeyEnrollmentResult?, String?) -> Unit,
        ) {
            viewModelScope.launch {
                if (!BuildConfig.ENABLE_SECURITY_KEY_ENROLL) {
                    onComplete(null, SECURITY_KEY_ENROLL_DISABLED_MESSAGE)
                    return@launch
                }
                if (BuildConfig.ENABLE_FIDO2_POC) {
                    fido2SecurityKeyPocManager.runEnrollmentAndAssertionPocOrNull(
                        application = application,
                        userDisplayName = displayName,
                    )
                }
                runCatching {
                    u2fSecurityKeyManager.enrollSecurityKey(
                        application = application,
                        comment = displayName,
                    )
                }.onSuccess { enrolled ->
                    onComplete(
                        SecurityKeyEnrollmentResult(
                            application = enrolled.application,
                            keyHandleBase64 = enrolled.keyHandleBase64,
                            publicKeyBase64 = enrolled.publicKeyBase64,
                            authorizedKey = enrolled.authorizedKey,
                        ),
                        null,
                    )
                }.onFailure { error ->
                    onComplete(null, error.message)
                }
            }
        }

        fun importFromSshConfig(
            content: String,
            onComplete: (SshConfigImportSummary) -> Unit,
        ) {
            viewModelScope.launch {
                val parseResult = parseSshConfig(content)
                val existingByName = repository.profiles.first().associateBy { profile -> profile.name }
                var imported = 0

                parseResult.hosts.forEach { importedHost ->
                    val existing = existingByName[importedHost.alias]
                    val identity =
                        repository.upsertIdentity(
                            existingIdentityId = existing?.identityId,
                            displayName = "${importedHost.user}@${importedHost.hostName}",
                            username = importedHost.user,
                            password = null,
                            privateKeyPath = importedHost.identityFile,
                            certificatePath = importedHost.certificateFile,
                            privateKeyPassphrase = null,
                        )
                    val profile =
                        ConnectionProfile(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            name = importedHost.alias,
                            host = importedHost.hostName,
                            port = importedHost.port,
                            username = identity.username,
                            password = identity.password,
                            privateKeyPath = identity.privateKeyPath,
                            certificatePath = identity.certificatePath,
                            privateKeyPassphrase = identity.privateKeyPassphrase,
                            identityId = identity.id,
                            forwardAgent = importedHost.forwardAgent,
                            proxyJump = importedHost.proxyJump,
                            portForwards = importedHost.portForwards,
                            lastUsedEpochMillis = existing?.lastUsedEpochMillis ?: System.currentTimeMillis(),
                        )
                    repository.save(profile)
                    imported += 1
                }

                onComplete(
                    SshConfigImportSummary(
                        importedCount = imported,
                        skippedCount = parseResult.skippedHostEntries,
                    ),
                )
            }
        }

        companion object {
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
            private const val SECURITY_KEY_ENROLL_DISABLED_MESSAGE =
                "Security key enrollment is disabled in this release."
        }
    }
