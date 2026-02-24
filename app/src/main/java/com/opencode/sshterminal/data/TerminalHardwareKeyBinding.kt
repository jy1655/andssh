package com.opencode.sshterminal.data

enum class TerminalHardwareKeyAction(
    val id: String,
) {
    ESC("ESC"),
    TAB("TAB"),
    ENTER("ENTER"),
    ARROW_UP("ARROW_UP"),
    ARROW_DOWN("ARROW_DOWN"),
    ARROW_LEFT("ARROW_LEFT"),
    ARROW_RIGHT("ARROW_RIGHT"),
    BACKSPACE("BACKSPACE"),
    CTRL_C("CTRL_C"),
    CTRL_D("CTRL_D"),
    PAGE_UP("PAGE_UP"),
    PAGE_DOWN("PAGE_DOWN"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromToken(token: String): TerminalHardwareKeyAction? {
            val normalized =
                token
                    .trim()
                    .uppercase()
                    .replace('-', '_')
                    .replace(' ', '_')
            return when (normalized) {
                "UP" -> ARROW_UP
                "DOWN" -> ARROW_DOWN
                "LEFT" -> ARROW_LEFT
                "RIGHT" -> ARROW_RIGHT
                "PGUP" -> PAGE_UP
                "PGDN" -> PAGE_DOWN
                else -> byId[normalized]
            }
        }
    }
}

data class TerminalHardwareKeyCombo(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)

data class TerminalHardwareKeyBinding(
    val combo: TerminalHardwareKeyCombo,
    val action: TerminalHardwareKeyAction,
)

const val DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS = ""

fun parseTerminalHardwareKeyBindings(config: String?): List<TerminalHardwareKeyBinding> {
    val parsed = linkedMapOf<TerminalHardwareKeyCombo, TerminalHardwareKeyBinding>()
    config.orEmpty()
        .lineSequence()
        .forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            val separator = line.indexOf('=')
            if (separator <= 0 || separator == line.lastIndex) return@forEach

            val lhs = line.substring(0, separator).trim()
            val rhs = line.substring(separator + 1).trim()
            val action = TerminalHardwareKeyAction.fromToken(rhs) ?: return@forEach
            val combo = parseTerminalHardwareKeyCombo(lhs) ?: return@forEach

            parsed -= combo
            parsed[combo] = TerminalHardwareKeyBinding(combo = combo, action = action)
        }
    return parsed.values.toList()
}

@Suppress("ReturnCount")
fun serializeTerminalHardwareKeyBindings(bindings: List<TerminalHardwareKeyBinding>): String {
    if (bindings.isEmpty()) return DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS
    val normalized = linkedMapOf<TerminalHardwareKeyCombo, TerminalHardwareKeyBinding>()
    bindings.forEach { binding ->
        val combo =
            TerminalHardwareKeyCombo(
                key = normalizeHardwareKeyToken(binding.combo.key) ?: return@forEach,
                ctrl = binding.combo.ctrl,
                alt = binding.combo.alt,
                shift = binding.combo.shift,
                meta = binding.combo.meta,
            )
        normalized -= combo
        normalized[combo] = binding.copy(combo = combo)
    }
    if (normalized.isEmpty()) return DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS
    return normalized.values.joinToString("\n") { binding ->
        val parts = mutableListOf<String>()
        if (binding.combo.ctrl) parts += "CTRL"
        if (binding.combo.alt) parts += "ALT"
        if (binding.combo.shift) parts += "SHIFT"
        if (binding.combo.meta) parts += "META"
        parts += binding.combo.key
        "${parts.joinToString("+")}=${binding.action.id}"
    }
}

@Suppress("ReturnCount")
private fun parseTerminalHardwareKeyCombo(token: String): TerminalHardwareKeyCombo? {
    val parts =
        token
            .split('+')
            .map { part -> part.trim() }
            .filter { part -> part.isNotEmpty() }
    if (parts.isEmpty()) return null
    val key = normalizeHardwareKeyToken(parts.last()) ?: return null
    var ctrl = false
    var alt = false
    var shift = false
    var meta = false
    parts.dropLast(1).forEach { modifierToken ->
        when (modifierToken.uppercase()) {
            "CTRL", "CONTROL" -> ctrl = true
            "ALT", "OPTION" -> alt = true
            "SHIFT" -> shift = true
            "META", "CMD", "COMMAND", "SUPER" -> meta = true
            else -> return null
        }
    }
    return TerminalHardwareKeyCombo(
        key = key,
        ctrl = ctrl,
        alt = alt,
        shift = shift,
        meta = meta,
    )
}

@Suppress("CyclomaticComplexMethod")
private fun normalizeHardwareKeyToken(token: String): String? {
    val normalized =
        token
            .trim()
            .uppercase()
            .replace('-', '_')
            .replace(' ', '_')
    if (normalized.isEmpty()) return null
    return when (normalized) {
        "UP", "ARROWUP" -> "UP"
        "DOWN", "ARROWDOWN" -> "DOWN"
        "LEFT", "ARROWLEFT" -> "LEFT"
        "RIGHT", "ARROWRIGHT" -> "RIGHT"
        "PGUP", "PAGEUP", "PAGE_UP" -> "PAGE_UP"
        "PGDN", "PAGEDOWN", "PAGE_DOWN" -> "PAGE_DOWN"
        "DEL", "DELETE", "BACKSPACE" -> "BACKSPACE"
        "RETURN", "ENTER" -> "ENTER"
        "ESC", "ESCAPE" -> "ESC"
        "TAB" -> "TAB"
        "SPACE", "SPACEBAR" -> "SPACE"
        "[", "]", ";", ",", ".", "/", "-", "=", "`", "'" -> normalized
        else -> {
            when {
                normalized.length == 1 && normalized[0] in ('A'..'Z') -> normalized
                normalized.length == 1 && normalized[0] in ('0'..'9') -> normalized
                else -> null
            }
        }
    }
}
