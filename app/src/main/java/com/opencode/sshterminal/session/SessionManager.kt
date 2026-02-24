package com.opencode.sshterminal.session

import com.opencode.sshterminal.di.ApplicationScope
import com.opencode.sshterminal.service.BellNotifier
import com.opencode.sshterminal.ssh.SshClient
import com.opencode.sshterminal.ssh.SshSession
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager
    @Inject
    constructor(
        private val sshClient: SshClient,
        private val bellNotifier: BellNotifier,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val tabRegistry = mutableMapOf<TabId, TabSession>()
        private val tabOrder = mutableListOf<TabId>()

        private val _tabs = MutableStateFlow<List<TabInfo>>(emptyList())
        val tabs: StateFlow<List<TabInfo>> = _tabs.asStateFlow()

        private val _activeTabId = MutableStateFlow<TabId?>(null)
        val activeTabId: StateFlow<TabId?> = _activeTabId.asStateFlow()

        private val _activeSnapshot = MutableStateFlow<SessionSnapshot?>(null)
        val activeSnapshot: StateFlow<SessionSnapshot?> = _activeSnapshot.asStateFlow()

        private val _compatSnapshot =
            MutableStateFlow(
                SessionSnapshot(
                    sessionId = SessionId(),
                    state = SessionState.IDLE,
                    host = "",
                    port = 22,
                    username = "",
                ),
            )
        val snapshot: StateFlow<SessionSnapshot> = _compatSnapshot.asStateFlow()

        private val _hasAnyConnected = MutableStateFlow(false)
        val hasAnyConnected: StateFlow<Boolean> = _hasAnyConnected.asStateFlow()

        private val fallbackBridge =
            TermuxTerminalBridge(
                cols = 120,
                rows = 40,
                onWriteToSsh = { bytes -> sendInput(bytes) },
            )

        private val connector =
            SessionConnector(
                scope = scope,
                sshClient = sshClient,
                tabRegistry = tabRegistry,
                activeTabId = _activeTabId,
                refreshFlows = { refreshFlows() },
            )

        val bridge: TermuxTerminalBridge
            get() =
                synchronized(tabRegistry) {
                    _activeTabId.value?.let { tabId -> tabRegistry[tabId]?.bridge }
                } ?: fallbackBridge

        fun bridgeForTab(tabId: TabId): TermuxTerminalBridge? {
            return synchronized(tabRegistry) { tabRegistry[tabId]?.bridge }
        }

        fun openTab(
            title: String,
            connectionId: String,
            request: ConnectRequest,
        ): TabId {
            val tabId = TabId()
            val bellTitle = title
            val bridge =
                TermuxTerminalBridge(
                    cols = 120,
                    rows = 40,
                    onWriteToSsh = { bytes -> connector.sendInputToTab(tabId, bytes) },
                    onBellReceived = { scope.launch { bellNotifier.notifyBell(tabId, bellTitle) } },
                )
            val snapshotFlow =
                MutableStateFlow(
                    SessionSnapshot(
                        sessionId = SessionId(),
                        state = SessionState.IDLE,
                        host = request.host,
                        port = request.port,
                        username = request.username,
                    ),
                )
            val tabSession =
                TabSession(
                    tabId = tabId,
                    title = title,
                    connectionId = connectionId,
                    bridge = bridge,
                    snapshotFlow = snapshotFlow,
                )

            synchronized(tabRegistry) {
                tabRegistry[tabId] = tabSession
                tabOrder += tabId
                _activeTabId.value = tabId
            }
            refreshFlows()
            connector.startConnect(tabId, request)
            return tabId
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

        fun disconnect() {
            var sshSession: SshSession?
            var connectJob: Job?
            synchronized(tabRegistry) {
                val tabId = _activeTabId.value ?: return
                val tab = tabRegistry[tabId] ?: return
                sshSession = tab.sshSession
                connectJob = tab.connectJob
                tab.sshSession = null
                tab.connectJob = null
                tab.pendingHostKeyRequest = null
                tab.snapshotFlow.value =
                    tab.snapshotFlow.value.copy(
                        state = SessionState.DISCONNECTED,
                        error = null,
                        hostKeyAlert = null,
                    )
            }
            connectJob?.cancel()
            scope.launch {
                runCatching { sshSession?.close() }
            }
            refreshFlows()
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
            connector.sendInputToTab(tabId, bytes)
        }

        fun sendInputToTab(
            tabId: TabId,
            bytes: ByteArray,
        ) {
            connector.sendInputToTab(tabId, bytes)
        }

        fun resize(
            cols: Int,
            rows: Int,
        ) {
            val tabId = _activeTabId.value ?: return
            resizeTab(tabId, cols, rows)
        }

        fun resizeTab(
            tabId: TabId,
            cols: Int,
            rows: Int,
        ) {
            val tab =
                synchronized(tabRegistry) {
                    val target = tabRegistry[tabId] ?: return
                    target.lastCols = cols
                    target.lastRows = rows
                    target
                }
            tab.bridge.resize(cols, rows)
            scope.launch {
                tab.sshSession?.windowChange(cols, rows)
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

        fun trustHostKeyOnce() = connector.reconnectPendingHostKey(HostKeyPolicy.TRUST_ONCE)

        fun updateKnownHostsAndReconnect() = connector.reconnectPendingHostKey(HostKeyPolicy.UPDATE_KNOWN_HOSTS)

        fun reconnectTabsOnNetworkAvailable() {
            val now = System.currentTimeMillis()
            val reconnectTargets = mutableListOf<Pair<TabId, ConnectRequest>>()
            synchronized(tabRegistry) {
                tabOrder.forEach { tabId ->
                    val tab = tabRegistry[tabId] ?: return@forEach
                    val request = tab.lastConnectRequest ?: return@forEach
                    val state = tab.snapshotFlow.value.state
                    if (state != SessionState.DISCONNECTED && state != SessionState.FAILED) return@forEach
                    if (tab.sshSession != null || tab.connectJob != null) return@forEach
                    val cooldownElapsed = now - tab.lastAutoReconnectAtMillis >= AUTO_RECONNECT_DEBOUNCE_MS
                    if (!cooldownElapsed) return@forEach
                    tab.lastAutoReconnectAtMillis = now
                    tab.pendingHostKeyRequest = null
                    tab.snapshotFlow.value =
                        tab.snapshotFlow.value.copy(
                            state = SessionState.RECONNECTING,
                            error = null,
                            hostKeyAlert = null,
                        )
                    reconnectTargets += tabId to request
                }
            }
            if (reconnectTargets.isEmpty()) return
            refreshFlows()
            reconnectTargets.forEach { (tabId, request) ->
                connector.startConnect(tabId, request)
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
            _activeSnapshot.value =
                activeId?.let { tabId ->
                    orderedTabs.firstOrNull { tab -> tab.tabId == tabId }?.snapshotFlow?.value
                }
            _compatSnapshot.value = _activeSnapshot.value ?: SessionSnapshot(
                sessionId = SessionId(),
                state = SessionState.IDLE,
                host = "",
                port = 22,
                username = "",
            )
            _hasAnyConnected.value =
                orderedTabs.any { tab ->
                    tab.snapshotFlow.value.state == SessionState.CONNECTED
                }
        }

        companion object {
            private const val AUTO_RECONNECT_DEBOUNCE_MS = 2_000L
        }
    }
