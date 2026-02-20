package com.opencode.sshterminal.session

import android.util.Log
import com.opencode.sshterminal.di.ApplicationScope
import com.opencode.sshterminal.service.BellNotifier
import com.opencode.sshterminal.ssh.HostKeyChangedException
import com.opencode.sshterminal.ssh.SshClient
import com.opencode.sshterminal.ssh.SshSession
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sshClient: SshClient,
    private val bellNotifier: BellNotifier,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val tabRegistry = mutableMapOf<TabId, TabSession>()
    private val tabOrder = mutableListOf<TabId>()

    private val _tabs = MutableStateFlow<List<TabInfo>>(emptyList())
    val tabs: StateFlow<List<TabInfo>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<TabId?>(null)
    val activeTabId: StateFlow<TabId?> = _activeTabId.asStateFlow()

    private val _activeSnapshot = MutableStateFlow<SessionSnapshot?>(null)
    val activeSnapshot: StateFlow<SessionSnapshot?> = _activeSnapshot.asStateFlow()

    private val _compatSnapshot = MutableStateFlow(
        SessionSnapshot(
            sessionId = SessionId(),
            state = SessionState.IDLE,
            host = "",
            port = 22,
            username = ""
        )
    )
    val snapshot: StateFlow<SessionSnapshot> = _compatSnapshot.asStateFlow()

    private val _hasAnyConnected = MutableStateFlow(false)
    val hasAnyConnected: StateFlow<Boolean> = _hasAnyConnected.asStateFlow()

    private val fallbackBridge = TermuxTerminalBridge(
        cols = 120,
        rows = 40,
        onWriteToSsh = { bytes -> sendInput(bytes) }
    )

    val activeBridge: TermuxTerminalBridge?
        get() = synchronized(tabRegistry) {
            _activeTabId.value?.let { tabId -> tabRegistry[tabId]?.bridge }
        }

    val bridge: TermuxTerminalBridge
        get() = activeBridge ?: fallbackBridge

    fun openTab(title: String, connectionId: String, request: ConnectRequest): TabId {
        val tabId = TabId()
        val bellTitle = title
        val bridge = TermuxTerminalBridge(
            cols = 120,
            rows = 40,
            onWriteToSsh = { bytes -> sendInputToTab(tabId, bytes) },
            onBellReceived = { scope.launch { bellNotifier.notifyBell(tabId, bellTitle) } }
        )
        val snapshotFlow = MutableStateFlow(
            SessionSnapshot(
                sessionId = SessionId(),
                state = SessionState.IDLE,
                host = request.host,
                port = request.port,
                username = request.username
            )
        )
        val tabSession = TabSession(
            tabId = tabId,
            title = title,
            connectionId = connectionId,
            bridge = bridge,
            snapshotFlow = snapshotFlow
        )

        synchronized(tabRegistry) {
            tabRegistry[tabId] = tabSession
            tabOrder += tabId
            _activeTabId.value = tabId
        }
        refreshFlows()
        startConnect(tabId, request)
        return tabId
    }

    fun connect(request: ConnectRequest) {
        openTab(
            title = "${request.username}@${request.host}",
            connectionId = "${request.username}@${request.host}:${request.port}",
            request = request
        )
    }

    fun closeTab(tabId: TabId) {
        var removedTab: TabSession?
        synchronized(tabRegistry) {
            val index = tabOrder.indexOf(tabId)
            if (index < 0) return

            val wasActive = _activeTabId.value == tabId
            val left = if (index - 1 >= 0) tabOrder[index - 1] else null
            val right = if (index + 1 < tabOrder.size) tabOrder[index + 1] else null

            tabOrder.removeAt(index)
            removedTab = tabRegistry.remove(tabId)
            if (wasActive) {
                _activeTabId.value = left ?: right
            }
        }
        val tab = removedTab ?: return

        tab.connectJob?.cancel()
        bellNotifier.clearTab(tabId)
        scope.launch {
            runCatching { tab.sshSession?.close() }
        }
        refreshFlows()
    }

    fun disconnectTab(tabId: TabId) {
        var sshSession: SshSession?
        var connectJob: Job?
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return
            sshSession = tab.sshSession
            connectJob = tab.connectJob
            tab.sshSession = null
            tab.connectJob = null
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value = tab.snapshotFlow.value.copy(
                state = SessionState.DISCONNECTED,
                error = null,
                hostKeyAlert = null
            )
        }
        connectJob?.cancel()
        scope.launch {
            runCatching { sshSession?.close() }
        }
        refreshFlows()
    }

    fun disconnect() {
        val tabId = _activeTabId.value ?: return
        disconnectTab(tabId)
    }

    fun switchTab(tabId: TabId) {
        synchronized(tabRegistry) {
            if (!tabRegistry.containsKey(tabId)) return
            _activeTabId.value = tabId
        }
        refreshFlows()
    }

    fun sendInput(bytes: ByteArray) {
        val tabId = _activeTabId.value ?: return
        sendInputToTab(tabId, bytes)
    }

    fun resize(cols: Int, rows: Int) {
        val activeTab: TabSession = synchronized(tabRegistry) {
            val tabId = _activeTabId.value ?: return
            val tab = tabRegistry[tabId] ?: return
            tab.lastCols = cols
            tab.lastRows = rows
            tab
        }
        activeTab.bridge.resize(cols, rows)
        scope.launch {
            activeTab.sshSession?.windowChange(cols, rows)
        }
    }

    fun forceRepaint() {
        val activeTab: TabSession = synchronized(tabRegistry) {
            val tabId = _activeTabId.value ?: return
            tabRegistry[tabId] ?: return
        }
        scope.launch {
            activeTab.sshSession?.windowChange(activeTab.lastCols, activeTab.lastRows)
        }
    }

    fun dismissHostKeyAlert() {
        synchronized(tabRegistry) {
            val tabId = _activeTabId.value ?: return
            val tab = tabRegistry[tabId] ?: return
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value = tab.snapshotFlow.value.copy(hostKeyAlert = null)
        }
        refreshFlows()
    }

    fun trustHostKeyOnce() {
        val tabId: TabId
        val request: ConnectRequest
        synchronized(tabRegistry) {
            tabId = _activeTabId.value ?: return
            val tab = tabRegistry[tabId] ?: return
            request = tab.pendingHostKeyRequest ?: return
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value = tab.snapshotFlow.value.copy(hostKeyAlert = null, error = null)
        }
        refreshFlows()
        startConnect(tabId, request.copy(hostKeyPolicy = HostKeyPolicy.TRUST_ONCE))
    }

    fun updateKnownHostsAndReconnect() {
        val tabId: TabId
        val request: ConnectRequest
        synchronized(tabRegistry) {
            tabId = _activeTabId.value ?: return
            val tab = tabRegistry[tabId] ?: return
            request = tab.pendingHostKeyRequest ?: return
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value = tab.snapshotFlow.value.copy(hostKeyAlert = null, error = null)
        }
        refreshFlows()
        startConnect(tabId, request.copy(hostKeyPolicy = HostKeyPolicy.UPDATE_KNOWN_HOSTS))
    }

    val isConnected: Boolean
        get() = _activeSnapshot.value?.state == SessionState.CONNECTED

    private fun startConnect(tabId: TabId, request: ConnectRequest) {
        var previousJob: Job?
        synchronized(tabRegistry) {
            val tab = tabRegistry[tabId] ?: return
            previousJob = tab.connectJob
            tab.pendingHostKeyRequest = null
            tab.snapshotFlow.value = SessionSnapshot(
                sessionId = SessionId(),
                state = SessionState.CONNECTING,
                host = request.host,
                port = request.port,
                username = request.username
            )
        }
        previousJob?.cancel()
        refreshFlows()

        val job = scope.launch {
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

    private suspend fun connectTab(tabId: TabId, request: ConnectRequest) {
        val currentJob = currentCoroutineContext()[Job]
        try {
            val session = sshClient.connect(request)
            var bridge: TermuxTerminalBridge?
            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId]
                if (tab == null) {
                    bridge = null
                } else {
                    tab.sshSession = session
                    tab.lastCols = request.cols
                    tab.lastRows = request.rows
                    bridge = tab.bridge
                }
            }
            val tabBridge = bridge
            if (tabBridge == null) {
                runCatching { session.close() }
                return
            }

            tabBridge.resize(request.cols, request.rows)
            session.openPtyShell(request.termType, request.cols, request.rows)
            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId] ?: return
                tab.snapshotFlow.value = tab.snapshotFlow.value.copy(
                    state = SessionState.CONNECTED,
                    error = null,
                    hostKeyAlert = null
                )
            }
            refreshFlows()

            session.readLoop { bytes ->
                tabBridge.feed(bytes)
            }

            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId] ?: return
                if (tab.sshSession === session) {
                    tab.sshSession = null
                }
                tab.snapshotFlow.value = tab.snapshotFlow.value.copy(state = SessionState.DISCONNECTED)
            }
            refreshFlows()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (err: Throwable) {
            Log.e(TAG, "Connection failed", err)
            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId] ?: return@synchronized
                tab.sshSession = null
                val hostKeyAlert = toHostKeyAlert(err, request)
                if (hostKeyAlert != null) {
                    tab.pendingHostKeyRequest = request
                    tab.snapshotFlow.value = tab.snapshotFlow.value.copy(
                        state = SessionState.FAILED,
                        error = hostKeyAlert.message,
                        hostKeyAlert = hostKeyAlert
                    )
                } else {
                    tab.snapshotFlow.value = tab.snapshotFlow.value.copy(
                        state = SessionState.FAILED,
                        error = err.message ?: "unknown error",
                        hostKeyAlert = null
                    )
                }
            }
            refreshFlows()
        } finally {
            synchronized(tabRegistry) {
                val tab = tabRegistry[tabId] ?: return@synchronized
                if (tab.connectJob == currentJob) {
                    tab.connectJob = null
                }
            }
        }
    }

    private fun refreshFlows() {
        val activeId: TabId?
        val orderedTabs: List<TabSession>
        synchronized(tabRegistry) {
            activeId = _activeTabId.value
            orderedTabs = tabOrder.mapNotNull { tabId -> tabRegistry[tabId] }
        }
        _tabs.value = orderedTabs.map { tab -> tab.toTabInfo() }
        _activeSnapshot.value = activeId?.let { tabId ->
            orderedTabs.firstOrNull { tab -> tab.tabId == tabId }?.snapshotFlow?.value
        }
        _compatSnapshot.value = _activeSnapshot.value ?: SessionSnapshot(
            sessionId = SessionId(),
            state = SessionState.IDLE,
            host = "",
            port = 22,
            username = ""
        )
        _hasAnyConnected.value = orderedTabs.any { tab ->
            tab.snapshotFlow.value.state == SessionState.CONNECTED
        }
    }

    private fun sendInputToTab(tabId: TabId, bytes: ByteArray) {
        scope.launch {
            val sshSession = synchronized(tabRegistry) {
                tabRegistry[tabId]?.sshSession
            }
            sshSession?.write(bytes)
        }
    }

    private fun toHostKeyAlert(err: Throwable, request: ConnectRequest): HostKeyAlert? {
        if (err is HostKeyChangedException) {
            return HostKeyAlert(
                host = err.host,
                port = err.port,
                fingerprint = err.fingerprint,
                message = err.message
            )
        }

        val message = err.message ?: return null
        val isHostKeyNotVerifiable =
            "HOST_KEY_NOT_VERIFIABLE" in message ||
                ("Could not verify" in message && "host key" in message)
        if (!isHostKeyNotVerifiable) return null

        val fingerprint = FINGERPRINT_REGEX.find(message)?.groupValues?.get(1) ?: "unknown"
        return HostKeyAlert(
            host = request.host,
            port = request.port,
            fingerprint = fingerprint,
            message = message
        )
    }

    companion object {
        private const val TAG = "SessionManager"
        private val FINGERPRINT_REGEX = Regex("fingerprint `([^`]+)`")
    }
}
