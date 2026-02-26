package com.opencode.sshterminal.data

enum class TerminalShortcutLayoutItem(
    val id: String,
) {
    MENU("menu"),
    SNIPPETS("snippets"),
    HISTORY("history"),
    ESC("esc"),
    TAB("tab"),
    CTRL("ctrl"),
    ALT("alt"),
    ARROW_UP("arrow_up"),
    ARROW_DOWN("arrow_down"),
    ARROW_LEFT("arrow_left"),
    ARROW_RIGHT("arrow_right"),
    HOME("home"),
    END("end"),
    INSERT("insert"),
    DELETE("delete"),
    BACKSPACE("backspace"),
    PAGE_UP("page_up"),
    PAGE_DOWN("page_down"),
    F1("f1"),
    F2("f2"),
    F3("f3"),
    F4("f4"),
    F5("f5"),
    F6("f6"),
    F7("f7"),
    F8("f8"),
    F9("f9"),
    F10("f10"),
    F11("f11"),
    F12("f12"),
    CTRL_C("ctrl_c"),
    CTRL_D("ctrl_d"),
    PASTE("paste"),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): TerminalShortcutLayoutItem? = byId[id]
    }
}

val DEFAULT_TERMINAL_SHORTCUT_LAYOUT_ITEMS: List<TerminalShortcutLayoutItem> =
    listOf(
        TerminalShortcutLayoutItem.MENU,
        TerminalShortcutLayoutItem.SNIPPETS,
        TerminalShortcutLayoutItem.HISTORY,
        TerminalShortcutLayoutItem.ESC,
        TerminalShortcutLayoutItem.TAB,
        TerminalShortcutLayoutItem.CTRL,
        TerminalShortcutLayoutItem.ALT,
        TerminalShortcutLayoutItem.ARROW_UP,
        TerminalShortcutLayoutItem.ARROW_DOWN,
        TerminalShortcutLayoutItem.ARROW_LEFT,
        TerminalShortcutLayoutItem.ARROW_RIGHT,
        TerminalShortcutLayoutItem.HOME,
        TerminalShortcutLayoutItem.END,
        TerminalShortcutLayoutItem.INSERT,
        TerminalShortcutLayoutItem.DELETE,
        TerminalShortcutLayoutItem.BACKSPACE,
        TerminalShortcutLayoutItem.PAGE_UP,
        TerminalShortcutLayoutItem.PAGE_DOWN,
        TerminalShortcutLayoutItem.F1,
        TerminalShortcutLayoutItem.F2,
        TerminalShortcutLayoutItem.F3,
        TerminalShortcutLayoutItem.F4,
        TerminalShortcutLayoutItem.F5,
        TerminalShortcutLayoutItem.F6,
        TerminalShortcutLayoutItem.F7,
        TerminalShortcutLayoutItem.F8,
        TerminalShortcutLayoutItem.F9,
        TerminalShortcutLayoutItem.F10,
        TerminalShortcutLayoutItem.F11,
        TerminalShortcutLayoutItem.F12,
        TerminalShortcutLayoutItem.CTRL_C,
        TerminalShortcutLayoutItem.CTRL_D,
        TerminalShortcutLayoutItem.PASTE,
    )

val DEFAULT_TERMINAL_SHORTCUT_LAYOUT: String =
    serializeTerminalShortcutLayout(DEFAULT_TERMINAL_SHORTCUT_LAYOUT_ITEMS)

fun parseTerminalShortcutLayout(layout: String?): List<TerminalShortcutLayoutItem> {
    val parsedItems =
        layout.orEmpty()
            .split(',')
            .mapNotNull { token -> TerminalShortcutLayoutItem.fromId(token.trim()) }
            .distinct()
    return if (parsedItems.isEmpty()) {
        DEFAULT_TERMINAL_SHORTCUT_LAYOUT_ITEMS
    } else {
        parsedItems
    }
}

fun serializeTerminalShortcutLayout(items: List<TerminalShortcutLayoutItem>): String {
    val normalized = items.distinct()
    return if (normalized.isEmpty()) {
        DEFAULT_TERMINAL_SHORTCUT_LAYOUT
    } else {
        normalized.joinToString(separator = ",") { item -> item.id }
    }
}
