package com.opencode.sshterminal.session

import android.content.Context
import com.opencode.sshterminal.data.ConnectionIdentity
import com.opencode.sshterminal.data.ConnectionProfile
import java.io.File

private const val KNOWN_HOSTS_FILE_NAME = "known_hosts"

internal fun ConnectionProfile.toConnectRequest(
    context: Context,
    cols: Int,
    rows: Int,
    keepaliveIntervalSeconds: Int = 15,
    identity: ConnectionIdentity? = null,
    proxyJumpCredentials: Map<String, JumpCredential> = emptyMap(),
): ConnectRequest =
    ConnectRequest(
        host = host,
        port = port,
        username = identity?.username ?: username,
        knownHostsPath = File(context.filesDir, KNOWN_HOSTS_FILE_NAME).absolutePath,
        password = identity?.password ?: password,
        privateKeyPath = identity?.privateKeyPath ?: privateKeyPath,
        privateKeyPassphrase = identity?.privateKeyPassphrase ?: privateKeyPassphrase,
        forwardAgent = forwardAgent,
        proxyJump = proxyJump,
        proxyJumpCredentials = proxyJumpCredentials,
        portForwards = portForwards,
        keepaliveIntervalSeconds = keepaliveIntervalSeconds,
        cols = cols,
        rows = rows,
    )
