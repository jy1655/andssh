package com.opencode.sshterminal.ssh

interface SshSession {
    suspend fun openPtyShell(
        termType: String,
        cols: Int,
        rows: Int,
    )

    suspend fun readLoop(onBytes: (ByteArray) -> Unit)

    suspend fun write(bytes: ByteArray)

    suspend fun windowChange(
        cols: Int,
        rows: Int,
    )

    suspend fun close()
}
