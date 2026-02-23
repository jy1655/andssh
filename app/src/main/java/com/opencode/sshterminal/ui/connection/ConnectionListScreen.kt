package com.opencode.sshterminal.ui.connection

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.ConnectionProfile
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    onConnect: (connectionId: String) -> Unit,
    viewModel: ConnectionListViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    var editingProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ConnectionProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.connection_list_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingProfile = null
                    showSheet = true
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.connection_add))
            }
        },
    ) { padding ->
        ConnectionListContent(
            profiles = profiles,
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
}

@Composable
private fun ConnectionListContent(
    profiles: List<ConnectionProfile>,
    onConnect: (connectionId: String) -> Unit,
    onEdit: (ConnectionProfile) -> Unit,
    onDelete: (ConnectionProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profiles.isEmpty()) {
        EmptyConnectionState(modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(profiles, key = { it.id }) { profile ->
            ConnectionCard(
                profile = profile,
                onClick = { onConnect(profile.id) },
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile) },
            )
        }
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
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val privateKeyPath: String = "",
)

private fun ConnectionProfile?.toDraft(): ConnectionDraft =
    ConnectionDraft(
        name = this?.name.orEmpty(),
        host = this?.host.orEmpty(),
        port = this?.port?.toString() ?: "22",
        username = this?.username.orEmpty(),
        password = this?.password.orEmpty(),
        privateKeyPath = this?.privateKeyPath.orEmpty(),
    )

private fun ConnectionDraft.toProfileOrNull(initial: ConnectionProfile?): ConnectionProfile? {
    if (host.isBlank() || username.isBlank()) return null
    return ConnectionProfile(
        id = initial?.id ?: UUID.randomUUID().toString(),
        name = name.ifBlank { "$username@$host" },
        host = host,
        port = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22,
        username = username,
        password = password.ifBlank { null },
        privateKeyPath = privateKeyPath.ifBlank { null },
        lastUsedEpochMillis = initial?.lastUsedEpochMillis ?: System.currentTimeMillis(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionBottomSheet(
    initial: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(initial) { mutableStateOf(initial.toDraft()) }

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
                onDraftChange = { draft = it },
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
                        draft.toProfileOrNull(initial)?.let(onSave)
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
    onDraftChange: (ConnectionDraft) -> Unit,
) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onDraftChange(draft.copy(name = it)) },
        label = { Text(stringResource(R.string.connection_label_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = draft.host,
        onValueChange = { onDraftChange(draft.copy(host = it)) },
        label = { Text(stringResource(R.string.connection_label_host)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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
    OutlinedTextField(
        value = draft.privateKeyPath,
        onValueChange = { onDraftChange(draft.copy(privateKeyPath = it)) },
        label = { Text(stringResource(R.string.connection_label_private_key_path)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
