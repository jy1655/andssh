package com.opencode.sshterminal.session

import com.opencode.sshterminal.ssh.SshSession
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Unique identifier for a tab. */
data class TabId(val value: String = UUID.randomUUID().toString())

/** Lightweight info exposed to UI for rendering the tab bar. */
data class TabInfo(
    val tabId: TabId,
    val title: String,
    val connectionId: String,
    val state: SessionState,
)

/** Internal holder for everything a single tab owns. */
internal class TabSession(
    val tabId: TabId,
    val title: String,
    val connectionId: String,
    val bridge: TermuxTerminalBridge,
    val snapshotFlow: MutableStateFlow<SessionSnapshot>,
) {
    var sshSession: SshSession? = null
    var connectJob: Job? = null
    var pendingHostKeyRequest: ConnectRequest? = null
    var lastCols: Int = 120
    var lastRows: Int = 40

    fun toTabInfo(): TabInfo =
        TabInfo(
            tabId = tabId,
            title = title,
            connectionId = connectionId,
            state = snapshotFlow.value.state,
        )
}
