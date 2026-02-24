package com.opencode.sshterminal.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.auth.AuthRepository
import com.opencode.sshterminal.crash.CrashReportRepository
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
    val autoLockTimeoutSeconds: Int = 60,
    val terminalColorScheme: String = "default",
    val terminalFont: String = "meslo_nerd",
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
    ) : ViewModel() {
        private val basePreferencesFlow =
            combine(
                settingsRepository.languageTag,
                settingsRepository.themePresetId,
                settingsRepository.autoLockTimeoutSeconds,
            ) { languageTag, themeId, autoLockTimeout ->
                BasePreferences(
                    languageTag = languageTag,
                    themePresetId = themeId,
                    autoLockTimeoutSeconds = autoLockTimeout,
                )
            }

        private val terminalPreferencesFlow =
            combine(
                settingsRepository.terminalColorScheme,
                settingsRepository.terminalFont,
                settingsRepository.clipboardTimeoutSeconds,
                settingsRepository.sshKeepaliveIntervalSeconds,
            ) { scheme, font, clipboardTimeout, keepaliveInterval ->
                TerminalPreferences(
                    colorScheme = scheme,
                    font = font,
                    clipboardTimeoutSeconds = clipboardTimeout,
                    sshKeepaliveIntervalSeconds = keepaliveInterval,
                )
            }

        private val preferencesFlow =
            combine(basePreferencesFlow, terminalPreferencesFlow) { basePrefs, terminalPrefs ->
                SettingsPreferences(
                    languageTag = basePrefs.languageTag,
                    themePresetId = basePrefs.themePresetId,
                    autoLockTimeoutSeconds = basePrefs.autoLockTimeoutSeconds,
                    terminalColorScheme = terminalPrefs.colorScheme,
                    terminalFont = terminalPrefs.font,
                    clipboardTimeoutSeconds = terminalPrefs.clipboardTimeoutSeconds,
                    sshKeepaliveIntervalSeconds = terminalPrefs.sshKeepaliveIntervalSeconds,
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
                    autoLockTimeoutSeconds = prefs.autoLockTimeoutSeconds,
                    terminalColorScheme = prefs.terminalColorScheme,
                    terminalFont = prefs.terminalFont,
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

        companion object {
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
        }
    }

private data class SettingsPreferences(
    val languageTag: String,
    val themePresetId: String,
    val autoLockTimeoutSeconds: Int,
    val terminalColorScheme: String,
    val terminalFont: String,
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
)

private data class TerminalPreferences(
    val colorScheme: String,
    val font: String,
    val clipboardTimeoutSeconds: Int,
    val sshKeepaliveIntervalSeconds: Int,
)
