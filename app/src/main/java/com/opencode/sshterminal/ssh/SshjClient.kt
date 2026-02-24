package com.opencode.sshterminal.ssh

import android.util.Log
import com.hierynomus.sshj.signature.SignatureEdDSA
import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType
import com.opencode.sshterminal.data.parseProxyJumpEntries
import com.opencode.sshterminal.data.proxyJumpHostPortKey
import com.opencode.sshterminal.session.ConnectRequest
import com.opencode.sshterminal.session.HostKeyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHPacket
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.Connection
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.Channel
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.PTYMode
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.connection.channel.forwarded.AbstractForwardedChannel
import net.schmizz.sshj.connection.channel.forwarded.AbstractForwardedChannelOpener
import net.schmizz.sshj.connection.channel.forwarded.ConnectListener
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import net.schmizz.sshj.signature.Signature
import net.schmizz.sshj.signature.SignatureDSA
import net.schmizz.sshj.signature.SignatureECDSA
import net.schmizz.sshj.signature.SignatureRSA
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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

                val forwardHandles = startPortForwards(ssh, request.portForwards).toMutableList()
                val session =
                    AgentForwardingSessionChannel(
                        connection = ssh.connection,
                        remoteCharset = ssh.remoteCharset,
                    ).apply { open() }
                startAgentForwarding(
                    ssh = ssh,
                    session = session,
                    request = request,
                )?.let(forwardHandles::add)
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

    private fun startAgentForwarding(
        ssh: SSHClient,
        session: AgentForwardingSessionChannel,
        request: ConnectRequest,
    ): SshForwardHandle? {
        if (!request.forwardAgent) return null
        val identities = loadAgentIdentities(ssh, request)
        val opener =
            AgentForwardingChannelOpener(
                connection = ssh.connection,
                identities = identities,
            )
        ssh.connection.attach(opener)
        val timeoutMs = ssh.connection.timeoutMs.takeIf { it > 0 } ?: DEFAULT_AGENT_FORWARD_TIMEOUT_MS
        return runCatching {
            session.requestAgentForwarding(timeoutMs)
            Log.i(TAG, "SSH agent forwarding enabled (keys=${identities.size})")
            AgentForwardingHandle(
                connection = ssh.connection,
                opener = opener,
            )
        }.onFailure { error ->
            runCatching { ssh.connection.forget(opener) }
            Log.w(TAG, "SSH agent forwarding unavailable: ${error.message}")
        }.getOrNull()
    }

    private fun loadAgentIdentities(
        ssh: SSHClient,
        request: ConnectRequest,
    ): List<AgentIdentity> {
        val keySpecs =
            buildList {
                request.privateKeyPath?.takeIf { path -> path.isNotBlank() }?.let { path ->
                    add(AgentKeySpec(path = path, passphrase = request.privateKeyPassphrase))
                }
                request.proxyJumpCredentials.values.forEach { credential ->
                    credential.privateKeyPath?.takeIf { path -> path.isNotBlank() }?.let { path ->
                        add(AgentKeySpec(path = path, passphrase = credential.privateKeyPassphrase))
                    }
                }
            }

        if (keySpecs.isEmpty()) return emptyList()

        val identities = mutableListOf<AgentIdentity>()
        val knownBlobs = mutableSetOf<String>()
        keySpecs.forEach { keySpec ->
            val keyProvider =
                runCatching {
                    if (keySpec.passphrase.isNullOrEmpty()) {
                        ssh.loadKeys(keySpec.path)
                    } else {
                        ssh.loadKeys(keySpec.path, keySpec.passphrase)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to load agent key `${keySpec.path}`: ${error.message}")
                }.getOrNull() ?: return@forEach

            val publicKey =
                runCatching { keyProvider.public }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to read public key `${keySpec.path}`: ${error.message}")
                    }.getOrNull() ?: return@forEach

            val privateKey =
                runCatching { keyProvider.private }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to read private key `${keySpec.path}`: ${error.message}")
                    }.getOrNull() ?: return@forEach

            val keyType =
                runCatching { keyProvider.type }
                    .onFailure { error ->
                        Log.w(TAG, "Failed to detect key type `${keySpec.path}`: ${error.message}")
                    }.getOrNull() ?: return@forEach

            if (keyType == KeyType.UNKNOWN) {
                Log.w(TAG, "Skipping unsupported agent key type for `${keySpec.path}`")
                return@forEach
            }

            val publicKeyBlob = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
            val encodedBlob = Base64.getEncoder().encodeToString(publicKeyBlob)
            if (!knownBlobs.add(encodedBlob)) return@forEach

            identities +=
                AgentIdentity(
                    keyBlob = publicKeyBlob,
                    comment = File(keySpec.path).name,
                    privateKey = privateKey,
                    keyType = keyType,
                )
        }
        return identities
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

    private data class AgentKeySpec(
        val path: String,
        val passphrase: String?,
    )

    private data class AgentIdentity(
        val keyBlob: ByteArray,
        val comment: String,
        val privateKey: PrivateKey,
        val keyType: KeyType,
    )

    private fun signWithAgentIdentity(
        identity: AgentIdentity,
        data: ByteArray,
        flags: Int,
    ): ByteArray? {
        val signature = buildAgentSignature(identity.keyType, flags) ?: return null
        return runCatching {
            signature.initSign(identity.privateKey)
            signature.update(data)
            val encoded = signature.encode(signature.sign())
            Buffer.PlainBuffer()
                .putSignature(signature.signatureName, encoded)
                .compactData
        }.onFailure { error ->
            Log.w(TAG, "Agent signing failed for key `${identity.comment}`: ${error.message}")
        }.getOrNull()
    }

    private fun buildAgentSignature(
        keyType: KeyType,
        flags: Int,
    ): Signature? {
        val normalized =
            when (keyType) {
                KeyType.RSA_CERT -> KeyType.RSA
                KeyType.DSA_CERT -> KeyType.DSA
                KeyType.ECDSA256_CERT -> KeyType.ECDSA256
                KeyType.ECDSA384_CERT -> KeyType.ECDSA384
                KeyType.ECDSA521_CERT -> KeyType.ECDSA521
                KeyType.ED25519_CERT -> KeyType.ED25519
                else -> keyType
            }
        return when (normalized) {
            KeyType.RSA -> {
                when {
                    flags and SSH_AGENT_RSA_SHA2_512_FLAG != 0 -> SignatureRSA.FactoryRSASHA512().create()
                    flags and SSH_AGENT_RSA_SHA2_256_FLAG != 0 -> SignatureRSA.FactoryRSASHA256().create()
                    else -> SignatureRSA.FactorySSHRSA().create()
                }
            }
            KeyType.DSA -> SignatureDSA.Factory().create()
            KeyType.ECDSA256 -> SignatureECDSA.Factory256().create()
            KeyType.ECDSA384 -> SignatureECDSA.Factory384().create()
            KeyType.ECDSA521 -> SignatureECDSA.Factory521().create()
            KeyType.ED25519 -> SignatureEdDSA.Factory().create()
            else -> null
        }
    }

    private inner class AgentForwardingChannelOpener(
        connection: Connection,
        private val identities: List<AgentIdentity>,
    ) : AbstractForwardedChannelOpener(SSH_AGENT_CHANNEL_TYPE, connection) {
        override fun handleOpen(buffer: SSHPacket) {
            val channel =
                try {
                    AgentForwardedChannel(
                        connection = conn,
                        recipient = buffer.readUInt32AsInt(),
                        remoteWindowSize = buffer.readUInt32(),
                        remoteMaxPacketSize = buffer.readUInt32(),
                    )
                } catch (error: Buffer.BufferException) {
                    throw ConnectionException(error)
                }
            callListener(
                AgentForwardingConnectListener(
                    identities = identities,
                    sign = ::signWithAgentIdentity,
                ),
                channel,
            )
        }
    }

    private inner class AgentForwardingHandle(
        private val connection: Connection,
        private val opener: AgentForwardingChannelOpener,
    ) : SshForwardHandle {
        override fun close() {
            runCatching { connection.forget(opener) }
        }
    }

    private class AgentForwardingSessionChannel(
        connection: Connection,
        remoteCharset: java.nio.charset.Charset,
    ) : SessionChannel(connection, remoteCharset) {
        fun requestAgentForwarding(timeoutMs: Int) {
            sendChannelRequest(
                SSH_AGENT_REQUEST_TYPE,
                true,
                Buffer.PlainBuffer(),
            ).await(timeoutMs.coerceAtLeast(1).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private class AgentForwardedChannel(
        connection: Connection,
        recipient: Int,
        remoteWindowSize: Long,
        remoteMaxPacketSize: Long,
    ) : AbstractForwardedChannel(
            connection,
            SSH_AGENT_CHANNEL_TYPE,
            recipient,
            remoteWindowSize,
            remoteMaxPacketSize,
            "127.0.0.1",
            0,
        )

    private class AgentForwardingConnectListener(
        private val identities: List<AgentIdentity>,
        private val sign: (AgentIdentity, ByteArray, Int) -> ByteArray?,
    ) : ConnectListener {
        override fun gotConnect(channel: Channel.Forwarded) {
            try {
                channel.confirm()
                val input = channel.inputStream
                val output = channel.outputStream
                while (true) {
                    val requestPayload = readAgentPacket(input) ?: break
                    val responsePayload = handleAgentPacket(requestPayload)
                    writeAgentPacket(output, responsePayload)
                }
            } catch (error: IOException) {
                throw error
            } catch (error: Throwable) {
                throw IOException("SSH agent channel failed", error)
            } finally {
                runCatching { channel.close() }
            }
        }

        private fun handleAgentPacket(payload: ByteArray): ByteArray {
            val packet = Buffer.PlainBuffer(payload)
            return try {
                when (packet.readByte().toInt() and 0xFF) {
                    SSH_AGENT_REQUEST_IDENTITIES -> buildIdentitiesResponse()
                    SSH_AGENT_SIGN_REQUEST -> buildSignResponse(packet)
                    else -> failureResponse()
                }
            } catch (_: Buffer.BufferException) {
                failureResponse()
            }
        }

        private fun buildIdentitiesResponse(): ByteArray {
            val response = Buffer.PlainBuffer()
            response.putByte(SSH_AGENT_IDENTITIES_ANSWER.toByte())
            response.putUInt32FromInt(identities.size)
            identities.forEach { identity ->
                response.putString(identity.keyBlob)
                response.putString(identity.comment)
            }
            return response.compactData
        }

        private fun buildSignResponse(packet: Buffer.PlainBuffer): ByteArray {
            val keyBlob =
                try {
                    packet.readStringAsBytes()
                } catch (_: Buffer.BufferException) {
                    return failureResponse()
                }
            val data =
                try {
                    packet.readStringAsBytes()
                } catch (_: Buffer.BufferException) {
                    return failureResponse()
                }
            val flags =
                try {
                    packet.readUInt32AsInt()
                } catch (_: Buffer.BufferException) {
                    return failureResponse()
                }
            val identity = identities.firstOrNull { candidate -> candidate.keyBlob.contentEquals(keyBlob) } ?: return failureResponse()
            val signatureBlob = sign(identity, data, flags) ?: return failureResponse()
            return Buffer.PlainBuffer()
                .putByte(SSH_AGENT_SIGN_RESPONSE.toByte())
                .putString(signatureBlob)
                .compactData
        }

        private fun failureResponse(): ByteArray = byteArrayOf(SSH_AGENT_FAILURE.toByte())

        private fun readAgentPacket(input: InputStream): ByteArray? {
            val b0 = input.read()
            if (b0 < 0) return null
            val b1 = input.read()
            val b2 = input.read()
            val b3 = input.read()
            if (b1 < 0 || b2 < 0 || b3 < 0) {
                throw IOException("Unexpected EOF while reading SSH agent header")
            }
            val size = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            if (size <= 0 || size > SSH_AGENT_MAX_PACKET_SIZE) {
                throw IOException("Invalid SSH agent packet size: $size")
            }
            val payload = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val read = input.read(payload, offset, size - offset)
                if (read < 0) {
                    throw IOException("Unexpected EOF while reading SSH agent payload")
                }
                offset += read
            }
            return payload
        }

        private fun writeAgentPacket(
            output: OutputStream,
            payload: ByteArray,
        ) {
            val size = payload.size
            output.write((size ushr 24) and 0xFF)
            output.write((size ushr 16) and 0xFF)
            output.write((size ushr 8) and 0xFF)
            output.write(size and 0xFF)
            output.write(payload)
            output.flush()
        }
    }

    companion object {
        private const val TAG = "SshjClient"
        private const val DEFAULT_LOCAL_FORWARD_BIND_HOST = "127.0.0.1"
        private const val DEFAULT_REMOTE_FORWARD_BIND_HOST = "127.0.0.1"
        private const val DYNAMIC_FORWARD_HANDSHAKE_TIMEOUT_MS = 15_000
        private const val DEFAULT_AGENT_FORWARD_TIMEOUT_MS = 15_000
        private const val SSH_AGENT_CHANNEL_TYPE = "auth-agent@openssh.com"
        private const val SSH_AGENT_REQUEST_TYPE = "auth-agent-req@openssh.com"
        private const val SSH_AGENT_MAX_PACKET_SIZE = 262_144
        private const val SSH_AGENT_FAILURE = 5
        private const val SSH_AGENT_REQUEST_IDENTITIES = 11
        private const val SSH_AGENT_IDENTITIES_ANSWER = 12
        private const val SSH_AGENT_SIGN_REQUEST = 13
        private const val SSH_AGENT_SIGN_RESPONSE = 14
        private const val SSH_AGENT_RSA_SHA2_256_FLAG = 0x02
        private const val SSH_AGENT_RSA_SHA2_512_FLAG = 0x04
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
        environmentVariables: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        // Best-effort: servers may reject (AcceptEnv policy)
        runCatching { session.setEnvVar("LANG", "en_US.UTF-8") }
        runCatching { session.setEnvVar("LC_CTYPE", "en_US.UTF-8") }
        environmentVariables.forEach { (name, value) ->
            if (name.isNotBlank()) {
                runCatching { session.setEnvVar(name, value) }
            }
        }
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
