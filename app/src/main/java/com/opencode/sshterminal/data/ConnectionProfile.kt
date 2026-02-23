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
    val lastUsedEpochMillis: Long = 0L,
)
