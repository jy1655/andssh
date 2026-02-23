package com.opencode.sshterminal.terminal

import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class TermuxTerminalBridge(
    cols: Int = 120,
    rows: Int = 40,
    transcriptRows: Int = 2000,
    private val onWriteToSsh: (ByteArray) -> Unit,
    private val onBellReceived: () -> Unit = {},
) {
    private val lock = ReentrantReadWriteLock()
    private val _renderVersion = MutableStateFlow(0L)
    val renderVersion: StateFlow<Long> = _renderVersion.asStateFlow()

    private val output =
        object : TerminalOutput() {
            override fun write(
                data: ByteArray,
                offset: Int,
                count: Int,
            ) {
                onWriteToSsh(data.copyOfRange(offset, offset + count))
            }

            override fun titleChanged(
                oldTitle: String?,
                newTitle: String?,
            ) = Unit

            override fun onCopyTextToClipboard(text: String?) = Unit

            override fun onPasteTextFromClipboard() = Unit

            override fun onBell() {
                onBellReceived()
            }

            override fun onColorsChanged() = Unit
        }

    private val sessionClient =
        BridgeTerminalSessionClient(
            Runnable { _renderVersion.value++ },
            Runnable { onBellReceived() },
        )

    val emulator: TerminalEmulator = TerminalEmulator(output, cols, rows, transcriptRows, sessionClient)

    fun feed(bytes: ByteArray) =
        lock.write {
            emulator.append(bytes, bytes.size)
            _renderVersion.value++
        }

    fun resize(
        cols: Int,
        rows: Int,
    ) = lock.write {
        emulator.resize(cols, rows)
        _renderVersion.value++
    }

    fun reset() =
        lock.write {
            emulator.reset()
            _renderVersion.value++
        }

    fun <T> withReadLock(block: TermuxTerminalBridge.() -> T): T = lock.read { block() }

    val screen: TerminalBuffer get() = emulator.screen
    val cursorRow: Int get() = emulator.cursorRow
    val cursorCol: Int get() = emulator.cursorCol
    val termRows: Int get() = emulator.mRows
    val termCols: Int get() = emulator.mColumns
}
