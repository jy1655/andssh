package com.opencode.sshterminal.sftp

import com.opencode.sshterminal.session.ConnectRequest
import com.opencode.sshterminal.session.HostKeyPolicy
import com.opencode.sshterminal.ssh.authenticate
import com.opencode.sshterminal.ssh.ensureKnownHostsFile
import com.opencode.sshterminal.ssh.upsertKnownHostEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteResourceInfo
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.EnumSet

class SshjSftpAdapter : SftpChannelAdapter {
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    override val isConnected: Boolean get() = ssh?.isConnected == true && sftp != null

    override suspend fun connect(request: ConnectRequest) =
        withContext(Dispatchers.IO) {
            close()
            val client = SSHClient()
            val verifier = configureHostKeyVerifier(client, request)
            var success = false
            try {
                client.connect(request.host, request.port)
                client.authenticate(request)
                verifier?.persistAcceptedHostKeyIfNeeded()
                ssh = client
                sftp = client.newSFTPClient()
                success = true
            } finally {
                if (!success) {
                    runCatching { client.disconnect() }
                    runCatching { client.close() }
                }
            }
        }

    override fun close() {
        runCatching { sftp?.close() }
        runCatching { ssh?.disconnect() }
        runCatching { ssh?.close() }
        sftp = null
        ssh = null
    }

    override suspend fun list(remotePath: String): List<RemoteEntry> =
        withContext(Dispatchers.IO) {
            requireSftp().ls(remotePath).map { it.toRemoteEntry() }
        }

    override suspend fun uploadStream(
        input: InputStream,
        remotePath: String,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?,
    ) = withContext(Dispatchers.IO) {
        val handle = requireSftp().open(remotePath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
        try {
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var offset = 0L
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                handle.write(offset, buf, 0, read)
                offset += read
                onProgress?.invoke(offset, totalBytes)
            }
        } finally {
            runCatching { handle.close() }
        }
    }

    override suspend fun downloadStream(
        remotePath: String,
        output: OutputStream,
        onProgress: ((Long, Long) -> Unit)?,
    ) = withContext(Dispatchers.IO) {
        val attrs = requireSftp().stat(remotePath)
        val totalBytes = attrs.size
        val handle = requireSftp().open(remotePath, EnumSet.of(OpenMode.READ))
        try {
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var offset = 0L
            while (true) {
                val read = handle.read(offset, buf, 0, buf.size)
                if (read < 0) break
                output.write(buf, 0, read)
                offset += read
                onProgress?.invoke(offset, totalBytes)
            }
            output.flush()
        } finally {
            runCatching { handle.close() }
        }
    }

    override suspend fun mkdir(remotePath: String) =
        withContext(Dispatchers.IO) {
            requireSftp().mkdir(remotePath)
        }

    override suspend fun rm(remotePath: String) =
        withContext(Dispatchers.IO) {
            removeRecursively(requireSftp(), remotePath)
        }

    override suspend fun rename(
        oldPath: String,
        newPath: String,
    ) = withContext(Dispatchers.IO) {
        requireSftp().rename(oldPath, newPath)
    }

    private fun requireSftp(): SFTPClient = sftp ?: error("Not connected. Call connect() first.")

    private fun removeRecursively(
        sftpClient: SFTPClient,
        remotePath: String,
    ) {
        val attrs = sftpClient.stat(remotePath)
        if (attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY) {
            sftpClient
                .ls(remotePath)
                .filterNot { info -> info.name == "." || info.name == ".." }
                .forEach { child -> removeRecursively(sftpClient, child.path) }
            sftpClient.rmdir(remotePath)
        } else {
            sftpClient.rm(remotePath)
        }
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        request: ConnectRequest,
    ): UpdatingKnownHostsVerifier? {
        return when (request.hostKeyPolicy) {
            HostKeyPolicy.TRUST_ONCE ->
                ssh.addHostKeyVerifier(PromiscuousVerifier())
                    .let { null }
            HostKeyPolicy.STRICT ->
                ssh.addHostKeyVerifier(
                    RejectingKnownHostsVerifier(ensureKnownHostsFile(request.knownHostsPath)),
                ).let { null }
            HostKeyPolicy.UPDATE_KNOWN_HOSTS -> {
                val verifier = UpdatingKnownHostsVerifier(ensureKnownHostsFile(request.knownHostsPath))
                ssh.addHostKeyVerifier(verifier)
                verifier
            }
        }
    }
}

private fun RemoteResourceInfo.toRemoteEntry(): RemoteEntry {
    val attributes = attributes
    return RemoteEntry(
        name = name,
        path = path,
        isDirectory = isDirectory,
        sizeBytes = attributes.size,
        modifiedEpochSec = attributes.mtime,
    )
}

private class RejectingKnownHostsVerifier(knownHosts: File) : OpenSSHKnownHosts(knownHosts) {
    override fun hostKeyChangedAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean = false
}

private class UpdatingKnownHostsVerifier(knownHosts: File) : OpenSSHKnownHosts(knownHosts) {
    private var acceptedHost: String? = null
    private var acceptedKey: PublicKey? = null

    override fun hostKeyUnverifiableAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean = true

    override fun hostKeyChangedAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean = false

    override fun verify(
        hostname: String?,
        port: Int,
        key: PublicKey?,
    ): Boolean {
        val verified = super.verify(hostname, port, key)
        if (verified && hostname != null && key != null) {
            acceptedHost = hostname
            acceptedKey = key
        }
        return verified
    }

    fun persistAcceptedHostKeyIfNeeded() {
        val host = acceptedHost ?: return
        val key = acceptedKey ?: return
        upsertKnownHostEntry(khFile, host, key)
    }
}

internal fun isSftpNoSuchFileError(t: Throwable): Boolean {
    var cur: Throwable? = t
    while (cur != null) {
        val message = cur.message.orEmpty()
        if (
            "SSH_FX_NO_SUCH_FILE" in message ||
            "NO_SUCH_FILE" in message ||
            "No such file" in message
        ) {
            return true
        }
        cur = cur.cause
    }
    return false
}
