package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.TerminalSnippet
import com.opencode.sshterminal.session.HostKeyAlert
import com.opencode.sshterminal.session.SessionSnapshot
import com.opencode.sshterminal.session.TabId
import com.opencode.sshterminal.session.TabInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateToSftp: (connectionId: String) -> Unit,
    onAllTabsClosed: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val activeSnapshot by viewModel.activeSnapshot.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val snippets by viewModel.snippets.collectAsState()
    val terminalColorSchemeId by viewModel.terminalColorSchemeId.collectAsState()
    val terminalFontId by viewModel.terminalFont.collectAsState()
    val terminalHapticFeedbackEnabled by viewModel.terminalHapticFeedbackEnabled.collectAsState()
    val terminalCursorStyle by viewModel.terminalCursorStyle.collectAsState()
    var hadTabs by remember { mutableStateOf(false) }
    var showConnectionPicker by remember { mutableStateOf(false) }
    var showSnippetSheet by remember { mutableStateOf(false) }
    var pageUpCount by remember { mutableStateOf(0) }
    var pageDownCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val clipboardLabel = stringResource(R.string.terminal_clipboard_label)

    LaunchedEffect(tabs) {
        if (tabs.isNotEmpty()) hadTabs = true
        if (hadTabs && tabs.isEmpty()) onAllTabsClosed()
    }

    val activeTab = tabs.find { it.tabId == activeTabId }
    val activeProfile =
        activeTab
            ?.connectionId
            ?.let { connectionId -> profiles.firstOrNull { profile -> profile.id == connectionId } }
    val connectionInfo =
        buildTerminalConnectionInfo(activeSnapshot, activeProfile)
            ?.toDisplayText(
                proxyJumpFormatter = { count ->
                    context.getString(R.string.terminal_connection_proxy_jump, count)
                },
                forwardFormatter = { count ->
                    context.getString(R.string.terminal_connection_forwards, count)
                },
                moreForwardFormatter = { count ->
                    context.getString(R.string.terminal_connection_forwards_more, count)
                },
            ).orEmpty()
    val activeColorSchemeId = activeProfile?.terminalColorSchemeId ?: terminalColorSchemeId
    val screenModel =
        TerminalScreenModel(
            tabs = tabs,
            activeTabId = activeTabId,
            activeSnapshot = activeSnapshot,
            activeConnectionId = activeTab?.connectionId,
            connectionInfo = connectionInfo,
            terminalColorSchemeId = activeColorSchemeId,
            terminalFontId = terminalFontId,
            terminalHapticFeedbackEnabled = terminalHapticFeedbackEnabled,
            terminalCursorStyle = terminalCursorStyle,
        )
    val screenCallbacks =
        TerminalScreenCallbacks(
            onNavigateToSftp = onNavigateToSftp,
            onShowConnectionPicker = { showConnectionPicker = true },
            onShowSnippets = { showSnippetSheet = true },
            onPageScroll = { direction ->
                val handledRemotely = viewModel.handlePageScroll(direction)
                if (!handledRemotely) {
                    if (direction > 0) pageUpCount++ else pageDownCount++
                }
            },
        )
    val dialogState =
        TerminalDialogsState(
            showConnectionPicker = showConnectionPicker,
            showSnippetSheet = showSnippetSheet,
            profiles = profiles,
            snippets = snippets,
            hostKeyAlert = activeSnapshot?.hostKeyAlert,
        )
    val dialogCallbacks =
        TerminalDialogsCallbacks(
            onDismissConnectionPicker = { showConnectionPicker = false },
            onSelectProfile = { profile ->
                showConnectionPicker = false
                viewModel.openTab(profile.id)
            },
            onRejectHostKey = viewModel::dismissHostKeyAlert,
            onTrustHostKeyOnce = viewModel::trustHostKeyOnce,
            onUpdateKnownHosts = viewModel::updateKnownHostsAndReconnect,
            onDismissSnippetSheet = { showSnippetSheet = false },
            onSaveSnippet = viewModel::saveSnippet,
            onDeleteSnippet = { snippetId -> viewModel.deleteSnippet(snippetId) },
            onRunSnippet = { command ->
                showSnippetSheet = false
                viewModel.runSnippet(command)
            },
        )

    TerminalScaffold(
        viewModel = viewModel,
        model = screenModel,
        callbacks = screenCallbacks,
        scrollCounters = TerminalScrollCounters(pageUpCount = pageUpCount, pageDownCount = pageDownCount),
        clipboardLabel = clipboardLabel,
    )

    TerminalScreenDialogs(state = dialogState, callbacks = dialogCallbacks)
}

private data class TerminalScreenModel(
    val tabs: List<TabInfo>,
    val activeTabId: TabId?,
    val activeSnapshot: SessionSnapshot?,
    val activeConnectionId: String?,
    val connectionInfo: String,
    val terminalColorSchemeId: String,
    val terminalFontId: String,
    val terminalHapticFeedbackEnabled: Boolean,
    val terminalCursorStyle: Int,
)

private data class TerminalScreenCallbacks(
    val onNavigateToSftp: (connectionId: String) -> Unit,
    val onShowConnectionPicker: () -> Unit,
    val onShowSnippets: () -> Unit,
    val onPageScroll: (Int) -> Unit,
)

private data class TerminalMainCallbacks(
    val onOpenDrawer: () -> Unit,
    val onShowConnectionPicker: () -> Unit,
    val onShowSnippets: () -> Unit,
    val onPageScroll: (Int) -> Unit,
    val onCopyText: (String) -> Unit,
)

private data class TerminalDialogsState(
    val showConnectionPicker: Boolean,
    val showSnippetSheet: Boolean,
    val profiles: List<ConnectionProfile>,
    val snippets: List<TerminalSnippet>,
    val hostKeyAlert: HostKeyAlert?,
)

private data class TerminalDialogsCallbacks(
    val onDismissConnectionPicker: () -> Unit,
    val onSelectProfile: (ConnectionProfile) -> Unit,
    val onRejectHostKey: () -> Unit,
    val onTrustHostKeyOnce: () -> Unit,
    val onUpdateKnownHosts: () -> Unit,
    val onDismissSnippetSheet: () -> Unit,
    val onSaveSnippet: (existingSnippetId: String?, title: String, command: String) -> Unit,
    val onDeleteSnippet: (snippetId: String) -> Unit,
    val onRunSnippet: (command: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalScaffold(
    viewModel: TerminalViewModel,
    model: TerminalScreenModel,
    callbacks: TerminalScreenCallbacks,
    scrollCounters: TerminalScrollCounters,
    clipboardLabel: String,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                drawerState = drawerState,
                connectionInfo = model.connectionInfo,
                onTerminal = { },
                onSftp = { model.activeConnectionId?.let(callbacks.onNavigateToSftp) },
                onDisconnect = { viewModel.disconnectActiveTab() },
            )
        },
    ) {
        TerminalMainColumn(
            viewModel = viewModel,
            model = model,
            scrollCounters = scrollCounters,
            callbacks =
                TerminalMainCallbacks(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onShowConnectionPicker = callbacks.onShowConnectionPicker,
                    onShowSnippets = callbacks.onShowSnippets,
                    onPageScroll = callbacks.onPageScroll,
                    onCopyText = { text -> viewModel.copyToClipboard(clipboardLabel, text) },
                ),
        )
    }
}

@Composable
private fun TerminalMainColumn(
    viewModel: TerminalViewModel,
    model: TerminalScreenModel,
    scrollCounters: TerminalScrollCounters,
    callbacks: TerminalMainCallbacks,
) {
    var imeFocusSignal by remember { mutableStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
    ) {
        TerminalTabBar(
            model =
                TerminalTabBarModel(
                    tabs = model.tabs,
                    activeTabId = model.activeTabId,
                ),
            callbacks =
                TerminalTabBarCallbacks(
                    onSwitchTab = viewModel::switchTab,
                    onShowNewTab = callbacks.onShowConnectionPicker,
                    onCloseActiveTab = { model.activeTabId?.let(viewModel::closeTab) },
                ),
        )

        TerminalRenderer(
            bridge = viewModel.bridge,
            terminalColorSchemeId = model.terminalColorSchemeId,
            terminalFontId = model.terminalFontId,
            terminalCursorStyle = model.terminalCursorStyle,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            scrollCounters = scrollCounters,
            callbacks =
                TerminalRendererCallbacks(
                    onTap = { imeFocusSignal++ },
                    onResize = { cols, rows -> viewModel.resize(cols, rows) },
                    onCopyText = callbacks.onCopyText,
                ),
        )

        model.activeSnapshot?.error?.let { error ->
            Text(
                text = stringResource(R.string.terminal_error_format, error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        TerminalInputBar(
            onSendBytes = viewModel::sendInput,
            onMenuClick = callbacks.onOpenDrawer,
            onSnippetClick = callbacks.onShowSnippets,
            onPageScroll = callbacks.onPageScroll,
            isHapticFeedbackEnabled = model.terminalHapticFeedbackEnabled,
            focusSignal = imeFocusSignal,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TerminalScreenDialogs(
    state: TerminalDialogsState,
    callbacks: TerminalDialogsCallbacks,
) {
    if (state.showConnectionPicker) {
        ConnectionPickerSheet(
            profiles = state.profiles,
            onDismiss = callbacks.onDismissConnectionPicker,
            onSelectProfile = callbacks.onSelectProfile,
        )
    }

    if (state.showSnippetSheet) {
        TerminalSnippetSheet(
            snippets = state.snippets,
            onDismiss = callbacks.onDismissSnippetSheet,
            onSaveSnippet = callbacks.onSaveSnippet,
            onRunSnippet = { snippet -> callbacks.onRunSnippet(snippet.command) },
            onDeleteSnippet = { snippet -> callbacks.onDeleteSnippet(snippet.id) },
        )
    }

    state.hostKeyAlert?.let { hostKeyAlert ->
        HostKeyChangedDialog(
            hostKeyAlert = hostKeyAlert,
            onReject = callbacks.onRejectHostKey,
            onTrustOnce = callbacks.onTrustHostKeyOnce,
            onUpdateKnownHosts = callbacks.onUpdateKnownHosts,
        )
    }
}

@Composable
private fun TerminalTabBar(
    model: TerminalTabBarModel,
    callbacks: TerminalTabBarCallbacks,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TerminalTabStrip(
            tabs = model.tabs,
            activeTabId = model.activeTabId,
            onSwitchTab = callbacks.onSwitchTab,
        )
        TerminalTabButtons(
            activeTabId = model.activeTabId,
            onShowNewTab = callbacks.onShowNewTab,
            onCloseActiveTab = callbacks.onCloseActiveTab,
        )
    }
}

@Composable
private fun RowScope.TerminalTabStrip(
    tabs: List<TabInfo>,
    activeTabId: TabId?,
    onSwitchTab: (TabId) -> Unit,
) {
    val selectedIndex = tabs.indexOfFirst { it.tabId == activeTabId }.coerceAtLeast(0)

    if (tabs.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.weight(1f),
            edgePadding = 4.dp,
            divider = { },
        ) {
            tabs.forEach { tabInfo ->
                Tab(
                    selected = tabInfo.tabId == activeTabId,
                    onClick = { onSwitchTab(tabInfo.tabId) },
                ) {
                    TerminalTabLabel(tabInfo = tabInfo)
                }
            }
        }
    } else {
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TerminalTabButtons(
    activeTabId: TabId?,
    onShowNewTab: () -> Unit,
    onCloseActiveTab: () -> Unit,
) {
    IconButton(onClick = onShowNewTab) {
        Icon(
            Icons.Default.Add,
            contentDescription = stringResource(R.string.terminal_new_tab),
            tint = MaterialTheme.colorScheme.primary,
        )
    }

    if (activeTabId != null) {
        IconButton(onClick = onCloseActiveTab) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.terminal_close_active_tab),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class TerminalTabBarModel(
    val tabs: List<TabInfo>,
    val activeTabId: TabId?,
)

private data class TerminalTabBarCallbacks(
    val onSwitchTab: (TabId) -> Unit,
    val onShowNewTab: () -> Unit,
    val onCloseActiveTab: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionPickerSheet(
    profiles: List<ConnectionProfile>,
    onDismiss: () -> Unit,
    onSelectProfile: (ConnectionProfile) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(R.string.terminal_select_connection),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()

        if (profiles.isEmpty()) {
            Text(
                text = stringResource(R.string.connection_no_saved_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn {
                items(profiles, key = { it.id }) { profile ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${profile.username}@${profile.host}:${profile.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HostKeyChangedDialog(
    hostKeyAlert: HostKeyAlert,
    onReject: () -> Unit,
    onTrustOnce: () -> Unit,
    onUpdateKnownHosts: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text(stringResource(R.string.terminal_host_key_changed)) },
        text = {
            Text(
                stringResource(
                    R.string.terminal_host_key_message,
                    hostKeyAlert.host,
                    hostKeyAlert.port,
                    hostKeyAlert.fingerprint,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.terminal_reject))
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onTrustOnce) {
                    Text(stringResource(R.string.terminal_trust_once))
                }
                TextButton(onClick = onUpdateKnownHosts) {
                    Text(stringResource(R.string.terminal_update_known_hosts))
                }
            }
        },
    )
}
