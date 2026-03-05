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

internal enum class TerminalTextInputApplyMode(val id: String) {
    REALTIME("realtime"),
    ON_SEND("on_send"),
    ;

    companion object {
        fun fromId(id: String): TerminalTextInputApplyMode {
            return entries.firstOrNull { mode -> mode.id == id } ?: REALTIME
        }
    }
}

@Composable
internal fun rememberTerminalInputController(
    onSendBytes: (ByteArray) -> Unit,
    onSubmitCommand: (String) -> Unit,
    directModeEnabled: Boolean = false,
    textInputApplyMode: TerminalTextInputApplyMode = TerminalTextInputApplyMode.REALTIME,
): TerminalInputController {
    val controller =
        remember(onSendBytes, onSubmitCommand) {
            TerminalInputController(onSendBytes, onSubmitCommand)
        }
    controller.directModeEnabled = directModeEnabled
    controller.textInputApplyMode = textInputApplyMode
    return controller
}

@Suppress("TooManyFunctions")
internal class TerminalInputController(
    private val onSendBytes: (ByteArray) -> Unit,
    private val onSubmitCommand: (String) -> Unit,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    var textFieldValue by mutableStateOf(TextFieldValue())
        private set

    var ctrlArmed by mutableStateOf(false)
        private set

    var altArmed by mutableStateOf(false)
        private set

    var directModeEnabled by mutableStateOf(false)

    var textInputApplyMode by mutableStateOf(TerminalTextInputApplyMode.REALTIME)

    val isComposing: Boolean get() = textFieldValue.composition != null

    val composingText: String
        get() {
            val range = textFieldValue.composition ?: return ""
            val composition = textFieldValue.text.safeSubstring(range) ?: return ""
            val codePointCount = composition.codePointCount(0, composition.length)
            if (composition.isBlank() || codePointCount != 1 || !composition.containsHangul()) {
                return ""
            }
            return composition
        }

    private var lastCommittedText: String = ""
    private var pendingPostSubmitCommit: String? = null
    private var pendingPostSubmitDeadlineMillis: Long = 0L

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
        if (shouldIgnorePostSubmitCommit(newValue, newCommitted)) {
            return
        }
        if (textInputApplyMode == TerminalTextInputApplyMode.ON_SEND) {
            lastCommittedText = newCommitted
            textFieldValue = newValue
            return
        }
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
        val submittedCommand = textFieldValue.text.trimEnd('\r', '\n')
        if (submittedCommand.isNotBlank()) {
            onSubmitCommand(submittedCommand)
        }
        val flushedComposingText =
            if (textInputApplyMode == TerminalTextInputApplyMode.ON_SEND) {
                sendBufferedInputForOnSendMode()
            } else {
                flushComposing()
            }
        if (flushedComposingText.isNotEmpty()) {
            pendingPostSubmitCommit = flushedComposingText
            pendingPostSubmitDeadlineMillis = nowMillis() + POST_SUBMIT_COMMIT_IGNORE_WINDOW_MS
        } else {
            clearPendingPostSubmitCommit()
        }
        onSendBytes(byteArrayOf('\r'.code.toByte()))
        clearInput()
    }

    private fun sendBufferedInputForOnSendMode(): String {
        val composition = textFieldValue.composition
        if (composition == null) {
            val plain = textFieldValue.text
            if (plain.isNotEmpty()) {
                sendTypedText(plain)
            }
            return ""
        }

        val text = textFieldValue.text
        val prefix = text.substring(0, composition.start)
        val composing = text.substring(composition.start, composition.end)
        val suffix = text.substring(composition.end)
        if (prefix.isNotEmpty()) {
            sendTypedText(prefix)
        }
        if (composing.isNotEmpty()) {
            sendTypedText(composing)
        }
        if (suffix.isNotEmpty()) {
            sendTypedText(suffix)
        }
        return composing
    }

    private fun sendTypedText(text: String) {
        val payload = buildPayload(text, ctrlArmed, altArmed)
        if (payload.isNotEmpty()) {
            onSendBytes(payload)
            resetModifiers()
        }
    }

    private fun flushComposing(): String {
        val composition = textFieldValue.composition ?: return ""
        val composingText = textFieldValue.text.substring(composition.start, composition.end)
        if (composingText.isNotEmpty()) {
            sendTypedText(composingText)
        }
        return composingText
    }

    private fun clearInput() {
        textFieldValue = TextFieldValue()
        lastCommittedText = ""
    }

    private fun resetModifiers() {
        ctrlArmed = false
        altArmed = false
    }

    private fun clearPendingPostSubmitCommit() {
        pendingPostSubmitCommit = null
        pendingPostSubmitDeadlineMillis = 0L
    }

    private fun shouldIgnorePostSubmitCommit(
        newValue: TextFieldValue,
        newCommitted: String,
    ): Boolean {
        val expected = pendingPostSubmitCommit ?: return false
        val shouldIgnore =
            if (nowMillis() > pendingPostSubmitDeadlineMillis) {
                false
            } else {
                newValue.composition == null &&
                    textFieldValue.text.isEmpty() &&
                    lastCommittedText.isEmpty() &&
                    newCommitted == expected
            }
        clearPendingPostSubmitCommit()
        return shouldIgnore
    }

    companion object {
        private const val POST_SUBMIT_COMMIT_IGNORE_WINDOW_MS = 300L
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

private fun String.safeSubstring(range: androidx.compose.ui.text.TextRange): String? {
    if (range.start < 0 || range.end > length || range.start >= range.end) return null
    return substring(range.start, range.end)
}

private fun String.containsHangul(): Boolean {
    return any { char ->
        val code = char.code
        code in 0xAC00..0xD7A3 || // Hangul syllables
            code in 0x1100..0x11FF || // Hangul Jamo
            code in 0x3130..0x318F || // Hangul Compatibility Jamo
            code in 0xA960..0xA97F || // Hangul Jamo Extended-A
            code in 0xD7B0..0xD7FF // Hangul Jamo Extended-B
    }
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
