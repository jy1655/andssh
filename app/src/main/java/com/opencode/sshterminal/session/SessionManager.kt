package com.opencode.sshterminal.session

import android.util.Log
import com.opencode.sshterminal.di.ApplicationScope
import com.opencode.sshterminal.ssh.HostKeyChangedException
import com.opencode.sshterminal.ssh.SshClient
import com.opencode.sshterminal.ssh.SshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val sshClient: SshClient,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _snapshot = MutableStateFlow(
        SessionSnapshot(
            sessionId = SessionId(),
            state = SessionState.IDLE,
            host = "",
            port = 22,
            username = ""
        )
    )
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()

    private val _outputBytes = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val outputBytes: SharedFlow<ByteArray> = _outputBytes.asSharedFlow()

    private var activeSession: SshSession? = null
    private var pendingHostKeyRequest: ConnectRequest? = null

    fun connect(request: ConnectRequest) {
        scope.launch {
            _snapshot.value = SessionSnapshot(
                sessionId = SessionId(),
                state = SessionState.CONNECTING,
                host = request.host,
                port = request.port,
                username = request.username
            )

            runCatching {
                runCatching { activeSession?.close() }
                val session = sshClient.connect(request)
                activeSession = session
                session.openPtyShell(request.termType, request.cols, request.rows)
                _snapshot.value = _snapshot.value.copy(state = SessionState.CONNECTED, error = null)

                session.readLoop { bytes ->
                    _outputBytes.tryEmit(bytes)
                }
                _snapshot.value = _snapshot.value.copy(state = SessionState.DISCONNECTED)
            }.onFailure { err ->
                Log.e(TAG, "Connection failed", err)
                val hostKeyAlert = toHostKeyAlert(err, request)
                if (hostKeyAlert != null) {
                    pendingHostKeyRequest = request
                    _snapshot.value = _snapshot.value.copy(
                        state = SessionState.FAILED,
                        error = hostKeyAlert.message,
                        hostKeyAlert = hostKeyAlert
                    )
                } else {
                    _snapshot.value = _snapshot.value.copy(
                        state = SessionState.FAILED,
                        error = err.message ?: "unknown error"
                    )
                }
            }
        }
    }

    fun sendInput(bytes: ByteArray) {
        scope.launch {
            activeSession?.write(bytes)
        }
    }

    fun resize(cols: Int, rows: Int) {
        scope.launch {
            activeSession?.windowChange(cols, rows)
        }
    }

    fun disconnect() {
        scope.launch {
            runCatching { activeSession?.close() }
            activeSession = null
            _snapshot.value = _snapshot.value.copy(state = SessionState.DISCONNECTED)
        }
    }

    fun dismissHostKeyAlert() {
        pendingHostKeyRequest = null
        _snapshot.value = _snapshot.value.copy(hostKeyAlert = null)
    }

    fun trustHostKeyOnce() {
        val request = pendingHostKeyRequest ?: return
        pendingHostKeyRequest = null
        _snapshot.value = _snapshot.value.copy(hostKeyAlert = null, error = null)
        connect(request.copy(hostKeyPolicy = HostKeyPolicy.TRUST_ONCE))
    }

    fun updateKnownHostsAndReconnect() {
        val request = pendingHostKeyRequest ?: return
        pendingHostKeyRequest = null
        _snapshot.value = _snapshot.value.copy(hostKeyAlert = null, error = null)
        connect(request.copy(hostKeyPolicy = HostKeyPolicy.UPDATE_KNOWN_HOSTS))
    }

    val isConnected: Boolean
        get() = _snapshot.value.state == SessionState.CONNECTED

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
