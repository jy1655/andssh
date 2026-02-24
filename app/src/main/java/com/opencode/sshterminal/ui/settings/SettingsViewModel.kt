package com.opencode.sshterminal.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.auth.AuthRepository
import com.opencode.sshterminal.crash.CrashReportRepository
import com.opencode.sshterminal.data.ConnectionBackupImportSummary
import com.opencode.sshterminal.data.ConnectionBackupManager
import com.opencode.sshterminal.data.SettingsRepository
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
    val terminalHapticFeedbackEnabled: Boolean = SettingsRepository.DEFAULT_TERMINAL_HAPTIC_FEEDBACK_ENABLED,
    val clipboardTimeoutSeconds: Int = 30,
    val sshKeepaliveIntervalSeconds: Int = SettingsRepository.DEFAULT_SSH_KEEPALIVE_INTERVAL,
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

        private val terminalInputFeedbackFlow =
            combine(
                settingsRepository.terminalHapticFeedbackEnabled,
                settingsRepository.terminalCursorStyle,
            ) { hapticFeedbackEnabled, cursorStyle ->
                TerminalInputFeedbackPreferences(
                    hapticFeedbackEnabled = hapticFeedbackEnabled,
                    cursorStyle = cursorStyle,
                )
            }

        private val terminalCorePreferencesFlow =
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
                )
            }

        private val terminalPreferencesFlow =
            combine(
                terminalCorePreferencesFlow,
                terminalInputFeedbackFlow,
            ) { corePrefs, feedbackPrefs ->
                TerminalPreferences(
                    colorScheme = corePrefs.colorScheme,
                    font = corePrefs.font,
                    fontSizeSp = corePrefs.fontSizeSp,
                    clipboardTimeoutSeconds = corePrefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = corePrefs.sshKeepaliveIntervalSeconds,
                    terminalHapticFeedbackEnabled = feedbackPrefs.hapticFeedbackEnabled,
                    terminalCursorStyle = feedbackPrefs.cursorStyle,
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
                    terminalHapticFeedbackEnabled = terminalPrefs.terminalHapticFeedbackEnabled,
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
                    terminalHapticFeedbackEnabled = prefs.terminalHapticFeedbackEnabled,
                    clipboardTimeoutSeconds = prefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = prefs.sshKeepaliveIntervalSeconds,
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
                authRepository.setBiometricEnabled(enabled)
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

        fun setTerminalHapticFeedbackEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setTerminalHapticFeedbackEnabled(enabled)
            }
        }

        fun setTerminalCursorStyle(style: Int) {
            viewModelScope.launch {
                settingsRepository.setTerminalCursorStyle(style)
            }
        }

        suspend fun exportEncryptedBackup(): String {
            return connectionBackupManager.exportEncryptedBackup()
        }

        suspend fun importEncryptedBackup(backupJson: String): ConnectionBackupImportSummary {
            return connectionBackupManager.importEncryptedBackup(backupJson)
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
    val terminalHapticFeedbackEnabled: Boolean,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
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
    val terminalHapticFeedbackEnabled: Boolean,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
)

private data class TerminalCorePreferences(
    val colorScheme: String,
    val font: String,
    val fontSizeSp: Int,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
)

private data class TerminalInputFeedbackPreferences(
    val hapticFeedbackEnabled: Boolean,
    val cursorStyle: Int,
)
