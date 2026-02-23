package com.opencode.sshterminal.session

import android.content.Context
import com.opencode.sshterminal.data.ConnectionProfile
import java.io.File

private const val KNOWN_HOSTS_FILE_NAME = "known_hosts"

internal fun ConnectionProfile.toConnectRequest(
    context: Context,
    cols: Int,
    rows: Int,
): ConnectRequest =
    ConnectRequest(
        host = host,
        port = port,
        username = username,
        knownHostsPath = File(context.filesDir, KNOWN_HOSTS_FILE_NAME).absolutePath,
        password = password,
        privateKeyPath = privateKeyPath,
        cols = cols,
        rows = rows,
    )
