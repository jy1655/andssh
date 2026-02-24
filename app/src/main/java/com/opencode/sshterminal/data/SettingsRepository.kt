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

        val sshKeepaliveIntervalSeconds: Flow<Int> =
            dataStore.data.map { prefs ->
                prefs[SSH_KEEPALIVE_INTERVAL_SECONDS_KEY] ?: DEFAULT_SSH_KEEPALIVE_INTERVAL
            }

        val screenshotProtectionEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[SCREENSHOT_PROTECTION_ENABLED_KEY] ?: DEFAULT_SCREENSHOT_PROTECTION_ENABLED
            }

        val terminalHapticFeedbackEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[TERMINAL_HAPTIC_FEEDBACK_ENABLED_KEY] ?: DEFAULT_TERMINAL_HAPTIC_FEEDBACK_ENABLED
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

        suspend fun setSshKeepaliveInterval(seconds: Int) {
            dataStore.edit { prefs -> prefs[SSH_KEEPALIVE_INTERVAL_SECONDS_KEY] = seconds }
        }

        suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[SCREENSHOT_PROTECTION_ENABLED_KEY] = enabled }
        }

        suspend fun setTerminalHapticFeedbackEnabled(enabled: Boolean) {
            dataStore.edit { prefs -> prefs[TERMINAL_HAPTIC_FEEDBACK_ENABLED_KEY] = enabled }
        }

        companion object {
            private val LANGUAGE_TAG_KEY = stringPreferencesKey("pref_language_tag")
            private val THEME_PRESET_KEY = stringPreferencesKey("pref_theme_preset")
            private val CLIPBOARD_TIMEOUT_SECONDS_KEY = intPreferencesKey("pref_clipboard_timeout_seconds")
            private val AUTO_LOCK_TIMEOUT_SECONDS_KEY = intPreferencesKey("pref_auto_lock_timeout_seconds")
            private val TERMINAL_COLOR_SCHEME_KEY = stringPreferencesKey("pref_terminal_color_scheme")
            private val TERMINAL_FONT_KEY = stringPreferencesKey("pref_terminal_font")
            private val SSH_KEEPALIVE_INTERVAL_SECONDS_KEY = intPreferencesKey("pref_ssh_keepalive_interval_seconds")
            private val SCREENSHOT_PROTECTION_ENABLED_KEY = booleanPreferencesKey("pref_screenshot_protection_enabled")
            private val TERMINAL_HAPTIC_FEEDBACK_ENABLED_KEY = booleanPreferencesKey("pref_terminal_haptic_feedback_enabled")
            const val DEFAULT_LANGUAGE_TAG = ""
            const val DEFAULT_THEME_PRESET = "green"
            const val DEFAULT_CLIPBOARD_TIMEOUT = 30
            const val DEFAULT_AUTO_LOCK_TIMEOUT = 60
            const val DEFAULT_TERMINAL_COLOR_SCHEME = "default"
            const val DEFAULT_TERMINAL_FONT = "meslo_nerd"
            const val DEFAULT_SSH_KEEPALIVE_INTERVAL = 15
            const val DEFAULT_SCREENSHOT_PROTECTION_ENABLED = false
            const val DEFAULT_TERMINAL_HAPTIC_FEEDBACK_ENABLED = true
        }
    }
