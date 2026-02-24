package com.opencode.sshterminal.ui.connection

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.ConnectionIdentity
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.PortForwardRule
import com.opencode.sshterminal.data.PortForwardType
import com.opencode.sshterminal.data.ProxyJumpEntry
import com.opencode.sshterminal.data.parseProxyJumpEntries
import com.opencode.sshterminal.data.proxyJumpHostPortKey
import com.opencode.sshterminal.terminal.TerminalColorSchemePreset
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    onConnect: (connectionId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ConnectionListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val profiles by viewModel.profiles.collectAsState()
    val identities by viewModel.identities.collectAsState()
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showQuickConnectDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(ConnectionSortOption.NAME) }
    val availableGroupFilters =
        remember(profiles) {
            val namedGroups =
                profiles
                    .mapNotNull { profile ->
                        profile.group?.trim()?.takeIf { group -> group.isNotEmpty() }
                    }.distinct()
                    .sortedBy { group -> group.lowercase() }
            buildList {
                if (profiles.any { profile -> profile.group.isNullOrBlank() }) {
                    add(UNGROUPED_FILTER_KEY)
                }
                addAll(namedGroups)
            }
        }
    LaunchedEffect(availableGroupFilters) {
        if (selectedGroupFilter != null && selectedGroupFilter !in availableGroupFilters) {
            selectedGroupFilter = null
        }
    }
    val filteredProfiles =
        remember(profiles, searchQuery, selectedGroupFilter, sortOption) {
            filterAndSortProfiles(
                profiles = profiles,
                searchQuery = searchQuery,
                selectedGroupFilter = selectedGroupFilter,
                sortOption = sortOption,
            )
        }
    val sshConfigPicker =
        rememberConnectionSshConfigPicker(
            onImported = { content ->
                viewModel.importFromSshConfig(content) { summary ->
                    val message =
                        when {
                            summary.importedCount <= 0 -> {
                                context.getString(
                                    R.string.connection_import_no_valid_hosts,
                                    summary.skippedCount,
                                )
                            }

                            summary.skippedCount > 0 -> {
                                context.getString(
                                    R.string.connection_import_result_with_skipped,
                                    summary.importedCount,
                                    summary.skippedCount,
                                )
                            }

                            else -> {
                                context.getString(
                                    R.string.connection_import_result,
                                    summary.importedCount,
                                )
                            }
                        }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onFailed = {
                Toast.makeText(
                    context,
                    context.getString(R.string.connection_import_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )

    Scaffold(
        topBar = {
            ConnectionListTopBar(
                onImportSshConfig = sshConfigPicker,
                onQuickConnect = { showQuickConnectDialog = true },
                onOpenSettings = onOpenSettings,
            )
        },
        floatingActionButton = {
            ConnectionListAddButton(
                onClick = {
                    editingProfile = null
                    showSheet = true
                },
            )
        },
    ) { padding ->
        ConnectionListContent(
            allProfiles = profiles,
            profiles = filteredProfiles,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            availableGroupFilters = availableGroupFilters,
            selectedGroupFilter = selectedGroupFilter,
            onSelectGroupFilter = { selectedGroupFilter = it },
            sortOption = sortOption,
            onSelectSortOption = { sortOption = it },
            onConnect = onConnect,
            onEdit = { profile ->
                editingProfile = profile
                showSheet = true
            },
            onDelete = { deleteTarget = it },
            modifier = Modifier.padding(padding),
        )
    }

    if (showSheet) {
        ConnectionBottomSheet(
            initial = editingProfile,
            identities = identities,
            onDismiss = { showSheet = false },
            onSave = { profile ->
                viewModel.save(profile)
                showSheet = false
            },
        )
    }

    ConnectionDeleteDialog(
        target = deleteTarget,
        onDismiss = { deleteTarget = null },
        onDelete = { target ->
            viewModel.delete(target.id)
            deleteTarget = null
        },
    )

    if (showQuickConnectDialog) {
        QuickConnectDialog(
            onDismiss = { showQuickConnectDialog = false },
            onConnect = { host, port, username, password ->
                showQuickConnectDialog = false
                viewModel.quickConnect(
                    host = host,
                    port = port,
                    username = username,
                    password = password.takeIf { it.isNotBlank() },
                ) { connectionId -> onConnect(connectionId) }
            },
        )
    }
}

@Composable
private fun ConnectionListContent(
    allProfiles: List<ConnectionProfile>,
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    availableGroupFilters: List<String>,
    selectedGroupFilter: String?,
    onSelectGroupFilter: (String?) -> Unit,
    sortOption: ConnectionSortOption,
    onSelectSortOption: (ConnectionSortOption) -> Unit,
    onConnect: (connectionId: String) -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ungroupedLabel = stringResource(R.string.connection_group_ungrouped)
        val allGroupsLabel = stringResource(R.string.connection_filter_all_groups)
        val sortLabel = stringResource(R.string.connection_sort_label)
        val groupsLabel = stringResource(R.string.connection_filter_groups_label)
        val sortNameLabel = stringResource(R.string.connection_sort_name)
        val sortRecentLabel = stringResource(R.string.connection_sort_recent)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(R.string.connection_search_label)) },
            placeholder = { Text(stringResource(R.string.connection_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (allProfiles.isNotEmpty()) {
            ConnectionListFiltersBar(
                availableGroupFilters = availableGroupFilters,
                selectedGroupFilter = selectedGroupFilter,
                ungroupedLabel = ungroupedLabel,
                allGroupsLabel = allGroupsLabel,
                groupsLabel = groupsLabel,
                sortLabel = sortLabel,
                sortNameLabel = sortNameLabel,
                sortRecentLabel = sortRecentLabel,
                sortOption = sortOption,
                onSelectGroupFilter = onSelectGroupFilter,
                onSelectSortOption = onSelectSortOption,
            )
        }

        when {
            allProfiles.isEmpty() -> {
                EmptyConnectionState(modifier = Modifier.fillMaxSize())
            }
            profiles.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.connection_no_search_results, searchQuery.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(profiles, key = { _, profile -> profile.id }) { index, profile ->
                        val currentGroup = profile.group?.takeIf { it.isNotBlank() } ?: ungroupedLabel
                        val previousGroup =
                            profiles
                                .getOrNull(index - 1)
                                ?.group
                                ?.takeIf { it.isNotBlank() } ?: ungroupedLabel
                        if (index == 0 || currentGroup != previousGroup) {
                            Text(
                                text = currentGroup,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ConnectionCard(
                            profile = profile,
                            onClick = { onConnect(profile.id) },
                            onEdit = { onEdit(profile) },
                            onDelete = { onDelete(profile) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionListFiltersBar(
    availableGroupFilters: List<String>,
    selectedGroupFilter: String?,
    ungroupedLabel: String,
    allGroupsLabel: String,
    groupsLabel: String,
    sortLabel: String,
    sortNameLabel: String,
    sortRecentLabel: String,
    sortOption: ConnectionSortOption,
    onSelectGroupFilter: (String?) -> Unit,
    onSelectSortOption: (ConnectionSortOption) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val selectedSortLabel =
        when (sortOption) {
            ConnectionSortOption.NAME -> sortNameLabel
            ConnectionSortOption.RECENT -> sortRecentLabel
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = groupsLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { sortExpanded = true }) {
                Text("$sortLabel: $selectedSortLabel")
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(sortNameLabel) },
                    onClick = {
                        sortExpanded = false
                        onSelectSortOption(ConnectionSortOption.NAME)
                    },
                )
                DropdownMenuItem(
                    text = { Text(sortRecentLabel) },
                    onClick = {
                        sortExpanded = false
                        onSelectSortOption(ConnectionSortOption.RECENT)
                    },
                )
            }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionGroupFilterButton(
            label = allGroupsLabel,
            selected = selectedGroupFilter == null,
            onClick = { onSelectGroupFilter(null) },
        )
        availableGroupFilters.forEach { groupFilter ->
            val label = if (groupFilter == UNGROUPED_FILTER_KEY) ungroupedLabel else groupFilter
            ConnectionGroupFilterButton(
                label = label,
                selected = selectedGroupFilter == groupFilter,
                onClick = { onSelectGroupFilter(groupFilter) },
            )
        }
    }
}

@Composable
private fun ConnectionGroupFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
    ) {
        Text(
            text = label,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun EmptyConnectionState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.connection_no_saved_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.connection_no_saved_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ConnectionDeleteDialog(
    target: ConnectionProfile?,
    onDismiss: () -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
) {
    if (target == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_delete_title)) },
        text = { Text(stringResource(R.string.connection_delete_message, target.name)) },
        confirmButton = {
            TextButton(onClick = { onDelete(target) }) {
                Text(
                    stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun ConnectionCard(
    profile: ConnectionProfile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
            ) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${profile.username}@${profile.host}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                connectionRouteSummary(profile)?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.connection_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.connection_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private data class ConnectionDraft(
    val name: String = "",
    val group: String = "",
    val terminalColorSchemeId: String = "",
    val host: String = "",
    val proxyJump: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = "",
    val proxyJumpIdentityIds: Map<String, String> = emptyMap(),
    val portForwards: List<PortForwardRule> = emptyList(),
)

private fun ConnectionProfile?.toDraft(): ConnectionDraft =
    ConnectionDraft(
        name = this?.name.orEmpty(),
        group = this?.group.orEmpty(),
        terminalColorSchemeId = this?.terminalColorSchemeId.orEmpty(),
        host = this?.host.orEmpty(),
        proxyJump = this?.proxyJump.orEmpty(),
        port = this?.port?.toString() ?: "22",
        username = this?.username.orEmpty(),
        password = this?.password.orEmpty(),
        privateKeyPath = this?.privateKeyPath.orEmpty(),
        privateKeyPassphrase = this?.privateKeyPassphrase.orEmpty(),
        proxyJumpIdentityIds = this?.proxyJumpIdentityIds.orEmpty(),
        portForwards = this?.portForwards.orEmpty(),
    )

private fun ConnectionDraft.toProfileOrNull(
    initial: ConnectionProfile?,
    selectedIdentityId: String?,
): ConnectionProfile? {
    if (host.isBlank() || username.isBlank()) return null
    val proxyJumpEntries = parseProxyJumpEntries(proxyJump)
    val validHopKeys =
        proxyJumpEntries
            .map { entry -> proxyJumpHostPortKey(entry.host, entry.port) }
            .toSet()
    val filteredProxyJumpIdentityIds = proxyJumpIdentityIds.filterKeys { key -> key in validHopKeys }
    return ConnectionProfile(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.ifBlank { "$username@$host" },
        group = group.trim().ifBlank { null },
        terminalColorSchemeId = terminalColorSchemeId.trim().ifBlank { null },
        host = host,
        proxyJump = proxyJump.trim().ifBlank { null },
        port = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22,
        username = username,
        password = password.ifBlank { null },
        privateKeyPath = privateKeyPath.ifBlank { null },
        privateKeyPassphrase = privateKeyPassphrase.ifBlank { null },
        identityId = selectedIdentityId,
        proxyJumpIdentityIds = filteredProxyJumpIdentityIds,
        portForwards = portForwards,
        lastUsedEpochMillis = initial?.lastUsedEpochMillis ?: System.currentTimeMillis(),
    )
}

private fun connectionRouteSummary(profile: ConnectionProfile): String? {
    val tags = mutableListOf<String>()
    if (!profile.proxyJump.isNullOrBlank()) {
        val hops = parseProxyJumpEntries(profile.proxyJump).size
        tags += if (hops > 0) "PJ:$hops" else "PJ"
    }
    if (profile.portForwards.isNotEmpty()) {
        tags += "FWD:${profile.portForwards.size}"
    }
    return tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionBottomSheet(
    initial: ConnectionProfile?,
    identities: List<ConnectionIdentity>,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initial) { mutableStateOf(initial.toDraft()) }
    var selectedIdentityId by remember(initial) { mutableStateOf(initial?.identityId) }
    val privateKeyPicker =
        rememberConnectionPrivateKeyPicker(
            onImported = { importedPath ->
                draft = draft.copy(privateKeyPath = importedPath)
            },
        )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    if (initial != null) {
                        stringResource(R.string.connection_sheet_edit_title)
                    } else {
                        stringResource(R.string.connection_sheet_new_title)
                    },
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))

            ConnectionFormFields(
                draft = draft,
                identities = identities,
                selectedIdentityId = selectedIdentityId,
                onSelectIdentity = { identity ->
                    selectedIdentityId = identity?.id
                    if (identity != null) {
                        draft =
                            draft.copy(
                                username = identity.username,
                                password = identity.password.orEmpty(),
                                privateKeyPath = identity.privateKeyPath.orEmpty(),
                                privateKeyPassphrase = identity.privateKeyPassphrase.orEmpty(),
                            )
                    }
                },
                onDraftChange = { draft = it },
                onPickPrivateKey = privateKeyPicker,
                onClearPrivateKey = { draft = draft.copy(privateKeyPath = "", privateKeyPassphrase = "") },
                onAddPortForwardRule = { rule ->
                    draft = draft.copy(portForwards = draft.portForwards + rule)
                },
                onUpdatePortForwardRuleAt = { index, rule ->
                    if (index in draft.portForwards.indices) {
                        val updated =
                            draft.portForwards.toMutableList().also { rules ->
                                rules[index] = rule
                            }
                        draft = draft.copy(portForwards = updated)
                    }
                },
                onMovePortForwardRule = { from, to ->
                    draft = draft.copy(portForwards = movePortForwardRule(draft.portForwards, from, to))
                },
                onRemovePortForwardRuleAt = { index ->
                    if (index in draft.portForwards.indices) {
                        draft = draft.copy(portForwards = draft.portForwards.toMutableList().also { it.removeAt(index) })
                    }
                },
                onClearPortForwards = { draft = draft.copy(portForwards = emptyList()) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }

                Button(
                    onClick = {
                        draft.toProfileOrNull(initial, selectedIdentityId)?.let(onSave)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
    }
}

@Composable
private fun ConnectionFormFields(
    draft: ConnectionDraft,
    identities: List<ConnectionIdentity>,
    selectedIdentityId: String?,
    onSelectIdentity: (ConnectionIdentity?) -> Unit,
    onDraftChange: (ConnectionDraft) -> Unit,
    onPickPrivateKey: () -> Unit,
    onClearPrivateKey: () -> Unit,
    onAddPortForwardRule: (PortForwardRule) -> Unit,
    onUpdatePortForwardRuleAt: (Int, PortForwardRule) -> Unit,
    onMovePortForwardRule: (Int, Int) -> Unit,
    onRemovePortForwardRuleAt: (Int) -> Unit,
    onClearPortForwards: () -> Unit,
) {
    if (identities.isNotEmpty()) {
        IdentitySelectorField(
            identities = identities,
            selectedIdentityId = selectedIdentityId,
            onSelectIdentity = onSelectIdentity,
        )
    }

    OutlinedTextField(
        value = draft.name,
        onValueChange = { onDraftChange(draft.copy(name = it)) },
        label = { Text(stringResource(R.string.connection_label_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.group,
        onValueChange = { onDraftChange(draft.copy(group = it)) },
        label = { Text(stringResource(R.string.connection_label_group_optional)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ConnectionTerminalSchemeField(
        selectedSchemeId = draft.terminalColorSchemeId,
        onSelectSchemeId = { schemeId ->
            onDraftChange(draft.copy(terminalColorSchemeId = schemeId))
        },
    )
    OutlinedTextField(
        value = draft.host,
        onValueChange = { onDraftChange(draft.copy(host = it)) },
        label = { Text(stringResource(R.string.connection_label_host)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.proxyJump,
        onValueChange = { value ->
            val hopKeys =
                parseProxyJumpEntries(value)
                    .map { entry -> proxyJumpHostPortKey(entry.host, entry.port) }
                    .toSet()
            onDraftChange(
                draft.copy(
                    proxyJump = value,
                    proxyJumpIdentityIds = draft.proxyJumpIdentityIds.filterKeys { key -> key in hopKeys },
                ),
            )
        },
        label = { Text(stringResource(R.string.connection_label_proxy_jump_optional)) },
        placeholder = { Text(stringResource(R.string.connection_proxy_jump_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    ProxyJumpIdentitySection(
        proxyJumpEntries = parseProxyJumpEntries(draft.proxyJump),
        identities = identities,
        selectedIdentityIds = draft.proxyJumpIdentityIds,
        onSelect = { entry, identity ->
            val key = proxyJumpHostPortKey(entry.host, entry.port)
            val updated =
                if (identity == null) {
                    draft.proxyJumpIdentityIds - key
                } else {
                    draft.proxyJumpIdentityIds + (key to identity.id)
                }
            onDraftChange(draft.copy(proxyJumpIdentityIds = updated))
        },
    )
    PortForwardRulesSection(
        rules = draft.portForwards,
        onAddRule = onAddPortForwardRule,
        onUpdateRuleAt = onUpdatePortForwardRuleAt,
        onMoveRule = onMovePortForwardRule,
        onRemoveRuleAt = onRemovePortForwardRuleAt,
        onClear = onClearPortForwards,
    )
    OutlinedTextField(
        value = draft.port,
        onValueChange = { onDraftChange(draft.copy(port = it)) },
        label = { Text(stringResource(R.string.connection_label_port)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.username,
        onValueChange = { onDraftChange(draft.copy(username = it)) },
        label = { Text(stringResource(R.string.connection_label_username)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.password,
        onValueChange = { onDraftChange(draft.copy(password = it)) },
        label = { Text(stringResource(R.string.connection_label_password_optional)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    ConnectionPrivateKeyField(
        privateKeyPath = draft.privateKeyPath,
        privateKeyPassphrase = draft.privateKeyPassphrase,
        onPrivateKeyPassphraseChange = { onDraftChange(draft.copy(privateKeyPassphrase = it)) },
        onPickPrivateKey = onPickPrivateKey,
        onClearPrivateKey = onClearPrivateKey,
    )
}

@Composable
private fun IdentitySelectorField(
    identities: List<ConnectionIdentity>,
    selectedIdentityId: String?,
    onSelectIdentity: (ConnectionIdentity?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedIdentity = identities.firstOrNull { identity -> identity.id == selectedIdentityId }
    val selectedLabel =
        selectedIdentity?.name ?: stringResource(R.string.connection_identity_manual)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${stringResource(R.string.connection_identity_selector_label)}: $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_identity_manual)) },
                onClick = {
                    expanded = false
                    onSelectIdentity(null)
                },
            )
            identities.forEach { identity ->
                DropdownMenuItem(
                    text = { Text(identity.name) },
                    onClick = {
                        expanded = false
                        onSelectIdentity(identity)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionTerminalSchemeField(
    selectedSchemeId: String,
    onSelectSchemeId: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        if (selectedSchemeId.isBlank()) {
            stringResource(R.string.connection_terminal_scheme_use_app_default)
        } else {
            TerminalColorSchemePreset.fromId(selectedSchemeId).displayName
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${stringResource(R.string.connection_terminal_scheme_label)}: $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_terminal_scheme_use_app_default)) },
                onClick = {
                    expanded = false
                    onSelectSchemeId("")
                },
            )
            TerminalColorSchemePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.displayName) },
                    onClick = {
                        expanded = false
                        onSelectSchemeId(preset.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProxyJumpIdentitySection(
    proxyJumpEntries: List<ProxyJumpEntry>,
    identities: List<ConnectionIdentity>,
    selectedIdentityIds: Map<String, String>,
    onSelect: (ProxyJumpEntry, ConnectionIdentity?) -> Unit,
) {
    if (proxyJumpEntries.isEmpty() || identities.isEmpty()) return

    Text(
        text = stringResource(R.string.connection_proxy_jump_identity_title),
        style = MaterialTheme.typography.labelLarge,
    )
    proxyJumpEntries.forEach { entry ->
        ProxyJumpIdentityRow(
            entry = entry,
            identities = identities,
            selectedIdentityId = selectedIdentityIds[proxyJumpHostPortKey(entry.host, entry.port)],
            onSelect = { identity -> onSelect(entry, identity) },
        )
    }
}

@Composable
private fun PortForwardRulesSection(
    rules: List<PortForwardRule>,
    onAddRule: (PortForwardRule) -> Unit,
    onUpdateRuleAt: (Int, PortForwardRule) -> Unit,
    onMoveRule: (Int, Int) -> Unit,
    onRemoveRuleAt: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRuleIndex by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.connection_port_forward_rules_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.connection_add_port_forward_rule))
            }
            if (rules.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.connection_clear_port_forward_rules))
                }
            }
        }
    }
    PortForwardPresetSection(onAddRule = onAddRule)
    if (rules.isEmpty()) {
        Text(
            text = stringResource(R.string.connection_port_forward_rules_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        rules.forEachIndexed { index, rule ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPortForwardRuleDisplay(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editingRuleIndex = index }) {
                    Text(stringResource(R.string.connection_edit))
                }
                TextButton(
                    onClick = { onMoveRule(index, index - 1) },
                    enabled = index > 0,
                ) {
                    Text(stringResource(R.string.connection_move_up_short))
                }
                TextButton(
                    onClick = { onMoveRule(index, index + 1) },
                    enabled = index < rules.lastIndex,
                ) {
                    Text(stringResource(R.string.connection_move_down_short))
                }
                TextButton(onClick = { onRemoveRuleAt(index) }) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }

    if (showAddDialog) {
        PortForwardRuleDialog(
            title = stringResource(R.string.connection_port_forward_add_title),
            confirmLabel = stringResource(R.string.connection_add_port_forward_rule),
            onDismiss = { showAddDialog = false },
            onConfirm = { rule ->
                onAddRule(rule)
                showAddDialog = false
            },
        )
    }

    val targetIndex = editingRuleIndex
    if (targetIndex != null && targetIndex in rules.indices) {
        PortForwardRuleDialog(
            title = stringResource(R.string.connection_port_forward_edit_title),
            confirmLabel = stringResource(R.string.common_save),
            initialRule = rules[targetIndex],
            onDismiss = { editingRuleIndex = null },
            onConfirm = { rule ->
                onUpdateRuleAt(targetIndex, rule)
                editingRuleIndex = null
            },
        )
    }
}

@Composable
private fun PortForwardPresetSection(onAddRule: (PortForwardRule) -> Unit) {
    Text(
        text = stringResource(R.string.connection_port_forward_presets_title),
        style = MaterialTheme.typography.labelMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                onAddRule(
                    PortForwardRule(
                        type = PortForwardType.LOCAL,
                        bindHost = "127.0.0.1",
                        bindPort = 8080,
                        targetHost = "127.0.0.1",
                        targetPort = 80,
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.connection_port_forward_preset_web))
        }
        OutlinedButton(
            onClick = {
                onAddRule(
                    PortForwardRule(
                        type = PortForwardType.LOCAL,
                        bindHost = "127.0.0.1",
                        bindPort = 8443,
                        targetHost = "127.0.0.1",
                        targetPort = 443,
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.connection_port_forward_preset_https))
        }
    }
    OutlinedButton(
        onClick = {
            onAddRule(
                PortForwardRule(
                    type = PortForwardType.DYNAMIC,
                    bindHost = "127.0.0.1",
                    bindPort = 1080,
                ),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.connection_port_forward_preset_socks))
    }
}

@Composable
private fun PortForwardRuleDialog(
    title: String,
    confirmLabel: String,
    initialRule: PortForwardRule? = null,
    onDismiss: () -> Unit,
    onConfirm: (PortForwardRule) -> Unit,
) {
    var type by remember(initialRule) { mutableStateOf(initialRule?.type ?: PortForwardType.LOCAL) }
    var bindHost by remember(initialRule) { mutableStateOf(initialRule?.bindHost.orEmpty()) }
    var bindPort by remember(initialRule) { mutableStateOf(initialRule?.bindPort?.toString().orEmpty()) }
    var targetHost by remember(initialRule) { mutableStateOf(initialRule?.targetHost.orEmpty()) }
    var targetPort by remember(initialRule) { mutableStateOf(initialRule?.targetPort?.toString().orEmpty()) }

    val candidate =
        buildPortForwardRule(
            type = type,
            bindHostInput = bindHost,
            bindPortInput = bindPort,
            targetHostInput = targetHost,
            targetPortInput = targetPort,
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PortForwardTypeSelector(
                    selectedType = type,
                    onSelect = { selected ->
                        type = selected
                    },
                )
                OutlinedTextField(
                    value = bindHost,
                    onValueChange = { bindHost = it },
                    label = { Text(stringResource(R.string.connection_port_forward_bind_host_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bindPort,
                    onValueChange = { bindPort = it },
                    label = { Text(stringResource(R.string.connection_port_forward_bind_port)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type != PortForwardType.DYNAMIC) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text(stringResource(R.string.connection_port_forward_target_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text(stringResource(R.string.connection_port_forward_target_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { candidate?.let(onConfirm) },
                enabled = candidate != null,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PortForwardTypeSelector(
    selectedType: PortForwardType,
    onSelect: (PortForwardType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = portForwardTypeLabel(selectedType)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${stringResource(R.string.connection_port_forward_type_label)}: $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PortForwardType.values().forEach { type ->
                DropdownMenuItem(
                    text = { Text(portForwardTypeLabel(type)) },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun portForwardTypeLabel(type: PortForwardType): String {
    return when (type) {
        PortForwardType.LOCAL -> stringResource(R.string.connection_port_forward_type_local)
        PortForwardType.REMOTE -> stringResource(R.string.connection_port_forward_type_remote)
        PortForwardType.DYNAMIC -> stringResource(R.string.connection_port_forward_type_dynamic)
    }
}

@Composable
private fun ProxyJumpIdentityRow(
    entry: ProxyJumpEntry,
    identities: List<ConnectionIdentity>,
    selectedIdentityId: String?,
    onSelect: (ConnectionIdentity?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = identities.firstOrNull { identity -> identity.id == selectedIdentityId }
    val selectedLabel = selected?.name ?: stringResource(R.string.connection_identity_manual)
    val hopLabel = "${entry.username?.let { "$it@" }.orEmpty()}${entry.host}:${entry.port}"

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$hopLabel -> $selectedLabel")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.connection_identity_manual)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            identities.forEach { identity ->
                DropdownMenuItem(
                    text = { Text(identity.name) },
                    onClick = {
                        expanded = false
                        onSelect(identity)
                    },
                )
            }
        }
    }
}
