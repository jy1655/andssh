package com.opencode.sshterminal.ui.sftp

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.R
import com.opencode.sshterminal.data.ConnectionProfile
import com.opencode.sshterminal.data.ConnectionRepository
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.session.JumpCredential
import com.opencode.sshterminal.session.toConnectRequest
import com.opencode.sshterminal.sftp.RemoteEntry
import com.opencode.sshterminal.sftp.SftpChannelAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SftpUiState(
    val entries: List<RemoteEntry> = emptyList(),
    val remotePath: String = ".",
    val status: String = "",
    val busy: Boolean = false,
    val transferProgress: Float = -1f,
)

@HiltViewModel
class SftpBrowserViewModel
    @Inject
    constructor(
        private val sftpAdapter: SftpChannelAdapter,
        private val connectionRepository: ConnectionRepository,
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val context: Context,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val connectionId: String = savedStateHandle["connectionId"] ?: ""

        private val _uiState = MutableStateFlow(SftpUiState())
        val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val profile = connectionRepository.get(connectionId) ?: return@launch
                val identity = profile.identityId?.let { identityId -> connectionRepository.getIdentity(identityId) }
                val proxyJumpCredentials = resolveProxyJumpCredentials(profile)
                val keepaliveInterval = settingsRepository.sshKeepaliveIntervalSeconds.first()
                val request =
                    profile.toConnectRequest(
                        context = context,
                        cols = DEFAULT_SFTP_COLS,
                        rows = DEFAULT_SFTP_ROWS,
                        keepaliveIntervalSeconds = keepaliveInterval,
                        identity = identity,
                        proxyJumpCredentials = proxyJumpCredentials,
                    )
                _uiState.value =
                    _uiState.value.copy(
                        busy = true,
                        status = context.getString(R.string.sftp_status_connecting),
                    )
                runCatching {
                    sftpAdapter.connect(request)
                    list()
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_connection_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            sftpAdapter.close()
        }

        fun list(path: String = _uiState.value.remotePath) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(busy = true, remotePath = path)
                runCatching {
                    val entries =
                        sftpAdapter.list(path)
                            .sortedWith(
                                compareByDescending<RemoteEntry> { it.isDirectory }
                                    .thenBy { it.name.lowercase() },
                            )
                    _uiState.value =
                        _uiState.value.copy(
                            entries = entries,
                            status = context.getString(R.string.sftp_status_items, entries.size),
                            busy = false,
                        )
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_list_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        fun navigateTo(path: String) = list(path)

        fun downloadToStream(
            remotePath: String,
            uri: Uri,
        ) {
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        busy = true,
                        status = context.getString(R.string.sftp_status_downloading),
                        transferProgress = 0f,
                    )
                runCatching {
                    val output =
                        context.contentResolver.openOutputStream(uri)
                            ?: error("Cannot open output stream for $uri")
                    output.use { os ->
                        sftpAdapter.downloadStream(remotePath, os) { transferred, total ->
                            if (total > 0) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        transferProgress = transferred.toFloat() / total,
                                    )
                            }
                        }
                    }
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_download_complete),
                            busy = false,
                            transferProgress = -1f,
                        )
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_download_failed, t.message),
                            busy = false,
                            transferProgress = -1f,
                        )
                }
            }
        }

        fun uploadFromUri(
            uri: Uri,
            remoteName: String,
        ) {
            viewModelScope.launch {
                if (!isValidRemoteName(remoteName)) {
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_invalid_remote_name),
                            busy = false,
                            transferProgress = -1f,
                        )
                    return@launch
                }
                _uiState.value =
                    _uiState.value.copy(
                        busy = true,
                        status = context.getString(R.string.sftp_status_uploading),
                        transferProgress = 0f,
                    )
                runCatching {
                    val fd =
                        context.contentResolver.openAssetFileDescriptor(uri, "r")
                            ?: error("Cannot open $uri")
                    val totalBytes = fd.length
                    fd.createInputStream().use { input ->
                        val remotePath = resolveRemoteChildPath(remoteName)
                        sftpAdapter.uploadStream(input, remotePath, totalBytes) { transferred, total ->
                            if (total > 0) {
                                _uiState.value =
                                    _uiState.value.copy(
                                        transferProgress = transferred.toFloat() / total,
                                    )
                            }
                        }
                    }
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_upload_complete),
                            busy = false,
                            transferProgress = -1f,
                        )
                    list()
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_upload_failed, t.message),
                            busy = false,
                            transferProgress = -1f,
                        )
                }
            }
        }

        fun mkdir(name: String) {
            viewModelScope.launch {
                if (!isValidRemoteName(name)) {
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_invalid_directory_name),
                            busy = false,
                        )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(busy = true)
                runCatching {
                    sftpAdapter.mkdir(resolveRemoteChildPath(name))
                    list()
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_mkdir_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        fun rm(remotePath: String) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(busy = true)
                runCatching {
                    sftpAdapter.rm(remotePath)
                    list()
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_delete_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        fun rmMany(remotePaths: List<String>) {
            if (remotePaths.isEmpty()) return
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(busy = true)
                var failureCount = 0
                remotePaths.forEach { remotePath ->
                    runCatching { sftpAdapter.rm(remotePath) }
                        .onFailure { failureCount += 1 }
                }
                val currentPath = _uiState.value.remotePath
                runCatching {
                    val entries =
                        sftpAdapter.list(currentPath)
                            .sortedWith(
                                compareByDescending<RemoteEntry> { it.isDirectory }
                                    .thenBy { it.name.lowercase() },
                            )
                    val status =
                        if (failureCount == 0) {
                            context.getString(R.string.sftp_status_bulk_delete_complete, remotePaths.size)
                        } else {
                            context.getString(
                                R.string.sftp_status_bulk_delete_partial,
                                remotePaths.size - failureCount,
                                failureCount,
                            )
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            entries = entries,
                            status = status,
                            busy = false,
                        )
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_delete_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        fun rename(
            oldPath: String,
            newName: String,
        ) {
            viewModelScope.launch {
                if (!isValidRemoteName(newName)) {
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_invalid_new_name),
                            busy = false,
                        )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(busy = true)
                runCatching {
                    val parent = oldPath.substringBeforeLast('/')
                    sftpAdapter.rename(oldPath, "$parent/$newName")
                    list()
                }.onFailure { t ->
                    _uiState.value =
                        _uiState.value.copy(
                            status = context.getString(R.string.sftp_status_rename_failed, t.message),
                            busy = false,
                        )
                }
            }
        }

        private fun resolveRemoteChildPath(name: String): String {
            val currentDir = _uiState.value.remotePath.trimEnd('/')
            return "$currentDir/$name"
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

        companion object {
            private const val DEFAULT_SFTP_COLS = 80
            private const val DEFAULT_SFTP_ROWS = 24
        }
    }

internal fun isValidRemoteName(name: String): Boolean {
    return name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name
}
