package com.opencode.sshterminal.ui.terminal

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.ConnectionRepository
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.data.TerminalSnippet
import com.opencode.sshterminal.data.TerminalSnippetRepository
import com.opencode.sshterminal.security.SensitiveClipboardManager
import com.opencode.sshterminal.service.SshForegroundService
import com.opencode.sshterminal.session.JumpCredential
import com.opencode.sshterminal.session.SessionManager
import com.opencode.sshterminal.session.SessionSnapshot
import com.opencode.sshterminal.session.SessionState
import com.opencode.sshterminal.session.TabId
import com.opencode.sshterminal.session.TabInfo
import com.opencode.sshterminal.session.toConnectRequest
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class TerminalViewModel
    @Inject
    constructor(
        private val sessionManager: SessionManager,
        private val connectionRepository: ConnectionRepository,
        private val settingsRepository: SettingsRepository,
        private val terminalSnippetRepository: TerminalSnippetRepository,
        private val sensitiveClipboardManager: SensitiveClipboardManager,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val tabs: StateFlow<List<TabInfo>> =
            sessionManager.tabs
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    sessionManager.tabs.value,
                )

        val activeTabId: StateFlow<TabId?> =
            sessionManager.activeTabId
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    sessionManager.activeTabId.value,
                )

        val activeSnapshot: StateFlow<SessionSnapshot?> =
            sessionManager.activeSnapshot
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    sessionManager.activeSnapshot.value,
                )

        val profiles: StateFlow<List<ConnectionProfile>> =
            connectionRepository.profiles
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    emptyList(),
                )

        val snippets: StateFlow<List<TerminalSnippet>> =
            terminalSnippetRepository.snippets
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    emptyList(),
                )

        val bridge: TermuxTerminalBridge get() = sessionManager.bridge

        val terminalColorSchemeId: StateFlow<String> =
            settingsRepository.terminalColorScheme
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    SettingsRepository.DEFAULT_TERMINAL_COLOR_SCHEME,
                )

        val terminalFont: StateFlow<String> =
            settingsRepository.terminalFont
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    SettingsRepository.DEFAULT_TERMINAL_FONT,
                )

        val sshKeepaliveIntervalSeconds: StateFlow<Int> =
            settingsRepository.sshKeepaliveIntervalSeconds
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    SettingsRepository.DEFAULT_SSH_KEEPALIVE_INTERVAL,
                )

        val terminalHapticFeedbackEnabled: StateFlow<Boolean> =
            settingsRepository.terminalHapticFeedbackEnabled
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                    SettingsRepository.DEFAULT_TERMINAL_HAPTIC_FEEDBACK_ENABLED,
                )

        init {
            viewModelScope.launch {
                sessionManager.hasAnyConnected.collect { anyConnected ->
                    if (anyConnected) {
                        context.startForegroundService(
                            Intent(context, SshForegroundService::class.java),
                        )
                    } else {
                        context.stopService(Intent(context, SshForegroundService::class.java))
                    }
                }
            }
        }

        fun openTab(connectionId: String) {
            viewModelScope.launch {
                val profile = connectionRepository.get(connectionId) ?: return@launch
                val identity = profile.identityId?.let { identityId -> connectionRepository.getIdentity(identityId) }
                val proxyJumpCredentials = resolveProxyJumpCredentials(profile)
                connectionRepository.touchLastUsed(profile.id)
                val tabId =
                    sessionManager.openTab(
                        title = profile.name,
                        connectionId = profile.id,
                        request =
                            profile.toConnectRequest(
                                context = context,
                                cols = DEFAULT_TERMINAL_COLS,
                                rows = DEFAULT_TERMINAL_ROWS,
                                keepaliveIntervalSeconds = sshKeepaliveIntervalSeconds.value,
                                identity = identity,
                                proxyJumpCredentials = proxyJumpCredentials,
                            ),
                    )
                profile.startupCommand?.let { startupCommand ->
                    scheduleStartupCommand(tabId = tabId, startupCommand = startupCommand)
                }
            }
        }

        private suspend fun resolveProxyJumpCredentials(profile: ConnectionProfile): Map<String, JumpCredential> {
            return profile.proxyJumpIdentityIds.mapNotNull { (hostPortKey, identityId) ->
                val identity = connectionRepository.getIdentity(identityId) ?: return@mapNotNull null
                hostPortKey to
                    JumpCredential(
                        username = identity.username,
                        password = identity.password,
                        privateKeyPath = identity.privateKeyPath,
                        privateKeyPassphrase = identity.privateKeyPassphrase,
                    )
            }.toMap()
        }

        private fun scheduleStartupCommand(
            tabId: TabId,
            startupCommand: String,
        ) {
            val normalized = startupCommand.trimEnd('\r', '\n')
            if (normalized.isBlank()) return
            viewModelScope.launch {
                val state =
                    sessionManager.tabs
                        .map { tabs -> tabs.firstOrNull { info -> info.tabId == tabId }?.state }
                        .first { tabState ->
                            tabState == null ||
                                tabState == SessionState.CONNECTED ||
                                tabState == SessionState.FAILED ||
                                tabState == SessionState.DISCONNECTED
                        }
                if (state == SessionState.CONNECTED) {
                    sessionManager.sendInputToTab(tabId, "$normalized\r".toByteArray(Charsets.UTF_8))
                }
            }
        }

        fun switchTab(tabId: TabId) = sessionManager.switchTab(tabId)

        fun closeTab(tabId: TabId) = sessionManager.closeTab(tabId)

        fun disconnectActiveTab() = sessionManager.disconnect()

        fun sendInput(bytes: ByteArray) = sessionManager.sendInput(bytes)

        fun copyToClipboard(
            label: String,
            text: String,
        ) = sensitiveClipboardManager.copyToClipboard(label, text)

        fun saveSnippet(
            existingSnippetId: String?,
            title: String,
            command: String,
        ) {
            val normalizedCommand = command.trimEnd()
            if (normalizedCommand.isBlank()) return
            val normalizedTitle = title.trim().ifEmpty { normalizedCommand.lineSequence().first().trim() }.take(MAX_SNIPPET_TITLE_LENGTH)
            val snippet =
                TerminalSnippet(
                    id = existingSnippetId ?: UUID.randomUUID().toString(),
                    title = normalizedTitle,
                    command = normalizedCommand,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )
            viewModelScope.launch {
                terminalSnippetRepository.save(snippet)
            }
        }

        fun deleteSnippet(snippetId: String) {
            viewModelScope.launch {
                terminalSnippetRepository.delete(snippetId)
            }
        }

        fun runSnippet(command: String) {
            val normalized = command.trimEnd('\r', '\n')
            if (normalized.isBlank()) return
            sendInput("$normalized\r".toByteArray(Charsets.UTF_8))
        }

        fun resize(
            cols: Int,
            rows: Int,
        ) = sessionManager.resize(cols, rows)

        /**
         * Returns true when the scroll request was forwarded to the remote TUI.
         * Returns false when the UI should handle local scrollback instead.
         */
        fun handlePageScroll(direction: Int): Boolean {
            if (direction == 0) return false
            val activeBridge = bridge
            if (activeBridge.isMouseTrackingActive()) {
                activeBridge.sendMouseWheel(
                    scrollUp = direction > 0,
                    repeatCount = MOUSE_WHEEL_STEPS_PER_PAGE_BUTTON,
                )
                return true
            }
            if (activeBridge.isAlternateBufferActive()) {
                val keyCode = if (direction > 0) KeyEvent.KEYCODE_PAGE_UP else KeyEvent.KEYCODE_PAGE_DOWN
                return activeBridge.sendKeyCode(keyCode)
            }
            return false
        }

        fun dismissHostKeyAlert() = sessionManager.dismissHostKeyAlert()

        fun trustHostKeyOnce() = sessionManager.trustHostKeyOnce()

        fun updateKnownHostsAndReconnect() = sessionManager.updateKnownHostsAndReconnect()

        companion object {
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
            private const val DEFAULT_TERMINAL_COLS = 120
            private const val DEFAULT_TERMINAL_ROWS = 40
            private const val MOUSE_WHEEL_STEPS_PER_PAGE_BUTTON = 3
            private const val MAX_SNIPPET_TITLE_LENGTH = 48
        }
    }
