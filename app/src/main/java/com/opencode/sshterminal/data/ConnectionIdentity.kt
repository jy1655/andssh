package com.opencode.sshterminal.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConnectionIdentity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val certificatePath: String? = null,
    val privateKeyPassphrase: String? = null,
    val requiresPrivateKeyRelink: Boolean = false,
    val lastUsedEpochMillis: Long = 0L,
) {
    override fun toString(): String =
        "ConnectionIdentity(id=$id, name=$name, username=$username, hasPassword=${password != null}, " +
            "privateKeyPath=$privateKeyPath, certificatePath=$certificatePath, " +
            "hasPassphrase=${privateKeyPassphrase != null}, " +
            "requiresPrivateKeyRelink=$requiresPrivateKeyRelink)"
}
