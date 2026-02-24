package com.opencode.sshterminal.ui.sftp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.opencode.sshterminal.R
import com.opencode.sshterminal.sftp.RemoteEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpBrowserScreen(
    onBack: () -> Unit,
    viewModel: SftpBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showMkdirDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var contextMenuEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(state.entries) {
        val currentPaths = state.entries.map { entry -> entry.path }.toSet()
        selectedPaths = selectedPaths.filter { path -> path in currentPaths }.toSet()
    }
    val launchers = rememberSftpLaunchers(viewModel::downloadToStream, viewModel::uploadFromUri)
    val menuCallbacks =
        SftpMenuCallbacks(
            onNavigate = { path ->
                selectionMode = false
                selectedPaths = emptySet()
                viewModel.navigateTo(path)
            },
            onDownload = { entry ->
                contextMenuEntry = null
                launchers.launchDownload(entry.path, entry.name)
            },
            onRename = { entry ->
                contextMenuEntry = null
                renameTarget = entry
            },
            onDelete = { entry ->
                contextMenuEntry = null
                deleteTarget = entry
            },
        )
    val dialogState =
        SftpDialogState(
            showMkdirDialog = showMkdirDialog,
            renameTarget = renameTarget,
            deleteTarget = deleteTarget,
            showBulkDeleteDialog = showBulkDeleteDialog,
            selectedCount = selectedPaths.size,
        )
    val dialogCallbacks =
        SftpDialogCallbacks(
            onDismissMkdir = { showMkdirDialog = false },
            onDismissRename = { renameTarget = null },
            onDismissDelete = { deleteTarget = null },
            onDismissBulkDelete = { showBulkDeleteDialog = false },
            onMkdir = { name ->
                showMkdirDialog = false
                if (name.isNotBlank()) viewModel.mkdir(name)
            },
            onRename = { oldPath, newName ->
                renameTarget = null
                if (newName.isNotBlank()) viewModel.rename(oldPath, newName)
            },
            onDelete = { path ->
                deleteTarget = null
                viewModel.rm(path)
            },
            onDeleteSelected = {
                showBulkDeleteDialog = false
                val targets = selectedPaths.toList()
                selectionMode = false
                selectedPaths = emptySet()
                viewModel.rmMany(targets)
            },
        )
    SftpBrowserScaffold(
        state = state,
        selectionMode = selectionMode,
        selectedPaths = selectedPaths,
        onToggleSelection = { path ->
            selectedPaths =
                if (path in selectedPaths) {
                    selectedPaths - path
                } else {
                    selectedPaths + path
                }
        },
        contextMenuEntry = contextMenuEntry,
        onContextMenuEntryChange = { contextMenuEntry = it },
        menuCallbacks = menuCallbacks,
        scaffoldCallbacks =
            SftpScaffoldCallbacks(
                onBack = onBack,
                onUpload = launchers.launchUploadPicker,
                onShowMkdirDialog = { showMkdirDialog = true },
                onRefresh = viewModel::list,
                selectionMode = selectionMode,
                selectedCount = selectedPaths.size,
                onEnterSelectionMode = {
                    contextMenuEntry = null
                    selectionMode = true
                },
                onClearSelection = {
                    selectionMode = false
                    selectedPaths = emptySet()
                },
                onDeleteSelection = {
                    if (selectedPaths.isNotEmpty()) {
                        showBulkDeleteDialog = true
                    }
                },
            ),
    )
    SftpDialogs(state = dialogState, callbacks = dialogCallbacks)
}

private data class SftpScaffoldCallbacks(
    val onBack: () -> Unit,
    val onUpload: () -> Unit,
    val onShowMkdirDialog: () -> Unit,
    val onRefresh: () -> Unit,
    val selectionMode: Boolean,
    val selectedCount: Int,
    val onEnterSelectionMode: () -> Unit,
    val onClearSelection: () -> Unit,
    val onDeleteSelection: () -> Unit,
)

@Composable
private fun SftpBrowserScaffold(
    state: SftpUiState,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onToggleSelection: (String) -> Unit,
    contextMenuEntry: RemoteEntry?,
    onContextMenuEntryChange: (RemoteEntry?) -> Unit,
    menuCallbacks: SftpMenuCallbacks,
    scaffoldCallbacks: SftpScaffoldCallbacks,
) {
    Scaffold(
        topBar = {
            SftpTopBar(
                busy = state.busy,
                selectionMode = scaffoldCallbacks.selectionMode,
                selectedCount = scaffoldCallbacks.selectedCount,
                onBack = scaffoldCallbacks.onBack,
                onUpload = scaffoldCallbacks.onUpload,
                onEnterSelectionMode = scaffoldCallbacks.onEnterSelectionMode,
                onClearSelection = scaffoldCallbacks.onClearSelection,
                onDeleteSelection = scaffoldCallbacks.onDeleteSelection,
            )
        },
        floatingActionButton = {
            if (!scaffoldCallbacks.selectionMode) {
                FloatingActionButton(onClick = scaffoldCallbacks.onShowMkdirDialog) {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.sftp_new_folder),
                    )
                }
            }
        },
    ) { padding ->
        SftpBrowserBody(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contextMenuEntry = contextMenuEntry,
            onContextMenuEntryChange = onContextMenuEntryChange,
            menuCallbacks = menuCallbacks,
            onRefresh = scaffoldCallbacks.onRefresh,
            selectionMode = selectionMode,
            selectedPaths = selectedPaths,
            onToggleSelection = onToggleSelection,
        )
    }
}

private data class SftpLaunchers(
    val launchDownload: (remotePath: String, suggestedName: String) -> Unit,
    val launchUploadPicker: () -> Unit,
)

@Composable
private fun rememberSftpLaunchers(
    onDownloadToStream: (remotePath: String, uri: Uri) -> Unit,
    onUploadFromUri: (uri: Uri, remoteName: String) -> Unit,
): SftpLaunchers {
    val context = LocalContext.current
    var pendingDownloadPath by remember { mutableStateOf("") }

    val downloadLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        ) { uri: Uri? ->
            if (uri != null && pendingDownloadPath.isNotEmpty()) {
                onDownloadToStream(pendingDownloadPath, uri)
            }
            pendingDownloadPath = ""
        }

    val uploadLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri: Uri? ->
            if (uri != null) {
                val docFile = DocumentFile.fromSingleUri(context, uri)
                val fileName = docFile?.name ?: "upload_${System.currentTimeMillis()}"
                onUploadFromUri(uri, fileName)
            }
        }

    return SftpLaunchers(
        launchDownload = { remotePath, suggestedName ->
            pendingDownloadPath = remotePath
            downloadLauncher.launch(suggestedName)
        },
        launchUploadPicker = {
            uploadLauncher.launch("*/*")
        },
    )
}

private data class SftpMenuCallbacks(
    val onNavigate: (String) -> Unit,
    val onDownload: (RemoteEntry) -> Unit,
    val onRename: (RemoteEntry) -> Unit,
    val onDelete: (RemoteEntry) -> Unit,
)

private data class SftpDialogState(
    val showMkdirDialog: Boolean,
    val renameTarget: RemoteEntry?,
    val deleteTarget: RemoteEntry?,
    val showBulkDeleteDialog: Boolean,
    val selectedCount: Int,
)

private data class SftpDialogCallbacks(
    val onDismissMkdir: () -> Unit,
    val onDismissRename: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onDismissBulkDelete: () -> Unit,
    val onMkdir: (name: String) -> Unit,
    val onRename: (oldPath: String, newName: String) -> Unit,
    val onDelete: (remotePath: String) -> Unit,
    val onDeleteSelected: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SftpTopBar(
    busy: Boolean,
    selectionMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text =
                    if (selectionMode) {
                        stringResource(R.string.sftp_selected_count, selectedCount)
                    } else {
                        stringResource(R.string.sftp_browser_title)
                    },
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.sftp_back),
                )
            }
        },
        actions = {
            if (selectionMode) {
                if (selectedCount > 0) {
                    IconButton(onClick = onDeleteSelection) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.sftp_delete_selected),
                        )
                    }
                }
                IconButton(onClick = onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_cancel),
                    )
                }
            } else {
                IconButton(onClick = onEnterSelectionMode) {
                    Icon(
                        Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = stringResource(R.string.sftp_select_multiple),
                    )
                }
                IconButton(onClick = onUpload) {
                    Icon(
                        Icons.Default.CloudUpload,
                        contentDescription = stringResource(R.string.sftp_upload),
                    )
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun SftpBrowserBody(
    state: SftpUiState,
    onRefresh: () -> Unit,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
    contextMenuEntry: RemoteEntry?,
    onContextMenuEntryChange: (RemoteEntry?) -> Unit,
    menuCallbacks: SftpMenuCallbacks,
) {
    val pullRefreshState = rememberPullRefreshState(refreshing = state.busy, onRefresh = onRefresh)

    Box(modifier = modifier.pullRefresh(pullRefreshState)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Breadcrumbs(
                path = state.remotePath,
                onNavigate = menuCallbacks.onNavigate,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            AnimatedVisibility(
                visible = state.transferProgress >= 0f,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LinearProgressIndicator(
                    progress = { state.transferProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            AnimatedVisibility(
                visible = state.status.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SftpEntryList(
                entries = state.entries,
                selectionMode = selectionMode,
                selectedPaths = selectedPaths,
                onToggleSelection = onToggleSelection,
                contextMenuEntry = contextMenuEntry,
                onContextMenuEntryChange = onContextMenuEntryChange,
                menuCallbacks = menuCallbacks,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        }

        PullRefreshIndicator(
            refreshing = state.busy,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SftpEntryList(
    entries: List<RemoteEntry>,
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onToggleSelection: (String) -> Unit,
    contextMenuEntry: RemoteEntry?,
    onContextMenuEntryChange: (RemoteEntry?) -> Unit,
    menuCallbacks: SftpMenuCallbacks,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(entries, key = { it.path }) { entry ->
            val selected = entry.path in selectedPaths
            Box {
                FileEntryRow(
                    entry = entry,
                    selectionMode = selectionMode,
                    selected = selected,
                    onClick = {
                        if (selectionMode) {
                            onToggleSelection(entry.path)
                        } else if (entry.isDirectory) {
                            menuCallbacks.onNavigate(entry.path)
                        }
                    },
                    onLongClick = {
                        if (selectionMode) {
                            onToggleSelection(entry.path)
                        } else {
                            onContextMenuEntryChange(entry)
                        }
                    },
                )
                if (!selectionMode) {
                    DropdownMenu(
                        expanded = contextMenuEntry == entry,
                        onDismissRequest = { onContextMenuEntryChange(null) },
                    ) {
                        if (entry.isDirectory) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_open)) },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                },
                                onClick = {
                                    onContextMenuEntryChange(null)
                                    menuCallbacks.onNavigate(entry.path)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sftp_download)) },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                },
                                onClick = { menuCallbacks.onDownload(entry) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sftp_rename)) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                            onClick = { menuCallbacks.onRename(entry) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sftp_delete)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuCallbacks.onDelete(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SftpDialogs(
    state: SftpDialogState,
    callbacks: SftpDialogCallbacks,
) {
    if (state.showMkdirDialog) {
        InputDialog(
            dialogState =
                InputDialogState(
                    title = stringResource(R.string.sftp_new_folder),
                    placeholder = stringResource(R.string.sftp_folder_name_placeholder),
                    confirmLabel = stringResource(R.string.sftp_create),
                    onConfirm = callbacks.onMkdir,
                ),
            onDismiss = callbacks.onDismissMkdir,
        )
    }

    state.renameTarget?.let { entry ->
        InputDialog(
            dialogState =
                InputDialogState(
                    title = stringResource(R.string.sftp_rename),
                    placeholder = stringResource(R.string.sftp_new_name_placeholder),
                    initialValue = entry.name,
                    confirmLabel = stringResource(R.string.sftp_rename),
                    onConfirm = { newName ->
                        if (newName != entry.name) {
                            callbacks.onRename(entry.path, newName)
                        } else {
                            callbacks.onDismissRename()
                        }
                    },
                ),
            onDismiss = callbacks.onDismissRename,
        )
    }

    state.deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = callbacks.onDismissDelete,
            title = { Text(stringResource(R.string.sftp_delete)) },
            text = { Text(stringResource(R.string.sftp_delete_entry_message, entry.name)) },
            confirmButton = {
                TextButton(onClick = { callbacks.onDelete(entry.path) }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissDelete) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (state.showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = callbacks.onDismissBulkDelete,
            title = { Text(stringResource(R.string.sftp_delete_selected)) },
            text = { Text(stringResource(R.string.sftp_delete_selected_message, state.selectedCount)) },
            confirmButton = {
                TextButton(onClick = callbacks.onDeleteSelected) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissBulkDelete) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private data class InputDialogState(
    val title: String,
    val placeholder: String,
    val confirmLabel: String,
    val initialValue: String = "",
    val onConfirm: (String) -> Unit,
)

@Composable
private fun InputDialog(
    dialogState: InputDialogState,
    onDismiss: () -> Unit,
) {
    var text by remember(dialogState.initialValue) { mutableStateOf(dialogState.initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogState.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(dialogState.placeholder) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { dialogState.onConfirm(text) }) { Text(dialogState.confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun Breadcrumbs(
    path: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = path.split("/").filter { it.isNotEmpty() }

    LazyRow(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TextButton(onClick = { onNavigate("/") }) {
                Text("/", style = MaterialTheme.typography.labelLarge)
            }
        }
        itemsIndexed(segments) { index, segment ->
            Icon(
                Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val fullPath = "/" + segments.take(index + 1).joinToString("/")
            TextButton(onClick = { onNavigate(fullPath) }) {
                Text(segment, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: RemoteEntry,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint =
                if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!entry.isDirectory) {
                    Text(
                        text = formatFileSize(entry.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.modifiedEpochSec > 0) {
                    Text(
                        text = dateFormat.format(Date(entry.modifiedEpochSec * 1000)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    },
                modifier = Modifier.size(20.dp),
            )
        } else if (!entry.isDirectory) {
            Icon(
                Icons.Default.CloudDownload,
                contentDescription = stringResource(R.string.sftp_download_icon_description),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
