package com.opencode.sshterminal.ui.sftp

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.data.ConnectionRepository
import com.opencode.sshterminal.session.ConnectRequest
import com.opencode.sshterminal.sftp.RemoteEntry
import com.opencode.sshterminal.sftp.SftpChannelAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SftpUiState(
    val entries: List<RemoteEntry> = emptyList(),
    val remotePath: String = ".",
    val status: String = "",
    val busy: Boolean = false,
    val transferProgress: Float = -1f
)

@HiltViewModel
class SftpBrowserViewModel @Inject constructor(
    private val sftpAdapter: SftpChannelAdapter,
    private val connectionRepository: ConnectionRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val connectionId: String = savedStateHandle["connectionId"] ?: ""

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = connectionRepository.get(connectionId) ?: return@launch
            val request = ConnectRequest(
                host = profile.host,
                port = profile.port,
                username = profile.username,
                knownHostsPath = File(context.filesDir, "known_hosts").absolutePath,
                password = profile.password,
                privateKeyPath = profile.privateKeyPath,
                cols = 80,
                rows = 24
            )
            _uiState.value = _uiState.value.copy(busy = true, status = "Connecting...")
            runCatching {
                sftpAdapter.connect(request)
                list()
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "Connection failed: ${t.message}", busy = false
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
                val entries = sftpAdapter.list(path)
                    .sortedWith(
                        compareByDescending<RemoteEntry> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                _uiState.value = _uiState.value.copy(
                    entries = entries, status = "${entries.size} items", busy = false
                )
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "List failed: ${t.message}", busy = false
                )
            }
        }
    }

    fun navigateTo(path: String) = list(path)

    fun setRemotePath(path: String) {
        _uiState.value = _uiState.value.copy(remotePath = path)
    }

    fun download(remotePath: String, localPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, status = "Downloading...", transferProgress = 0f)
            runCatching {
                sftpAdapter.download(remotePath, localPath)
                _uiState.value = _uiState.value.copy(
                    status = "Downloaded to $localPath", busy = false, transferProgress = -1f
                )
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "Download failed: ${t.message}", busy = false, transferProgress = -1f
                )
            }
        }
    }

    fun downloadToStream(remotePath: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, status = "Downloading...", transferProgress = 0f)
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Cannot open output stream for $uri")
                output.use { os ->
                    sftpAdapter.downloadStream(remotePath, os) { transferred, total ->
                        if (total > 0) {
                            _uiState.value = _uiState.value.copy(
                                transferProgress = transferred.toFloat() / total
                            )
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    status = "Download complete", busy = false, transferProgress = -1f
                )
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "Download failed: ${t.message}", busy = false, transferProgress = -1f
                )
            }
        }
    }

    fun uploadFromUri(uri: Uri, remoteName: String) {
        viewModelScope.launch {
            if (!isValidRemoteName(remoteName)) {
                _uiState.value = _uiState.value.copy(status = "Invalid remote name", busy = false, transferProgress = -1f)
                return@launch
            }
            _uiState.value = _uiState.value.copy(busy = true, status = "Uploading...", transferProgress = 0f)
            runCatching {
                val fd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: error("Cannot open $uri")
                val totalBytes = fd.length
                fd.createInputStream().use { input ->
                    val currentDir = _uiState.value.remotePath.trimEnd('/')
                    val remotePath = "$currentDir/$remoteName"
                    sftpAdapter.uploadStream(input, remotePath, totalBytes) { transferred, total ->
                        if (total > 0) {
                            _uiState.value = _uiState.value.copy(
                                transferProgress = transferred.toFloat() / total
                            )
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    status = "Upload complete", busy = false, transferProgress = -1f
                )
                list()
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "Upload failed: ${t.message}", busy = false, transferProgress = -1f
                )
            }
        }
    }

    fun mkdir(name: String) {
        viewModelScope.launch {
            if (!isValidRemoteName(name)) {
                _uiState.value = _uiState.value.copy(status = "Invalid directory name", busy = false)
                return@launch
            }
            val currentDir = _uiState.value.remotePath.trimEnd('/')
            _uiState.value = _uiState.value.copy(busy = true)
            runCatching {
                sftpAdapter.mkdir("$currentDir/$name")
                list()
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "mkdir failed: ${t.message}", busy = false
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
                _uiState.value = _uiState.value.copy(
                    status = "Delete failed: ${t.message}", busy = false
                )
            }
        }
    }

    fun rename(oldPath: String, newName: String) {
        viewModelScope.launch {
            if (!isValidRemoteName(newName)) {
                _uiState.value = _uiState.value.copy(status = "Invalid new name", busy = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(busy = true)
            runCatching {
                val parent = oldPath.substringBeforeLast('/')
                sftpAdapter.rename(oldPath, "$parent/$newName")
                list()
            }.onFailure { t ->
                _uiState.value = _uiState.value.copy(
                    status = "Rename failed: ${t.message}", busy = false
                )
            }
        }
    }
}

internal fun isValidRemoteName(name: String): Boolean {
    if (name.isBlank()) return false
    if (name == "." || name == "..") return false
    return '/' !in name && '\\' !in name
}
