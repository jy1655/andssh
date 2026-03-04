package com.opencode.sshterminal.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConnectionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ConnectionProtocol = ConnectionProtocol.SSH,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val certificatePath: String? = null,
    val privateKeyPassphrase: String? = null,
    val forwardAgent: Boolean = false,
    val identityId: String? = null,
    val proxyJump: String? = null,
    val proxyJumpIdentityIds: Map<String, String> = emptyMap(),
    val portKnockSequence: List<Int> = emptyList(),
    val portKnockDelayMillis: Int = 250,
    val portForwards: List<PortForwardRule> = emptyList(),
    val tags: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val group: String? = null,
    val terminalColorSchemeId: String? = null,
    val startupCommand: String? = null,
    val lastUsedEpochMillis: Long = 0L,
)
