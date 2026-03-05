package com.opencode.sshterminal.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.auth.AuthRepository
import com.opencode.sshterminal.crash.CrashReportRepository
import com.opencode.sshterminal.data.ConnectionBackupImportSummary
import com.opencode.sshterminal.data.ConnectionBackupManager
import com.opencode.sshterminal.data.DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS
import com.opencode.sshterminal.data.DEFAULT_TERMINAL_SHORTCUT_LAYOUT
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.security.BiometricBoundKeyManager
import com.opencode.sshterminal.ui.theme.ThemePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val languageTag: String = "",
    val themePreset: ThemePreset = ThemePreset.GREEN,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isScreenshotProtectionEnabled: Boolean = SettingsRepository.DEFAULT_SCREENSHOT_PROTECTION_ENABLED,
    val autoLockTimeoutSeconds: Int = 60,
    val terminalColorScheme: String = "default",
    val terminalFont: String = "meslo_nerd",
    val terminalFontSizeSp: Int = SettingsRepository.DEFAULT_TERMINAL_FONT_SIZE_SP,
    val terminalCursorStyle: Int = SettingsRepository.DEFAULT_TERMINAL_CURSOR_STYLE,
    val terminalInputMode: String = SettingsRepository.DEFAULT_TERMINAL_INPUT_MODE,
    val terminalTextInputApplyMode: String = SettingsRepository.DEFAULT_TERMINAL_TEXT_INPUT_APPLY_MODE,
    val terminalShortcutLayout: String = DEFAULT_TERMINAL_SHORTCUT_LAYOUT,
    val terminalHardwareKeyBindings: String = DEFAULT_TERMINAL_HARDWARE_KEY_BINDINGS,
    val clipboardTimeoutSeconds: Int = 30,
    val sshKeepaliveIntervalSeconds: Int = SettingsRepository.DEFAULT_SSH_KEEPALIVE_INTERVAL,
    val sshCompressionEnabled: Boolean = SettingsRepository.DEFAULT_SSH_COMPRESSION_ENABLED,
    val crashReportCount: Int = 0,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val authRepository: AuthRepository,
        private val crashReportRepository: CrashReportRepository,
        private val connectionBackupManager: ConnectionBackupManager,
        private val biometricBoundKeyManager: BiometricBoundKeyManager,
    ) : ViewModel() {
        private val basePreferencesFlow =
            combine(
                settingsRepository.languageTag,
                settingsRepository.themePresetId,
                settingsRepository.autoLockTimeoutSeconds,
                settingsRepository.screenshotProtectionEnabled,
            ) { languageTag, themeId, autoLockTimeout, screenshotProtectionEnabled ->
                BasePreferences(
                    languageTag = languageTag,
                    themePresetId = themeId,
                    autoLockTimeoutSeconds = autoLockTimeout,
                    screenshotProtectionEnabled = screenshotProtectionEnabled,
                )
            }

        private val terminalCoreBasePreferencesFlow =
            combine(
                settingsRepository.terminalColorScheme,
                settingsRepository.terminalFont,
                settingsRepository.terminalFontSizeSp,
                settingsRepository.clipboardTimeoutSeconds,
                settingsRepository.sshKeepaliveIntervalSeconds,
            ) { scheme, font, fontSizeSp, clipboardTimeout, keepaliveInterval ->
                TerminalCorePreferences(
                    colorScheme = scheme,
                    font = font,
                    fontSizeSp = fontSizeSp,
                    clipboardTimeoutSeconds = clipboardTimeout,
                    sshKeepaliveIntervalSeconds = keepaliveInterval,
                    sshCompressionEnabled = SettingsRepository.DEFAULT_SSH_COMPRESSION_ENABLED,
                )
            }

        private val terminalCorePreferencesFlow =
            combine(
                terminalCoreBasePreferencesFlow,
                settingsRepository.sshCompressionEnabled,
            ) { basePrefs, compressionEnabled ->
                basePrefs.copy(
                    sshCompressionEnabled = compressionEnabled,
                )
            }

        private val terminalInteractionPreferencesFlow =
            combine(
                settingsRepository.terminalCursorStyle,
                settingsRepository.terminalInputMode,
                settingsRepository.terminalTextInputApplyMode,
            ) { cursorStyle, inputMode, textInputApplyMode ->
                TerminalInteractionPreferences(
                    terminalCursorStyle = cursorStyle,
                    terminalInputMode = inputMode,
                    terminalTextInputApplyMode = textInputApplyMode,
                )
            }

        private val terminalPreferencesFlow =
            combine(
                terminalCorePreferencesFlow,
                terminalInteractionPreferencesFlow,
                settingsRepository.terminalShortcutLayout,
                settingsRepository.terminalHardwareKeyBindings,
            ) { corePrefs, interactionPrefs, shortcutLayout, hardwareKeyBindings ->
                TerminalPreferences(
                    colorScheme = corePrefs.colorScheme,
                    font = corePrefs.font,
                    fontSizeSp = corePrefs.fontSizeSp,
                    clipboardTimeoutSeconds = corePrefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = corePrefs.sshKeepaliveIntervalSeconds,
                    sshCompressionEnabled = corePrefs.sshCompressionEnabled,
                    terminalInputMode = interactionPrefs.terminalInputMode,
                    terminalTextInputApplyMode = interactionPrefs.terminalTextInputApplyMode,
                    terminalShortcutLayout = shortcutLayout,
                    terminalHardwareKeyBindings = hardwareKeyBindings,
                    terminalCursorStyle = interactionPrefs.terminalCursorStyle,
                )
            }

        private val preferencesFlow =
            combine(basePreferencesFlow, terminalPreferencesFlow) { basePrefs, terminalPrefs ->
                SettingsPreferences(
                    languageTag = basePrefs.languageTag,
                    themePresetId = basePrefs.themePresetId,
                    autoLockTimeoutSeconds = basePrefs.autoLockTimeoutSeconds,
                    screenshotProtectionEnabled = basePrefs.screenshotProtectionEnabled,
                    terminalColorScheme = terminalPrefs.colorScheme,
                    terminalFont = terminalPrefs.font,
                    terminalFontSizeSp = terminalPrefs.fontSizeSp,
                    clipboardTimeoutSeconds = terminalPrefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = terminalPrefs.sshKeepaliveIntervalSeconds,
                    sshCompressionEnabled = terminalPrefs.sshCompressionEnabled,
                    terminalInputMode = terminalPrefs.terminalInputMode,
                    terminalTextInputApplyMode = terminalPrefs.terminalTextInputApplyMode,
                    terminalShortcutLayout = terminalPrefs.terminalShortcutLayout,
                    terminalHardwareKeyBindings = terminalPrefs.terminalHardwareKeyBindings,
                    terminalCursorStyle = terminalPrefs.terminalCursorStyle,
                )
            }

        private val authFlow =
            combine(
                authRepository.isAppLockEnabled,
                authRepository.isBiometricEnabled,
            ) { appLockEnabled, biometricEnabled ->
                AuthPreferences(
                    isAppLockEnabled = appLockEnabled,
                    isBiometricEnabled = biometricEnabled,
                )
            }

        private val crashReportCountFlow = flow { emit(crashReportRepository.getReportCount()) }

        val uiState: StateFlow<SettingsUiState> =
            combine(preferencesFlow, authFlow, crashReportCountFlow) { prefs, authPrefs, crashCount ->
                SettingsUiState(
                    languageTag = prefs.languageTag,
                    themePreset = ThemePreset.fromId(prefs.themePresetId),
                    isAppLockEnabled = authPrefs.isAppLockEnabled,
                    isBiometricEnabled = authPrefs.isBiometricEnabled,
                    isScreenshotProtectionEnabled = prefs.screenshotProtectionEnabled,
                    autoLockTimeoutSeconds = prefs.autoLockTimeoutSeconds,
                    terminalColorScheme = prefs.terminalColorScheme,
                    terminalFont = prefs.terminalFont,
                    terminalFontSizeSp = prefs.terminalFontSizeSp,
                    terminalCursorStyle = prefs.terminalCursorStyle,
                    terminalInputMode = prefs.terminalInputMode,
                    terminalTextInputApplyMode = prefs.terminalTextInputApplyMode,
                    terminalShortcutLayout = prefs.terminalShortcutLayout,
                    terminalHardwareKeyBindings = prefs.terminalHardwareKeyBindings,
                    clipboardTimeoutSeconds = prefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = prefs.sshKeepaliveIntervalSeconds,
                    sshCompressionEnabled = prefs.sshCompressionEnabled,
                    crashReportCount = crashCount,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                SettingsUiState(),
            )

        fun setLanguageTag(tag: String) {
            viewModelScope.launch {
                settingsRepository.setLanguageTag(tag)
                val locales =
                    if (tag.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }

        fun setThemePreset(preset: ThemePreset) {
            viewModelScope.launch {
                settingsRepository.setThemePreset(preset.id)
            }
        }

        fun setAppLockEnabled(enabled: Boolean) {
            viewModelScope.launch {
                authRepository.setAppLockEnabled(enabled)
            }
        }

        fun setBiometricEnabled(enabled: Boolean) {
            viewModelScope.launch {
                if (enabled) {
                    authRepository.setBiometricEnabled(biometricBoundKeyManager.ensureKey())
                } else {
                    authRepository.setBiometricEnabled(false)
                    biometricBoundKeyManager.deleteKey()
                }
            }
        }

        fun setAutoLockTimeout(seconds: Int) {
            viewModelScope.launch {
                settingsRepository.setAutoLockTimeout(seconds)
            }
        }

        fun setScreenshotProtectionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setScreenshotProtectionEnabled(enabled)
            }
        }

        fun setTerminalColorScheme(id: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalColorScheme(id)
            }
        }

        fun setTerminalFont(id: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalFont(id)
            }
        }

        fun setTerminalFontSizeSp(sizeSp: Int) {
            viewModelScope.launch {
                settingsRepository.setTerminalFontSizeSp(sizeSp)
            }
        }

        fun setClipboardTimeout(seconds: Int) {
            viewModelScope.launch {
                settingsRepository.setClipboardTimeout(seconds)
            }
        }

        fun setSshKeepaliveInterval(seconds: Int) {
            viewModelScope.launch {
                settingsRepository.setSshKeepaliveInterval(seconds)
            }
        }

        fun setSshCompressionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setSshCompressionEnabled(enabled)
            }
        }

        fun setTerminalCursorStyle(style: Int) {
            viewModelScope.launch {
                settingsRepository.setTerminalCursorStyle(style)
            }
        }

        fun setTerminalInputMode(mode: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalInputMode(mode)
            }
        }

        fun setTerminalTextInputApplyMode(mode: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalTextInputApplyMode(mode)
            }
        }

        fun setTerminalShortcutLayout(layout: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalShortcutLayout(layout)
            }
        }

        fun setTerminalHardwareKeyBindings(config: String) {
            viewModelScope.launch {
                settingsRepository.setTerminalHardwareKeyBindings(config)
            }
        }

        suspend fun exportPasswordEncryptedBackup(password: CharArray): String {
            return connectionBackupManager.exportPasswordEncryptedBackup(password)
        }

        suspend fun importBackup(
            json: String,
            password: CharArray?,
        ): ConnectionBackupImportSummary {
            return connectionBackupManager.importBackup(backupJson = json, password = password)
        }

        companion object {
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
        }
    }

private data class SettingsPreferences(
    val languageTag: String,
    val themePresetId: String,
    val autoLockTimeoutSeconds: Int,
    val screenshotProtectionEnabled: Boolean,
    val terminalColorScheme: String,
    val terminalFont: String,
    val terminalFontSizeSp: Int,
    val terminalCursorStyle: Int,
    val terminalInputMode: String,
    val terminalTextInputApplyMode: String,
    val terminalShortcutLayout: String,
    val terminalHardwareKeyBindings: String,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
    val sshCompressionEnabled: Boolean,
)

private data class AuthPreferences(
    val isAppLockEnabled: Boolean,
    val isBiometricEnabled: Boolean,
)

private data class BasePreferences(
    val languageTag: String,
    val themePresetId: String,
    val autoLockTimeoutSeconds: Int,
    val screenshotProtectionEnabled: Boolean,
)

private data class TerminalPreferences(
    val colorScheme: String,
    val font: String,
    val fontSizeSp: Int,
    val terminalCursorStyle: Int,
    val terminalInputMode: String,
    val terminalTextInputApplyMode: String,
    val terminalShortcutLayout: String,
    val terminalHardwareKeyBindings: String,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
    val sshCompressionEnabled: Boolean,
)

private data class TerminalCorePreferences(
    val colorScheme: String,
    val font: String,
    val fontSizeSp: Int,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
    val sshCompressionEnabled: Boolean,
)

private data class TerminalInteractionPreferences(
    val terminalCursorStyle: Int,
    val terminalInputMode: String,
    val terminalTextInputApplyMode: String,
)
