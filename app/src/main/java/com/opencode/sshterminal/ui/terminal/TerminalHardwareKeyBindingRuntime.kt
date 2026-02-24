package com.opencode.sshterminal.ui.terminal

import android.view.KeyEvent
import com.opencode.sshterminal.data.TerminalHardwareKeyAction
import com.opencode.sshterminal.data.TerminalHardwareKeyBinding

@Suppress("LongParameterList")
internal fun resolveTerminalHardwareKeyAction(
    bindings: List<TerminalHardwareKeyBinding>,
    key: String,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    meta: Boolean,
): TerminalHardwareKeyAction? {
    return bindings.firstOrNull { binding ->
        binding.combo.key == key &&
            binding.combo.ctrl == ctrl &&
            binding.combo.alt == alt &&
            binding.combo.shift == shift &&
            binding.combo.meta == meta
    }?.action
}

@Suppress("CyclomaticComplexMethod")
internal fun mapAndroidKeyCodeToHardwareKeyToken(keyCode: Int): String? {
    return when (keyCode) {
        KeyEvent.KEYCODE_TAB -> "TAB"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "ENTER"
        KeyEvent.KEYCODE_SPACE -> "SPACE"
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> "BACKSPACE"
        KeyEvent.KEYCODE_ESCAPE -> "ESC"
        KeyEvent.KEYCODE_DPAD_UP -> "UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        KeyEvent.KEYCODE_PAGE_UP -> "PAGE_UP"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PAGE_DOWN"
        KeyEvent.KEYCODE_LEFT_BRACKET -> "["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
        KeyEvent.KEYCODE_SEMICOLON -> ";"
        KeyEvent.KEYCODE_COMMA -> ","
        KeyEvent.KEYCODE_PERIOD -> "."
        KeyEvent.KEYCODE_SLASH -> "/"
        KeyEvent.KEYCODE_MINUS -> "-"
        KeyEvent.KEYCODE_EQUALS -> "="
        KeyEvent.KEYCODE_GRAVE -> "`"
        KeyEvent.KEYCODE_APOSTROPHE -> "'"
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
            val offset = keyCode - KeyEvent.KEYCODE_A
            ('A'.code + offset).toChar().toString()
        }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            val offset = keyCode - KeyEvent.KEYCODE_0
            ('0'.code + offset).toChar().toString()
        }
        else -> null
    }
}
