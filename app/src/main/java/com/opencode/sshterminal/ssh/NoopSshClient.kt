package com.opencode.sshterminal.ssh

import com.opencode.sshterminal.session.ConnectRequest
import kotlinx.coroutines.delay

class NoopSshClient : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession {
        delay(200)
        return object : SshSession {
            private var open = false

            override suspend fun openPtyShell(
                termType: String,
                cols: Int,
                rows: Int,
                environmentVariables: Map<String, String>,
            ) {
                open = true
            }

            override suspend fun readLoop(onBytes: (ByteArray) -> Unit) {
                if (!open) return
                onBytes("Connected to ${request.username}@${request.host}:${request.port}\r\n".toByteArray())
                onBytes("[MVP scaffold] Replace NoopSshClient with sshj adapter.\r\n".toByteArray())
            }

            override suspend fun write(bytes: ByteArray) {
                // no-op for scaffold
            }

            override suspend fun windowChange(
                cols: Int,
                rows: Int,
            ) {
                // no-op for scaffold
            }

            override suspend fun close() {
                open = false
            }
        }
    }
}
