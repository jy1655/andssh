package com.opencode.sshterminal.ssh

import android.util.Log
import com.opencode.sshterminal.session.ConnectRequest
import com.opencode.sshterminal.session.HostKeyPolicy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

class SshjClient : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession = withContext(Dispatchers.IO) {
        val ssh = SSHClient()
        val verifierSetup = configureHostKeyVerifier(ssh, request)
        ssh.connection.keepAlive.keepAliveInterval = 15

        try {
            ssh.connect(request.host, request.port)
            logKnownHostsDiffIfNeeded(request, verifierSetup)
        } catch (t: Throwable) {
            val changedFingerprint = verifierSetup.verifier?.changedFingerprint
            if (changedFingerprint != null) {
                runCatching { ssh.disconnect() }
                runCatching { ssh.close() }
                throw HostKeyChangedException(
                    host = request.host,
                    port = request.port,
                    fingerprint = changedFingerprint,
                    message = "Host key changed for ${request.host}:${request.port}"
                )
            }
            throw t
        }

        when {
            !request.password.isNullOrEmpty() -> {
                ssh.authPassword(request.username, request.password)
            }
            !request.privateKeyPath.isNullOrEmpty() -> {
                val keyProvider = if (request.privateKeyPassphrase.isNullOrEmpty()) {
                    ssh.loadKeys(request.privateKeyPath)
                } else {
                    ssh.loadKeys(request.privateKeyPath, request.privateKeyPassphrase)
                }
                ssh.authPublickey(request.username, keyProvider)
            }
            else -> error("Either password or privateKeyPath must be provided")
        }

        val session = ssh.startSession()
        SshjSession(ssh, session)
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        request: ConnectRequest
    ): VerifierSetup {
        return when (request.hostKeyPolicy) {
            HostKeyPolicy.TRUST_ONCE -> {
                ssh.addHostKeyVerifier(PromiscuousVerifier())
                VerifierSetup(verifier = null, knownHostsFile = null, knownHostsBefore = null)
            }
            HostKeyPolicy.STRICT -> {
                val knownHosts = ensureFileExists(request.knownHostsPath)
                val verifier = RejectingKnownHostsVerifier(knownHosts)
                ssh.addHostKeyVerifier(verifier)
                VerifierSetup(verifier = verifier, knownHostsFile = knownHosts, knownHostsBefore = null)
            }
            HostKeyPolicy.UPDATE_KNOWN_HOSTS -> {
                val knownHosts = ensureFileExists(request.knownHostsPath)
                val before = readKnownHostsLines(knownHosts)
                val verifier = UpdatingKnownHostsVerifier(knownHosts)
                ssh.addHostKeyVerifier(verifier)
                VerifierSetup(verifier = verifier, knownHostsFile = knownHosts, knownHostsBefore = before)
            }
        }
    }

    private fun ensureFileExists(path: String): File {
        val file = File(path)
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.createNewFile()
        }
        return file
    }

    private fun readKnownHostsLines(file: File): List<String> =
        if (file.exists()) file.readLines() else emptyList()

    private fun logKnownHostsDiffIfNeeded(request: ConnectRequest, setup: VerifierSetup) {
        if (request.hostKeyPolicy != HostKeyPolicy.UPDATE_KNOWN_HOSTS) return
        val knownHostsFile = setup.knownHostsFile ?: return
        val before = setup.knownHostsBefore ?: emptyList()
        val after = readKnownHostsLines(knownHostsFile)

        if (before == after) {
            Log.i(TAG, "known_hosts unchanged after UPDATE_KNOWN_HOSTS for ${request.host}:${request.port}")
            return
        }

        val added = after.toSet() - before.toSet()
        val removed = before.toSet() - after.toSet()

        Log.i(
            TAG,
            "known_hosts updated for ${request.host}:${request.port}, added=${added.size}, removed=${removed.size}"
        )
        added.take(5).forEach { Log.i(TAG, "known_hosts + $it") }
        removed.take(5).forEach { Log.i(TAG, "known_hosts - $it") }
    }

    private data class VerifierSetup(
        val verifier: KnownHostsVerifierBase?,
        val knownHostsFile: File?,
        val knownHostsBefore: List<String>?
    )

    companion object {
        private const val TAG = "SshjClient"
    }
}

private abstract class KnownHostsVerifierBase(knownHosts: File) : OpenSSHKnownHosts(knownHosts) {
    var changedFingerprint: String? = null
}

private class RejectingKnownHostsVerifier(knownHosts: File) : KnownHostsVerifierBase(knownHosts) {
    override fun hostKeyChangedAction(hostname: String?, key: java.security.PublicKey?): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        return false
    }
}

private class UpdatingKnownHostsVerifier(knownHosts: File) : KnownHostsVerifierBase(knownHosts) {
    override fun hostKeyUnverifiableAction(hostname: String?, key: java.security.PublicKey?): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        return true
    }

    override fun hostKeyChangedAction(hostname: String?, key: java.security.PublicKey?): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        return true
    }
}

private class SshjSession(
    private val ssh: SSHClient,
    private val session: Session
) : SshSession {
    private var shell: Session.Shell? = null

    override suspend fun openPtyShell(termType: String, cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        // Best-effort: servers may reject (AcceptEnv policy)
        runCatching { session.setEnvVar("LANG", "en_US.UTF-8") }
        runCatching { session.setEnvVar("LC_CTYPE", "en_US.UTF-8") }
        session.allocatePTY(termType, cols, rows, 0, 0, linkedMapOf<PTYMode, Int>())
        shell = session.startShell()
    }

    override suspend fun readLoop(onBytes: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val activeShell = checkNotNull(shell) { "shell not started; call openPtyShell first" }
        val input = activeShell.inputStream
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            onBytes(buffer.copyOf(read))
        }
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val activeShell = checkNotNull(shell) { "shell not started; call openPtyShell first" }
        activeShell.outputStream.write(bytes)
        activeShell.outputStream.flush()
    }

    override suspend fun windowChange(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        shell?.changeWindowDimensions(cols, rows, 0, 0)
        Unit
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { shell?.close() }
        runCatching { session.close() }
        runCatching { ssh.disconnect() }
        runCatching { ssh.close() }
        Unit
    }
}
