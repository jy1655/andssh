package com.opencode.sshterminal.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val privateKeyPassphrase: String? = null,
    val forwardAgent: Boolean = false,
    val identityId: String? = null,
    val proxyJump: String? = null,
    val proxyJumpIdentityIds: Map<String, String> = emptyMap(),
    val portForwards: List<PortForwardRule> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val group: String? = null,
    val terminalColorSchemeId: String? = null,
    val startupCommand: String? = null,
    val lastUsedEpochMillis: Long = 0L,
)
