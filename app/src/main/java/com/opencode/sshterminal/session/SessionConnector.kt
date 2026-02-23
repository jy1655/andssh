package com.opencode.sshterminal.session

import android.util.Log
import com.opencode.sshterminal.ssh.HostKeyChangedException
import com.opencode.sshterminal.ssh.SshClient
import com.opencode.sshterminal.ssh.SshSession
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class SessionConnector(
    private val scope: CoroutineScope,
    private val sshClient: SshClient,
    private val tabRegistry: MutableMap<TabId, TabSession>,
    private val activeTabId: StateFlow<TabId?>,
    private val refreshFlows: () -> Unit,
) {
    fun startConnect(
        tabId: TabId,
        request: ConnectRequest,
    ) {
        var previousJob: Job?
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return
            previousJob = tab.connectJob
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value =
                SessionSnapshot(
                    sessionId = SessionId(),
                    state = SessionState.CONNECTING,
                    host = request.host,
                    port = request.port,
                    username = request.username,
                )
        }
        previousJob?.cancel()
        refreshFlows()
        val job =
            scope.launch {
                connectTab(tabId, request)
            }
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId]
            if (tab == null) {
                job.cancel()
            } else {
                tab.connectJob = job
            }
        }
    }

    fun sendInputToTab(
        tabId: TabId,
        bytes: ByteArray,
    ) {
        scope.launch {
            val sshSession =
                synchronized(tabRegistry) {
                    tabRegistry[tabId]?.sshSession
                }
            sshSession?.write(bytes)
        }
    }

    fun reconnectPendingHostKey(policy: HostKeyPolicy) {
        val pendingReconnect = consumePendingHostKeyReconnect() ?: return
        refreshFlows()
        startConnect(
            pendingReconnect.first,
            pendingReconnect.second.copy(hostKeyPolicy = policy),
        )
    }

    private fun consumePendingHostKeyReconnect(): Pair<TabId, ConnectRequest>? {
        var pendingReconnect: Pair<TabId, ConnectRequest>? = null
        synchronized(tabRegistry) {
            val tabId = activeTabId.value
            if (tabId != null) {
                val tab = tabRegistry[tabId]
                val request = tab?.pendingHostKeyRequest
                if (tab != null && request != null) {
                    tab.pendingHostKeyRequest = null
                    tab.snapshotFlow.value = tab.snapshotFlow.value.copy(hostKeyAlert = null, error = null)
                    pendingReconnect = tabId to request
                }
            }
        }
        return pendingReconnect
    }

    private suspend fun connectTab(
        tabId: TabId,
        request: ConnectRequest,
    ) {
        val currentJob = currentCoroutineContext()[Job]
        var session: SshSession? = null
        try {
            val connectFailure =
                runCatching {
                    session = sshClient.connect(request)
                    val activeSession = session ?: return@runCatching
                    val tabBridge = attachSessionToTab(tabId, activeSession, request)
                    if (tabBridge == null) {
                        runCatching { activeSession.close() }
                        return@runCatching
                    }
                    establishConnectedSession(tabId, tabBridge, activeSession, request)
                    activeSession.readLoop { bytes ->
                        tabBridge.feed(bytes)
                    }
                    markTabDisconnected(tabId, activeSession)
                }.exceptionOrNull()

            when (connectFailure) {
                null -> Unit
                is CancellationException -> {
                    runCatching { session?.close() }
                    throw connectFailure
                }
                is Exception -> handleConnectError(tabId, request, session, connectFailure)
                else -> throw connectFailure
            }
        } finally {
            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId] ?: return@synchronized
                if (tab.connectJob == currentJob) {
                    tab.connectJob = null
                }
            }
        }
    }

    private fun attachSessionToTab(
        tabId: TabId,
        session: SshSession,
        request: ConnectRequest,
    ): TermuxTerminalBridge? =
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return@synchronized null
            tab.sshSession = session
            tab.lastCols = request.cols
            tab.lastRows = request.rows
            tab.bridge
        }

    private suspend fun establishConnectedSession(
        tabId: TabId,
        tabBridge: TermuxTerminalBridge,
        session: SshSession,
        request: ConnectRequest,
    ) {
        tabBridge.resize(request.cols, request.rows)
        session.openPtyShell(request.termType, request.cols, request.rows)
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return@synchronized
            tab.snapshotFlow.value =
                tab.snapshotFlow.value.copy(
                    state = SessionState.CONNECTED,
                    error = null,
                    hostKeyAlert = null,
                )
        }
        refreshFlows()
    }

    private fun markTabDisconnected(
        tabId: TabId,
        session: SshSession,
    ) {
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return@synchronized
            if (tab.sshSession === session) {
                tab.sshSession = null
            }
            tab.snapshotFlow.value = tab.snapshotFlow.value.copy(state = SessionState.DISCONNECTED)
        }
        refreshFlows()
    }

    private suspend fun handleConnectError(
        tabId: TabId,
        request: ConnectRequest,
        session: SshSession?,
        err: Exception,
    ) {
        runCatching { session?.close() }
        Log.e(TAG, "Connection failed", err)
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return@synchronized
            tab.sshSession = null
            val hostKeyAlert = toHostKeyAlert(err, request)
            if (hostKeyAlert != null) {
                tab.pendingHostKeyRequest = request
                tab.snapshotFlow.value =
                    tab.snapshotFlow.value.copy(
                        state = SessionState.FAILED,
                        error = hostKeyAlert.message,
                        hostKeyAlert = hostKeyAlert,
                    )
            } else {
                tab.snapshotFlow.value =
                    tab.snapshotFlow.value.copy(
                        state = SessionState.FAILED,
                        error = err.message ?: "unknown error",
                        hostKeyAlert = null,
                    )
            }
        }
        refreshFlows()
    }

    private fun toHostKeyAlert(
        err: Exception,
        request: ConnectRequest,
    ): HostKeyAlert? {
        val hostKeyAlert =
            if (err is HostKeyChangedException) {
                HostKeyAlert(
                    host = err.host,
                    port = err.port,
                    fingerprint = err.fingerprint,
                    message = err.message,
                )
            } else {
                val message = err.message
                val isHostKeyNotVerifiable =
                    message != null &&
                        (
                            "HOST_KEY_NOT_VERIFIABLE" in message ||
                                ("Could not verify" in message && "host key" in message)
                        )
                if (isHostKeyNotVerifiable && message != null) {
                    val fingerprint = FINGERPRINT_REGEX.find(message)?.groupValues?.get(1) ?: "unknown"
                    HostKeyAlert(
                        host = request.host,
                        port = request.port,
                        fingerprint = fingerprint,
                        message = message,
                    )
                } else {
                    null
                }
            }
        return hostKeyAlert
    }

    companion object {
        private const val TAG = "SessionConnector"
        private val FINGERPRINT_REGEX = Regex("fingerprint `([^`]+)`")
    }
}
