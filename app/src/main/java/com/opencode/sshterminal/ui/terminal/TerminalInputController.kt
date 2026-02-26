package com.opencode.sshterminal.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue

internal enum class TerminalShortcut {
    ESC,
    TAB,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    HOME,
    END,
    INSERT,
    DELETE,
    BACKSPACE,
    CTRL_C,
    CTRL_D,
    F1,
    F2,
    F3,
    F4,
    F5,
    F6,
    F7,
    F8,
    F9,
    F10,
    F11,
    F12,
}

@Composable
internal fun rememberTerminalInputController(
    onSendBytes: (ByteArray) -> Unit,
    onSubmitCommand: (String) -> Unit,
    directModeEnabled: Boolean = false,
): TerminalInputController {
    val controller =
        remember(onSendBytes, onSubmitCommand) {
            TerminalInputController(onSendBytes, onSubmitCommand)
        }
    controller.directModeEnabled = directModeEnabled
    return controller
}

internal class TerminalInputController(
    private val onSendBytes: (ByteArray) -> Unit,
    private val onSubmitCommand: (String) -> Unit,
) {
    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    var ctrlArmed by mutableStateOf(false)
        private set

    var altArmed by mutableStateOf(false)
        private set

    var directModeEnabled by mutableStateOf(false)

    val isComposing: Boolean get() = textFieldValue.composition != null

    val composingText: String
        get() {
            val range = textFieldValue.composition ?: return ""
            return textFieldValue.text.substring(range.start, range.end)
        }

    private var lastCommittedText: String = ""

    fun toggleCtrl() {
        ctrlArmed = !ctrlArmed
    }

    fun toggleAlt() {
        altArmed = !altArmed
    }

    @Suppress("CyclomaticComplexMethod")
    fun onShortcut(shortcut: TerminalShortcut) {
        when (shortcut) {
            TerminalShortcut.ESC -> {
                onSendBytes(byteArrayOf(0x1B))
                resetModifiers()
            }
            TerminalShortcut.TAB -> {
                onSendBytes(byteArrayOf('\t'.code.toByte()))
                resetModifiers()
            }
            TerminalShortcut.ARROW_UP -> onSendBytes("\u001B[A".toByteArray(Charsets.UTF_8))
            TerminalShortcut.ARROW_DOWN -> onSendBytes("\u001B[B".toByteArray(Charsets.UTF_8))
            TerminalShortcut.ARROW_LEFT -> onSendBytes("\u001B[D".toByteArray(Charsets.UTF_8))
            TerminalShortcut.ARROW_RIGHT -> onSendBytes("\u001B[C".toByteArray(Charsets.UTF_8))
            TerminalShortcut.HOME -> onSendBytes("\u001B[H".toByteArray(Charsets.UTF_8))
            TerminalShortcut.END -> onSendBytes("\u001B[F".toByteArray(Charsets.UTF_8))
            TerminalShortcut.INSERT -> onSendBytes("\u001B[2~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.DELETE -> onSendBytes("\u001B[3~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.BACKSPACE -> onSendBytes(byteArrayOf(0x7F))
            TerminalShortcut.CTRL_C -> onSendBytes(byteArrayOf(0x03))
            TerminalShortcut.CTRL_D -> onSendBytes(byteArrayOf(0x04))
            TerminalShortcut.F1 -> onSendBytes("\u001BOP".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F2 -> onSendBytes("\u001BOQ".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F3 -> onSendBytes("\u001BOR".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F4 -> onSendBytes("\u001BOS".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F5 -> onSendBytes("\u001B[15~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F6 -> onSendBytes("\u001B[17~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F7 -> onSendBytes("\u001B[18~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F8 -> onSendBytes("\u001B[19~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F9 -> onSendBytes("\u001B[20~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F10 -> onSendBytes("\u001B[21~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F11 -> onSendBytes("\u001B[23~".toByteArray(Charsets.UTF_8))
            TerminalShortcut.F12 -> onSendBytes("\u001B[24~".toByteArray(Charsets.UTF_8))
        }
    }

    fun onTextFieldValueChange(newValue: TextFieldValue) {
        val newCommitted = newValue.committedText()
        if (newCommitted != lastCommittedText) {
            lastCommittedText =
                syncCommittedText(
                    previous = lastCommittedText,
                    current = newCommitted,
                    onInserted = { inserted ->
                        if (inserted.isNotEmpty()) {
                            sendTypedText(inserted)
                        }
                    },
                    onDelete = { count -> repeat(count) { onSendBytes(byteArrayOf(0x7F)) } },
                )
        }
        textFieldValue = newValue
    }

    fun submitInput() {
        flushComposing()
        val submittedCommand = textFieldValue.text.trimEnd('\r', '\n')
        if (submittedCommand.isNotBlank()) {
            onSubmitCommand(submittedCommand)
        }
        onSendBytes(byteArrayOf('\r'.code.toByte()))
        clearInput()
    }

    private fun sendTypedText(text: String) {
        val payload = buildPayload(text, ctrlArmed, altArmed)
        if (payload.isNotEmpty()) {
            onSendBytes(payload)
            resetModifiers()
        }
    }

    private fun flushComposing() {
        val composition = textFieldValue.composition ?: return
        val composingText = textFieldValue.text.substring(composition.start, composition.end)
        if (composingText.isNotEmpty()) {
            sendTypedText(composingText)
        }
    }

    private fun clearInput() {
        textFieldValue = TextFieldValue()
        lastCommittedText = ""
    }

    private fun resetModifiers() {
        ctrlArmed = false
        altArmed = false
    }
}

private fun syncCommittedText(
    previous: String,
    current: String,
    onInserted: (String) -> Unit,
    onDelete: (Int) -> Unit,
): String {
    when {
        current.startsWith(previous) -> {
            val inserted = current.substring(previous.length)
            onInserted(inserted)
        }
        previous.startsWith(current) -> {
            onDelete(previous.length - current.length)
        }
        else -> {
            onDelete(previous.length)
            onInserted(current)
        }
    }
    return current
}

private fun TextFieldValue.committedText(): String {
    val composition = composition ?: return text
    return text.removeRange(composition.start, composition.end)
}

private fun buildPayload(
    text: String,
    ctrlArmed: Boolean,
    altArmed: Boolean,
): ByteArray {
    if (text.isEmpty() && !ctrlArmed && !altArmed) return ByteArray(0)
    val core =
        when {
            ctrlArmed && text.length == 1 -> {
                val upper = text[0].uppercaseChar().code
                byteArrayOf(if (upper in 64..95) (upper - 64).toByte() else text[0].code.toByte())
            }
            else -> text.toByteArray(Charsets.UTF_8)
        }
    return if (altArmed) byteArrayOf(0x1B) + core else core
}
