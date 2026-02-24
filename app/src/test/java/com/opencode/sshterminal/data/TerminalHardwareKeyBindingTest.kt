package com.opencode.sshterminal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalHardwareKeyBindingTest {
    @Test
    fun `parse supports comments aliases and normalization`() {
        val parsed =
            parseTerminalHardwareKeyBindings(
                """
                ctrl+h=backspace
                ctrl+p=up
                # ignored comment
                ALT+SHIFT+Enter=tab
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                TerminalHardwareKeyBinding(
                    combo = TerminalHardwareKeyCombo(key = "H", ctrl = true),
                    action = TerminalHardwareKeyAction.BACKSPACE,
                ),
                TerminalHardwareKeyBinding(
                    combo = TerminalHardwareKeyCombo(key = "P", ctrl = true),
                    action = TerminalHardwareKeyAction.ARROW_UP,
                ),
                TerminalHardwareKeyBinding(
                    combo = TerminalHardwareKeyCombo(key = "ENTER", alt = true, shift = true),
                    action = TerminalHardwareKeyAction.TAB,
                ),
            ),
            parsed,
        )
    }

    @Test
    fun `serialize emits canonical uppercase format`() {
        val serialized =
            serializeTerminalHardwareKeyBindings(
                listOf(
                    TerminalHardwareKeyBinding(
                        combo = TerminalHardwareKeyCombo(key = "h", ctrl = true),
                        action = TerminalHardwareKeyAction.BACKSPACE,
                    ),
                    TerminalHardwareKeyBinding(
                        combo = TerminalHardwareKeyCombo(key = "pageup", alt = true),
                        action = TerminalHardwareKeyAction.PAGE_UP,
                    ),
                ),
            )

        assertEquals(
            "CTRL+H=BACKSPACE\nALT+PAGE_UP=PAGE_UP",
            serialized,
        )
    }

    @Test
    fun `parse keeps last duplicate combo`() {
        val parsed =
            parseTerminalHardwareKeyBindings(
                """
                CTRL+H=BACKSPACE
                CTRL+H=TAB
                """.trimIndent(),
            )

        assertEquals(
            listOf(
                TerminalHardwareKeyBinding(
                    combo = TerminalHardwareKeyCombo(key = "H", ctrl = true),
                    action = TerminalHardwareKeyAction.TAB,
                ),
            ),
            parsed,
        )
    }
}
