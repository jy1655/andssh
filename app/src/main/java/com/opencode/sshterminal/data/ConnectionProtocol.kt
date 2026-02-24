package com.opencode.sshterminal.data

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionProtocol {
    SSH,
    MOSH,
}
