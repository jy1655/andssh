package com.opencode.sshterminal.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.data.ConnectionIdentity
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.ConnectionProtocol
import com.opencode.sshterminal.data.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
                        requiresPrivateKeyRelink = profile.requiresPrivateKeyRelink,
                    )
                repository.save(
                    profile.copy(
                        identityId = identity.id,
                        username = identity.username,
                        password = identity.password,
                        privateKeyPath = identity.privateKeyPath,
                        certificatePath = identity.certificatePath,
                        privateKeyPassphrase = identity.privateKeyPassphrase,
                        requiresPrivateKeyRelink = resolvePrivateKeyRelink(profile, identity),
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
                        requiresPrivateKeyRelink = false,
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
                        requiresPrivateKeyRelink = false,
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

        private fun resolvePrivateKeyRelink(
            profile: ConnectionProfile,
            identity: ConnectionIdentity,
        ): Boolean {
            if (!identity.privateKeyPath.isNullOrBlank()) return false
            if (!identity.password.isNullOrBlank()) return false
            return profile.requiresPrivateKeyRelink || identity.requiresPrivateKeyRelink
        }

        companion object {
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
        }
    }
