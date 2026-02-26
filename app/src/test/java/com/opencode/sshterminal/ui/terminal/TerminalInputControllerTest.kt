package com.opencode.sshterminal.ui.terminal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalInputControllerTest {
    @Test
    fun `submitInput reports typed command`() {
        val sent = mutableListOf<ByteArray>()
        var submittedCommand: String? = null
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = { command -> submittedCommand = command },
            )

        controller.onTextFieldValueChange(TextFieldValue("ls -la"))
        sent.clear()

        controller.submitInput()

        assertEquals("ls -la", submittedCommand)
        assertTrue(sent.single().contentEquals(byteArrayOf('\r'.code.toByte())))
    }

    @Test
    fun `submitInput skips blank command`() {
        val sent = mutableListOf<ByteArray>()
        var submittedCommand: String? = null
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = { command -> submittedCommand = command },
            )

        controller.onTextFieldValueChange(TextFieldValue("   "))
        sent.clear()

        controller.submitInput()

        assertNull(submittedCommand)
        assertTrue(sent.single().contentEquals(byteArrayOf('\r'.code.toByte())))
    }

    @Test
    fun `direct mode keeps committed text for subsequent edits`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        controller.directModeEnabled = true

        controller.onTextFieldValueChange(TextFieldValue("a"))

        assertEquals("a", controller.textFieldValue.text)
        assertTrue(sent.single().contentEquals("a".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `direct mode preserves text during active composition`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        controller.directModeEnabled = true

        controller.onTextFieldValueChange(
            TextFieldValue(text = "\uAC00", composition = TextRange(0, 1)),
        )

        assertEquals("\uAC00", controller.textFieldValue.text)
        assertTrue(controller.isComposing)
        assertEquals("\uAC00", controller.composingText)
    }

    @Test
    fun `direct mode keeps text after composition commit`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        controller.directModeEnabled = true

        // Start composing
        controller.onTextFieldValueChange(
            TextFieldValue(text = "\uAC00", composition = TextRange(0, 1)),
        )
        assertTrue(controller.isComposing)

        // Commit composition (composition becomes null)
        controller.onTextFieldValueChange(TextFieldValue("\uAC00"))

        assertFalse(controller.isComposing)
        assertEquals("\uAC00", controller.textFieldValue.text)
    }

    @Test
    fun `direct mode submitInput records command`() {
        val sent = mutableListOf<ByteArray>()
        var submittedCommand: String? = null
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = { command -> submittedCommand = command },
            )
        controller.directModeEnabled = true

        controller.onTextFieldValueChange(TextFieldValue("echo hello"))
        sent.clear()

        controller.submitInput()

        assertEquals("echo hello", submittedCommand)
        assertTrue(sent.single().contentEquals(byteArrayOf('\r'.code.toByte())))
    }

    @Test
    fun `direct mode translates committed deletion to backspace byte`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        controller.directModeEnabled = true

        controller.onTextFieldValueChange(TextFieldValue("ab"))
        sent.clear()

        controller.onTextFieldValueChange(TextFieldValue("a"))

        assertTrue(sent.single().contentEquals(byteArrayOf(0x7F)))
    }

    @Test
    fun `function shortcuts send expected ansi sequences`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        val expected =
            listOf(
                TerminalShortcut.F1 to "\u001BOP",
                TerminalShortcut.F2 to "\u001BOQ",
                TerminalShortcut.F3 to "\u001BOR",
                TerminalShortcut.F4 to "\u001BOS",
                TerminalShortcut.F5 to "\u001B[15~",
                TerminalShortcut.F6 to "\u001B[17~",
                TerminalShortcut.F7 to "\u001B[18~",
                TerminalShortcut.F8 to "\u001B[19~",
                TerminalShortcut.F9 to "\u001B[20~",
                TerminalShortcut.F10 to "\u001B[21~",
                TerminalShortcut.F11 to "\u001B[23~",
                TerminalShortcut.F12 to "\u001B[24~",
            )

        expected.forEach { (shortcut, sequence) ->
            sent.clear()
            controller.onShortcut(shortcut)
            assertArrayEquals(sequence.toByteArray(Charsets.UTF_8), sent.single())
        }
    }

    @Test
    fun `navigation shortcuts send expected ansi sequences`() {
        val sent = mutableListOf<ByteArray>()
        val controller =
            TerminalInputController(
                onSendBytes = { payload -> sent.add(payload) },
                onSubmitCommand = {},
            )
        val expected =
            listOf(
                TerminalShortcut.HOME to "\u001B[H",
                TerminalShortcut.END to "\u001B[F",
                TerminalShortcut.INSERT to "\u001B[2~",
                TerminalShortcut.DELETE to "\u001B[3~",
            )

        expected.forEach { (shortcut, sequence) ->
            sent.clear()
            controller.onShortcut(shortcut)
            assertArrayEquals(sequence.toByteArray(Charsets.UTF_8), sent.single())
        }
    }
}
