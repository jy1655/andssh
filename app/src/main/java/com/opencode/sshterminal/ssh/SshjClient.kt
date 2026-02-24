package com.opencode.sshterminal.ssh

import android.util.Log
import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType
import com.opencode.sshterminal.data.parseProxyJumpEntries
import com.opencode.sshterminal.data.proxyJumpHostPortKey
import com.opencode.sshterminal.session.ConnectRequest
import com.opencode.sshterminal.session.HostKeyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.PublicKey
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class SshjClient : SshClient {
    override suspend fun connect(request: ConnectRequest): SshSession =
        withContext(Dispatchers.IO) {
            val jumpHosts = parseProxyJump(request.proxyJump, request.username)
            val relayClients = mutableListOf<SSHClient>()
            var upstreamClient: SSHClient? = null
            var sessionReady = false
            var finalClient: SSHClient? = null

            try {
                jumpHosts.forEach { jumpTarget ->
                    val relay = SSHClient()
                    relay.connection.keepAlive.keepAliveInterval = request.keepaliveIntervalSeconds.coerceAtLeast(0)
                    val authRequest = request.withJumpCredentialOverride(jumpTarget)
                    connectAndAuthenticate(
                        ssh = relay,
                        request = authRequest,
                        target = jumpTarget,
                        viaClient = upstreamClient,
                    )
                    relayClients += relay
                    upstreamClient = relay
                }

                val ssh = SSHClient()
                ssh.connection.keepAlive.keepAliveInterval = request.keepaliveIntervalSeconds.coerceAtLeast(0)
                val endpoint = JumpTarget(host = request.host, port = request.port, username = request.username)
                connectAndAuthenticate(
                    ssh = ssh,
                    request = request,
                    target = endpoint,
                    viaClient = upstreamClient,
                )
                finalClient = ssh

                val forwardHandles = startPortForwards(ssh, request.portForwards)
                val session = ssh.startSession()
                sessionReady = true
                SshjSession(
                    ssh = ssh,
                    session = session,
                    relayClients = relayClients,
                    forwardHandles = forwardHandles,
                )
            } finally {
                if (!sessionReady) {
                    relayClients.asReversed().forEach { relay ->
                        runCatching { relay.disconnect() }
                        runCatching { relay.close() }
                    }
                    finalClient?.let { client ->
                        runCatching { client.disconnect() }
                        runCatching { client.close() }
                    }
                }
            }
        }

    private fun ConnectRequest.withJumpCredentialOverride(jumpTarget: JumpTarget): ConnectRequest {
        val key = proxyJumpHostPortKey(jumpTarget.host, jumpTarget.port)
        val credential = proxyJumpCredentials[key] ?: return this.copy(username = jumpTarget.username)
        return copy(
            username = credential.username.ifBlank { jumpTarget.username },
            password = credential.password,
            privateKeyPath = credential.privateKeyPath,
            privateKeyPassphrase = credential.privateKeyPassphrase,
        )
    }

    private fun connectAndAuthenticate(
        ssh: SSHClient,
        request: ConnectRequest,
        target: JumpTarget,
        viaClient: SSHClient?,
    ) {
        val verifierSetup = configureHostKeyVerifier(ssh, request)
        try {
            if (viaClient != null) {
                ssh.connectVia(viaClient.newDirectConnection(target.host, target.port))
            } else {
                ssh.connect(target.host, target.port)
            }
            verifierSetup.updatingVerifier?.persistAcceptedHostKeyIfNeeded()
            logKnownHostsDiffIfNeeded(request, verifierSetup, target)
            ssh.authenticate(request)
        } catch (failure: Throwable) {
            val changedFingerprint = verifierSetup.verifier?.changedFingerprint
            if (changedFingerprint != null) {
                throw HostKeyChangedException(
                    host = target.host,
                    port = target.port,
                    fingerprint = changedFingerprint,
                    message = "Host key changed for ${target.host}:${target.port}",
                )
            }
            throw failure
        }
    }

    private fun configureHostKeyVerifier(
        ssh: SSHClient,
        request: ConnectRequest,
    ): VerifierSetup {
        return when (request.hostKeyPolicy) {
            HostKeyPolicy.TRUST_ONCE -> {
                ssh.addHostKeyVerifier(PromiscuousVerifier())
                VerifierSetup(
                    verifier = null,
                    updatingVerifier = null,
                    knownHostsFile = null,
                    knownHostsBefore = null,
                )
            }
            HostKeyPolicy.STRICT -> {
                val knownHosts = ensureKnownHostsFile(request.knownHostsPath)
                val verifier = RejectingKnownHostsVerifier(knownHosts)
                ssh.addHostKeyVerifier(verifier)
                VerifierSetup(
                    verifier = verifier,
                    updatingVerifier = null,
                    knownHostsFile = knownHosts,
                    knownHostsBefore = null,
                )
            }
            HostKeyPolicy.UPDATE_KNOWN_HOSTS -> {
                val knownHosts = ensureKnownHostsFile(request.knownHostsPath)
                val before = readKnownHostsLines(knownHosts)
                val verifier = UpdatingKnownHostsVerifier(knownHosts)
                ssh.addHostKeyVerifier(verifier)
                VerifierSetup(
                    verifier = verifier,
                    updatingVerifier = verifier,
                    knownHostsFile = knownHosts,
                    knownHostsBefore = before,
                )
            }
        }
    }

    private fun logKnownHostsDiffIfNeeded(
        request: ConnectRequest,
        setup: VerifierSetup,
        target: JumpTarget,
    ) {
        if (request.hostKeyPolicy != HostKeyPolicy.UPDATE_KNOWN_HOSTS) return
        val knownHostsFile = setup.knownHostsFile ?: return
        val before = setup.knownHostsBefore.orEmpty()
        val after = readKnownHostsLines(knownHostsFile)
        if (before == after) {
            Log.i(
                TAG,
                "known_hosts unchanged after UPDATE_KNOWN_HOSTS for ${target.host}:${target.port}",
            )
        } else {
            val added = after.toSet() - before.toSet()
            val removed = before.toSet() - after.toSet()
            Log.i(
                TAG,
                "known_hosts updated for ${target.host}:${target.port}, added=${added.size}, removed=${removed.size}",
            )
            added.take(5).forEach { Log.i(TAG, "known_hosts + $it") }
            removed.take(5).forEach { Log.i(TAG, "known_hosts - $it") }
        }
    }

    private fun startPortForwards(
        ssh: SSHClient,
        rules: List<PortForwardRule>,
    ): List<SshForwardHandle> {
        return rules.mapNotNull { rule ->
            when (rule.type) {
                PortForwardType.LOCAL -> startLocalForward(ssh, rule)
                PortForwardType.REMOTE -> startRemoteForward(ssh, rule)
                PortForwardType.DYNAMIC -> startDynamicForward(ssh, rule)
            }
        }
    }

    private fun startLocalForward(
        ssh: SSHClient,
        rule: PortForwardRule,
    ): SshForwardHandle? {
        val targetHost = rule.targetHost ?: return null
        val targetPort = rule.targetPort ?: return null
        val bindHost = rule.bindHost ?: DEFAULT_LOCAL_FORWARD_BIND_HOST
        val bindPort = rule.bindPort

        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(bindHost, bindPort))
        val params =
            Parameters(
                targetHost,
                targetPort,
                bindHost,
                serverSocket.localPort,
            )
        val forwarder = ssh.newLocalPortForwarder(params, serverSocket)
        val thread =
            Thread {
                runCatching { forwarder.listen() }
                    .onFailure { error ->
                        Log.w(
                            TAG,
                            "LocalForward stopped ($bindHost:${serverSocket.localPort} -> $targetHost:$targetPort): ${error.message}",
                        )
                    }
            }.apply {
                isDaemon = true
                name = "ssh-local-forward-${serverSocket.localPort}"
                start()
            }
        Log.i(
            TAG,
            "LocalForward started $bindHost:${serverSocket.localPort} -> $targetHost:$targetPort",
        )
        return LocalForwardHandle(forwarder, serverSocket, thread)
    }

    private fun startRemoteForward(
        ssh: SSHClient,
        rule: PortForwardRule,
    ): SshForwardHandle? {
        val targetHost = rule.targetHost ?: return null
        val targetPort = rule.targetPort ?: return null
        val bindHost = rule.bindHost ?: DEFAULT_REMOTE_FORWARD_BIND_HOST

        val remotePortForwarder = ssh.remotePortForwarder
        val requestedForward = RemotePortForwarder.Forward(bindHost, rule.bindPort)
        val listener = SocketForwardingConnectListener(InetSocketAddress(targetHost, targetPort))
        val activeForward = remotePortForwarder.bind(requestedForward, listener)
        Log.i(
            TAG,
            "RemoteForward started ${activeForward.address}:${activeForward.port} -> $targetHost:$targetPort",
        )
        return RemoteForwardHandle(remotePortForwarder, activeForward)
    }

    private fun startDynamicForward(
        ssh: SSHClient,
        rule: PortForwardRule,
    ): SshForwardHandle? {
        val bindHost = rule.bindHost ?: DEFAULT_LOCAL_FORWARD_BIND_HOST
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(bindHost, rule.bindPort))

        val handle = DynamicForwardHandle(serverSocket)
        val acceptThread =
            Thread {
                while (!serverSocket.isClosed) {
                    val client =
                        runCatching { serverSocket.accept() }
                            .onFailure { error ->
                                if (!serverSocket.isClosed) {
                                    Log.w(TAG, "DynamicForward accept failed: ${error.message}")
                                }
                            }
                            .getOrNull() ?: continue
                    handle.trackSocket(client)
                    val worker =
                        Thread {
                            runCatching { handleDynamicClient(ssh, client) }
                                .onFailure { error ->
                                    Log.w(TAG, "DynamicForward client failed: ${error.message}")
                                }
                            handle.untrackSocket(client)
                        }.apply {
                            isDaemon = true
                            name = "ssh-dynamic-forward-client-${client.port}"
                            start()
                        }
                    handle.trackWorker(worker)
                }
            }.apply {
                isDaemon = true
                name = "ssh-dynamic-forward-accept-${serverSocket.localPort}"
                start()
            }
        handle.setAcceptThread(acceptThread)
        Log.i(TAG, "DynamicForward started $bindHost:${serverSocket.localPort}")
        return handle
    }

    private fun handleDynamicClient(
        ssh: SSHClient,
        client: Socket,
    ) {
        client.soTimeout = DYNAMIC_FORWARD_HANDSHAKE_TIMEOUT_MS
        client.tcpNoDelay = true
        client.use { socket ->
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            if (!performSocks5Greeting(input, output)) return
            val target =
                parseSocks5ConnectTarget(input)
                    ?: run {
                        writeSocks5Reply(output, SOCKS5_REPLY_GENERAL_FAILURE)
                        return
                    }
            val directConnection =
                runCatching { ssh.newDirectConnection(target.host, target.port) }
                    .onFailure {
                        writeSocks5Reply(output, SOCKS5_REPLY_HOST_UNREACHABLE)
                    }
                    .getOrNull() ?: return

            directConnection.use { tunnel ->
                writeSocks5Reply(output, SOCKS5_REPLY_SUCCEEDED)
                socket.soTimeout = 0
                pipeBidirectional(
                    clientInput = input,
                    clientOutput = output,
                    tunnel = tunnel,
                )
            }
        }
    }

    private fun performSocks5Greeting(
        input: BufferedInputStream,
        output: OutputStream,
    ): Boolean {
        val version = input.read()
        if (version != SOCKS5_VERSION) return false
        val methodCount = input.read()
        if (methodCount <= 0) return false
        val methods = readExactly(input, methodCount) ?: return false
        val supportsNoAuth = methods.any { method -> method.toInt() == SOCKS5_AUTH_NO_AUTH }
        if (!supportsNoAuth) {
            output.write(byteArrayOf(SOCKS5_VERSION.toByte(), SOCKS5_AUTH_NO_ACCEPTABLE.toByte()))
            output.flush()
            return false
        }
        output.write(byteArrayOf(SOCKS5_VERSION.toByte(), SOCKS5_AUTH_NO_AUTH.toByte()))
        output.flush()
        return true
    }

    private fun parseSocks5ConnectTarget(input: BufferedInputStream): SocksTarget? {
        val version = input.read()
        if (version != SOCKS5_VERSION) return null
        val command = input.read()
        input.read() // reserved byte
        val addressType = input.read()
        if (command != SOCKS5_CMD_CONNECT) return null

        val host =
            when (addressType) {
                SOCKS5_ATYP_IPV4 -> {
                    val bytes = readExactly(input, 4) ?: return null
                    InetAddress.getByAddress(bytes).hostAddress
                }
                SOCKS5_ATYP_DOMAIN -> {
                    val length = input.read()
                    if (length <= 0) return null
                    val bytes = readExactly(input, length) ?: return null
                    String(bytes, Charsets.UTF_8)
                }
                SOCKS5_ATYP_IPV6 -> {
                    val bytes = readExactly(input, 16) ?: return null
                    InetAddress.getByAddress(bytes).hostAddress
                }
                else -> return null
            }

        val portBytes = readExactly(input, 2) ?: return null
        val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
        if (port <= 0) return null
        return SocksTarget(host = host, port = port)
    }

    private fun writeSocks5Reply(
        output: OutputStream,
        replyCode: Int,
    ) {
        val response =
            byteArrayOf(
                SOCKS5_VERSION.toByte(),
                replyCode.toByte(),
                0x00,
                SOCKS5_ATYP_IPV4.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            )
        output.write(response)
        output.flush()
    }

    private fun pipeBidirectional(
        clientInput: InputStream,
        clientOutput: OutputStream,
        tunnel: DirectConnection,
    ) {
        val tunnelInput = tunnel.inputStream
        val tunnelOutput = tunnel.outputStream
        val upstream =
            Thread {
                copyStream(clientInput, tunnelOutput)
                runCatching { tunnelOutput.close() }
            }.apply {
                isDaemon = true
                name = "ssh-dynamic-forward-upstream"
                start()
            }
        val downstream =
            Thread {
                copyStream(tunnelInput, clientOutput)
                runCatching { clientOutput.close() }
            }.apply {
                isDaemon = true
                name = "ssh-dynamic-forward-downstream"
                start()
            }

        runCatching { upstream.join() }
        runCatching { downstream.join() }
    }

    private fun copyStream(
        input: InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = runCatching { input.read(buffer) }.getOrDefault(-1)
            if (read <= 0) break
            val wrote =
                runCatching {
                    output.write(buffer, 0, read)
                    output.flush()
                }
            if (wrote.isFailure) break
        }
    }

    private fun readExactly(
        input: InputStream,
        byteCount: Int,
    ): ByteArray? {
        val out = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = input.read(out, offset, byteCount - offset)
            if (read < 0) return null
            offset += read
        }
        return out
    }

    private fun parseProxyJump(
        proxyJump: String?,
        defaultUsername: String,
    ): List<JumpTarget> {
        if (proxyJump.isNullOrBlank()) return emptyList()
        return parseProxyJumpEntries(proxyJump)
            .map { entry ->
                JumpTarget(
                    host = entry.host,
                    port = entry.port,
                    username = entry.username ?: defaultUsername,
                )
            }
    }

    private data class VerifierSetup(
        val verifier: KnownHostsVerifierBase?,
        val updatingVerifier: UpdatingKnownHostsVerifier?,
        val knownHostsFile: File?,
        val knownHostsBefore: List<String>?,
    )

    private data class JumpTarget(
        val host: String,
        val port: Int,
        val username: String,
    )

    private data class SocksTarget(
        val host: String,
        val port: Int,
    )

    companion object {
        private const val TAG = "SshjClient"
        private const val DEFAULT_LOCAL_FORWARD_BIND_HOST = "127.0.0.1"
        private const val DEFAULT_REMOTE_FORWARD_BIND_HOST = "127.0.0.1"
        private const val DYNAMIC_FORWARD_HANDSHAKE_TIMEOUT_MS = 15_000
        private const val SOCKS5_VERSION = 5
        private const val SOCKS5_AUTH_NO_AUTH = 0
        private const val SOCKS5_AUTH_NO_ACCEPTABLE = 0xFF
        private const val SOCKS5_CMD_CONNECT = 1
        private const val SOCKS5_ATYP_IPV4 = 1
        private const val SOCKS5_ATYP_DOMAIN = 3
        private const val SOCKS5_ATYP_IPV6 = 4
        private const val SOCKS5_REPLY_SUCCEEDED = 0
        private const val SOCKS5_REPLY_GENERAL_FAILURE = 1
        private const val SOCKS5_REPLY_HOST_UNREACHABLE = 4
    }
}

private interface SshForwardHandle {
    fun close()
}

private class LocalForwardHandle(
    private val forwarder: net.schmizz.sshj.connection.channel.direct.LocalPortForwarder,
    private val serverSocket: ServerSocket,
    private val thread: Thread,
) : SshForwardHandle {
    override fun close() {
        runCatching { forwarder.close() }
        runCatching { serverSocket.close() }
        runCatching { thread.interrupt() }
    }
}

private class RemoteForwardHandle(
    private val remotePortForwarder: RemotePortForwarder,
    private val forward: RemotePortForwarder.Forward,
) : SshForwardHandle {
    override fun close() {
        runCatching { remotePortForwarder.cancel(forward) }
    }
}

private class DynamicForwardHandle(
    private val serverSocket: ServerSocket,
) : SshForwardHandle {
    @Volatile
    private var acceptThread: Thread? = null

    private val workers = Collections.newSetFromMap(ConcurrentHashMap<Thread, Boolean>())
    private val sockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    fun setAcceptThread(thread: Thread) {
        acceptThread = thread
    }

    fun trackWorker(thread: Thread) {
        workers += thread
    }

    fun trackSocket(socket: Socket) {
        sockets += socket
    }

    fun untrackSocket(socket: Socket) {
        sockets.remove(socket)
    }

    override fun close() {
        runCatching { serverSocket.close() }
        acceptThread?.let { thread -> runCatching { thread.interrupt() } }
        sockets.toList().forEach { socket -> runCatching { socket.close() } }
        workers.toList().forEach { worker -> runCatching { worker.interrupt() } }
    }
}

private abstract class KnownHostsVerifierBase(knownHosts: File) : OpenSSHKnownHosts(knownHosts) {
    var changedFingerprint: String? = null
}

private class RejectingKnownHostsVerifier(knownHosts: File) : KnownHostsVerifierBase(knownHosts) {
    override fun hostKeyChangedAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        return false
    }
}

private class UpdatingKnownHostsVerifier(knownHosts: File) : KnownHostsVerifierBase(knownHosts) {
    private var acceptedHost: String? = null
    private var acceptedKey: PublicKey? = null

    override fun hostKeyUnverifiableAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        acceptedHost = hostname
        acceptedKey = key
        return true
    }

    override fun hostKeyChangedAction(
        hostname: String?,
        key: java.security.PublicKey?,
    ): Boolean {
        changedFingerprint = key?.let { SecurityUtils.getFingerprint(it) } ?: "unknown"
        acceptedHost = hostname
        acceptedKey = key
        return true
    }

    fun persistAcceptedHostKeyIfNeeded() {
        val host = acceptedHost ?: return
        val key = acceptedKey ?: return
        upsertKnownHostEntry(khFile, host, key)
    }
}

private class SshjSession(
    private val ssh: SSHClient,
    private val session: Session,
    private val relayClients: List<SSHClient>,
    private val forwardHandles: List<SshForwardHandle>,
) : SshSession {
    private var shell: Session.Shell? = null

    override suspend fun openPtyShell(
        termType: String,
        cols: Int,
        rows: Int,
    ) = withContext(Dispatchers.IO) {
        // Best-effort: servers may reject (AcceptEnv policy)
        runCatching { session.setEnvVar("LANG", "en_US.UTF-8") }
        runCatching { session.setEnvVar("LC_CTYPE", "en_US.UTF-8") }
        session.allocatePTY(termType, cols, rows, 0, 0, linkedMapOf<PTYMode, Int>())
        shell = session.startShell()
    }

    override suspend fun readLoop(onBytes: (ByteArray) -> Unit) =
        withContext(Dispatchers.IO) {
            val activeShell = checkNotNull(shell) { "shell not started; call openPtyShell first" }
            val input = activeShell.inputStream
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    onBytes(buffer.copyOf(read))
                }
            }
        }

    override suspend fun write(bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val activeShell = checkNotNull(shell) { "shell not started; call openPtyShell first" }
            activeShell.outputStream.write(bytes)
            activeShell.outputStream.flush()
        }

    override suspend fun windowChange(
        cols: Int,
        rows: Int,
    ) = withContext(Dispatchers.IO) {
        shell?.changeWindowDimensions(cols, rows, 0, 0)
        Unit
    }

    override suspend fun close() =
        withContext(Dispatchers.IO) {
            forwardHandles.asReversed().forEach { handle -> runCatching { handle.close() } }
            runCatching { shell?.close() }
            runCatching { session.close() }
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            relayClients.asReversed().forEach { relay ->
                runCatching { relay.disconnect() }
                runCatching { relay.close() }
            }
            Unit
        }
}
