package com.opencode.sshterminal.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val languageTag: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[LANGUAGE_TAG_KEY] ?: DEFAULT_LANGUAGE_TAG
            }

        val themePresetId: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[THEME_PRESET_KEY] ?: DEFAULT_THEME_PRESET
            }

        val clipboardTimeoutSeconds: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[CLIPBOARD_TIMEOUT_SECONDS_KEY] ?: DEFAULT_CLIPBOARD_TIMEOUT
            }

        val autoLockTimeoutSeconds: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[AUTO_LOCK_TIMEOUT_SECONDS_KEY] ?: DEFAULT_AUTO_LOCK_TIMEOUT
            }

        val terminalColorScheme: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_COLOR_SCHEME_KEY] ?: DEFAULT_TERMINAL_COLOR_SCHEME
            }

        val terminalFont: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_FONT_KEY] ?: DEFAULT_TERMINAL_FONT
            }

        val terminalFontSizeSp: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_FONT_SIZE_SP_KEY] ?: DEFAULT_TERMINAL_FONT_SIZE_SP
            }

        val sshKeepaliveIntervalSeconds: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[SSH_KEEPALIVE_INTERVAL_SECONDS_KEY] ?: DEFAULT_SSH_KEEPALIVE_INTERVAL
            }

        val sshCompressionEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[SSH_COMPRESSION_ENABLED_KEY] ?: DEFAULT_SSH_COMPRESSION_ENABLED
            }

        val screenshotProtectionEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[SCREENSHOT_PROTECTION_ENABLED_KEY] ?: DEFAULT_SCREENSHOT_PROTECTION_ENABLED
            }

        val terminalCursorStyle: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_CURSOR_STYLE_KEY] ?: DEFAULT_TERMINAL_CURSOR_STYLE
            }

        val terminalShortcutLayout: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_SHORTCUT_LAYOUT_KEY] ?: DEFAULT_TERMINAL_SHORTCUT_LAYOUT
            }

        val terminalHardwareKeyBindings: Flow<String> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_HARDWARE_KEY_BINDINGS_KEY] ?: DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS
            }

        val terminalInputMode: Flow<String> =
            dataStore.data.map { prefs ->
                normalizeTerminalInputMode(
                    prefs[TERMINAL_INPUT_MODE_KEY] ?: DEFAULT_TERMINAL_INPUT_MODE,
                )
            }

        val terminalTextInputApplyMode: Flow<String> =
            dataStore.data.map { prefs ->
                normalizeTerminalTextInputApplyMode(
                    prefs[TERMINAL_TEXT_INPUT_APPLY_MODE_KEY] ?: DEFAULT_TERMINAL_TEXT_INPUT_APPLY_MODE,
                )
            }

        suspend fun setLanguageTag(tag: String) {
            dataStore.edit { prefs -> prefs[LANGUAGE_TAG_KEY] = tag }
        }

        suspend fun setThemePreset(presetId: String) {
            dataStore.edit { prefs -> prefs[THEME_PRESET_KEY] = presetId }
        }

        suspend fun setClipboardTimeout(seconds: Int) {
            dataStore.edit { prefs -> prefs[CLIPBOARD_TIMEOUT_SECONDS_KEY] = seconds }
        }

        suspend fun setAutoLockTimeout(seconds: Int) {
            dataStore.edit { prefs -> prefs[AUTO_LOCK_TIMEOUT_SECONDS_KEY] = seconds }
        }

        suspend fun setTerminalColorScheme(schemeId: String) {
            dataStore.edit { prefs -> prefs[TERMINAL_COLOR_SCHEME_KEY] = schemeId }
        }

        suspend fun setTerminalFont(fontId: String) {
            dataStore.edit { prefs -> prefs[TERMINAL_FONT_KEY] = fontId }
        }

        suspend fun setTerminalFontSizeSp(sizeSp: Int) {
            dataStore.edit { prefs ->
                prefs[TERMINAL_FONT_SIZE_SP_KEY] = sizeSp.coerceIn(MIN_TERMINAL_FONT_SIZE_SP, MAX_TERMINAL_FONT_SIZE_SP)
            }
        }

        suspend fun setSshKeepaliveInterval(seconds: Int) {
            dataStore.edit { prefs -> prefs[SSH_KEEPALIVE_INTERVAL_SECONDS_KEY] = seconds }
        }

        suspend fun setSshCompressionEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[SSH_COMPRESSION_ENABLED_KEY] = enabled }
        }

        suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[SCREENSHOT_PROTECTION_ENABLED_KEY] = enabled }
        }

        suspend fun setTerminalCursorStyle(style: Int) {
            dataStore.edit { prefs -> prefs[TERMINAL_CURSOR_STYLE_KEY] = style }
        }

        suspend fun setTerminalShortcutLayout(layout: String) {
            dataStore.edit { prefs ->
                prefs[TERMINAL_SHORTCUT_LAYOUT_KEY] =
                    serializeTerminalShortcutLayout(
                        parseTerminalShortcutLayout(layout),
                    )
            }
        }

        suspend fun setTerminalHardwareKeyBindings(config: String) {
            dataStore.edit { prefs ->
                prefs[TERMINAL_HARDWARE_KEY_BINDINGS_KEY] =
                    serializeTerminalHardwareKeyBindings(
                        parseTerminalHardwareKeyBindings(config),
                    )
            }
        }

        suspend fun setTerminalInputMode(mode: String) {
            dataStore.edit { prefs -> prefs[TERMINAL_INPUT_MODE_KEY] = normalizeTerminalInputMode(mode) }
        }

        suspend fun setTerminalTextInputApplyMode(mode: String) {
            dataStore.edit { prefs ->
                prefs[TERMINAL_TEXT_INPUT_APPLY_MODE_KEY] = normalizeTerminalTextInputApplyMode(mode)
            }
        }

        private fun normalizeTerminalInputMode(mode: String): String {
            return when (mode) {
                TERMINAL_INPUT_MODE_DIRECT,
                TERMINAL_INPUT_MODE_TEXT_BAR,
                -> mode
                else -> DEFAULT_TERMINAL_INPUT_MODE
            }
        }

        private fun normalizeTerminalTextInputApplyMode(mode: String): String {
            return when (mode) {
                TERMINAL_TEXT_INPUT_APPLY_MODE_REALTIME,
                TERMINAL_TEXT_INPUT_APPLY_MODE_ON_SEND,
                -> mode
                else -> DEFAULT_TERMINAL_TEXT_INPUT_APPLY_MODE
            }
        }

        companion object {
            private val LANGUAGE_TAG_KEY = stringPreferencesKey("pref_language_tag")
            private val THEME_PRESET_KEY = stringPreferencesKey("pref_theme_preset")
            private val CLIPBOARD_TIMEOUT_SECONDS_KEY = intPreferencesKey("pref_clipboard_timeout_seconds")
            private val AUTO_LOCK_TIMEOUT_SECONDS_KEY = intPreferencesKey("pref_auto_lock_timeout_seconds")
            private val TERMINAL_COLOR_SCHEME_KEY = stringPreferencesKey("pref_terminal_color_scheme")
            private val TERMINAL_FONT_KEY = stringPreferencesKey("pref_terminal_font")
            private val TERMINAL_FONT_SIZE_SP_KEY = intPreferencesKey("pref_terminal_font_size_sp")
            private val SSH_KEEPALIVE_INTERVAL_SECONDS_KEY = intPreferencesKey("pref_ssh_keepalive_interval_seconds")
            private val SSH_COMPRESSION_ENABLED_KEY = booleanPreferencesKey("pref_ssh_compression_enabled")
            private val SCREENSHOT_PROTECTION_ENABLED_KEY = booleanPreferencesKey("pref_screenshot_protection_enabled")
            private val TERMINAL_CURSOR_STYLE_KEY = intPreferencesKey("pref_terminal_cursor_style")
            private val TERMINAL_SHORTCUT_LAYOUT_KEY = stringPreferencesKey("pref_terminal_shortcut_layout")
            private val TERMINAL_HARDWARE_KEY_BINDINGS_KEY =
                stringPreferencesKey("pref_terminal_hardware_key_bindings")
            private val TERMINAL_INPUT_MODE_KEY = stringPreferencesKey("pref_terminal_input_mode")
            private val TERMINAL_TEXT_INPUT_APPLY_MODE_KEY =
                stringPreferencesKey("pref_terminal_text_input_apply_mode")
            const val DEFAULT_LANGUAGE_TAG = ""
            const val DEFAULT_THEME_PRESET = "green"
            const val DEFAULT_CLIPBOARD_TIMEOUT = 30
            const val DEFAULT_AUTO_LOCK_TIMEOUT = 60
            const val DEFAULT_TERMINAL_COLOR_SCHEME = "default"
            const val DEFAULT_TERMINAL_FONT = "meslo_nerd"
            const val DEFAULT_TERMINAL_FONT_SIZE_SP = 20
            const val MIN_TERMINAL_FONT_SIZE_SP = 8
            const val MAX_TERMINAL_FONT_SIZE_SP = 48
            const val DEFAULT_SSH_KEEPALIVE_INTERVAL = 15
            const val DEFAULT_SSH_COMPRESSION_ENABLED = false
            const val DEFAULT_SCREENSHOT_PROTECTION_ENABLED = false
            const val DEFAULT_TERMINAL_CURSOR_STYLE = 0
            const val TERMINAL_INPUT_MODE_DIRECT = "direct"
            const val TERMINAL_INPUT_MODE_TEXT_BAR = "text_bar"
            const val DEFAULT_TERMINAL_INPUT_MODE = TERMINAL_INPUT_MODE_DIRECT
            const val TERMINAL_TEXT_INPUT_APPLY_MODE_REALTIME = "realtime"
            const val TERMINAL_TEXT_INPUT_APPLY_MODE_ON_SEND = "on_send"
            const val DEFAULT_TERMINAL_TEXT_INPUT_APPLY_MODE = TERMINAL_TEXT_INPUT_APPLY_MODE_REALTIME
        }
    }
