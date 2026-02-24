package com.opencode.sshterminal.session

import com.opencode.sshterminal.data.PortForwardRule
import java.util.UUID

data class SessionId(val value: String = UUID.randomUUID().toString())

enum class SessionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    FAILED,
}

data class SessionSnapshot(
    val sessionId: SessionId,
    val state: SessionState,
    val host: String,
    val port: Int,
    val username: String,
    val error: String? = null,
    val hostKeyAlert: HostKeyAlert? = null,
)

data class HostKeyAlert(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val message: String,
)

data class ConnectRequest(
    val host: String,
    val port: Int = 22,
    val username: String,
    val knownHostsPath: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val privateKeyPassphrase: String? = null,
    val forwardAgent: Boolean = false,
    val proxyJump: String? = null,
    val proxyJumpCredentials: Map<String, JumpCredential> = emptyMap(),
    val portForwards: List<PortForwardRule> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val keepaliveIntervalSeconds: Int = 15,
    val compressionEnabled: Boolean = false,
    val hostKeyPolicy: HostKeyPolicy = HostKeyPolicy.STRICT,
    val termType: String = "xterm-256color",
    val cols: Int,
    val rows: Int,
)

data class JumpCredential(
    val username: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val privateKeyPassphrase: String? = null,
)

enum class HostKeyPolicy {
    STRICT,
    TRUST_ONCE,
    UPDATE_KNOWN_HOSTS,
}
