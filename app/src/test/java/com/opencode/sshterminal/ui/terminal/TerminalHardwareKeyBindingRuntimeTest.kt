package com.opencode.sshterminal.ui.terminal

import android.view.KeyEvent
import com.opencode.sshterminal.data.TerminalHardwareKeyAction
import com.opencode.sshterminal.data.TerminalHardwareKeyBinding
import com.opencode.sshterminal.data.TerminalHardwareKeyCombo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalHardwareKeyBindingRuntimeTest {
    @Test
    fun `resolve finds exact modifier match`() {
        val bindings =
            listOf(
                TerminalHardwareKeyBinding(
                    combo = TerminalHardwareKeyCombo(key = "H", ctrl = true),
                    action = TerminalHardwareKeyAction.BACKSPACE,
                ),
            )

        assertEquals(
            TerminalHardwareKeyAction.BACKSPACE,
            resolveTerminalHardwareKeyAction(
                bindings = bindings,
                key = "H",
                ctrl = true,
                alt = false,
                shift = false,
                meta = false,
            ),
        )
        assertNull(
            resolveTerminalHardwareKeyAction(
                bindings = bindings,
                key = "H",
                ctrl = true,
                alt = true,
                shift = false,
                meta = false,
            ),
        )
    }

    @Test
    fun `mapAndroidKeyCodeToHardwareKeyToken maps common keys`() {
        assertEquals("A", mapAndroidKeyCodeToHardwareKeyToken(KeyEvent.KEYCODE_A))
        assertEquals("9", mapAndroidKeyCodeToHardwareKeyToken(KeyEvent.KEYCODE_9))
        assertEquals("ENTER", mapAndroidKeyCodeToHardwareKeyToken(KeyEvent.KEYCODE_ENTER))
        assertEquals("PAGE_UP", mapAndroidKeyCodeToHardwareKeyToken(KeyEvent.KEYCODE_PAGE_UP))
    }
}
