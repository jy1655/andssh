package com.opencode.sshterminal.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.data.ConnectionIdentity
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.ConnectionProtocol
import com.opencode.sshterminal.data.ConnectionRepository
import com.opencode.sshterminal.data.parseSshConfig
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

@HiltViewModel
class ConnectionListViewModel
    @Inject
    constructor(
        private val repository: ConnectionRepository,
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
        }
    }
