package com.opencode.sshterminal.ssh

class HostKeyChangedException(
    val host: String,
    val port: Int,
    val fingerprint: String,
    override val message: String,
) : RuntimeException(message)
