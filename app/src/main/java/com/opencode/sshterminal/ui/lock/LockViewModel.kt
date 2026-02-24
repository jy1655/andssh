package com.opencode.sshterminal.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.sshterminal.R
import com.opencode.sshterminal.auth.AuthRepository
import com.opencode.sshterminal.auth.AutoLockManager
import com.opencode.sshterminal.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val autoLockManager: AutoLockManager,
        private val settingsRepository: SettingsRepository,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _isLocked = MutableStateFlow(true)
        val isLocked: StateFlow<Boolean> = _isLocked

        private val _isFirstSetup = MutableStateFlow(true)
        val isFirstSetup: StateFlow<Boolean> = _isFirstSetup

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error

        private val biometricAvailable =
            BiometricManager
                .from(appContext)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        val isBiometricEnabled: StateFlow<Boolean> =
            authRepository.isBiometricEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                initialValue = false,
            )

        val canUseBiometric: StateFlow<Boolean> =
            combine(isBiometricEnabled, isLocked, isFirstSetup) { enabled, locked, firstSetup ->
                enabled && biometricAvailable && locked && !firstSetup
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
                initialValue = false,
            )

        init {
            viewModelScope.launch {
                settingsRepository.autoLockTimeoutSeconds.collect { seconds ->
                    autoLockManager.updateTimeout(seconds)
                }
            }
            viewModelScope.launch {
                combine(
                    authRepository.isAppLockEnabled,
                    authRepository.isFirstSetupRequired,
                ) { appLockEnabled, firstSetupRequired ->
                    appLockEnabled to firstSetupRequired
                }.collect { (appLockEnabled, firstSetupRequired) ->
                    _isFirstSetup.value = firstSetupRequired
                    if (firstSetupRequired) {
                        _isLocked.value = true
                    } else if (!appLockEnabled) {
                        _isLocked.value = false
                    }
                }
            }
        }

        fun checkAutoLock() {
            if (autoLockManager.shouldLockOnResume()) {
                _isLocked.value = true
            }
        }

        fun unlock(password: String) {
            viewModelScope.launch {
                val verified = authRepository.verifyPassword(password.toCharArray())
                if (verified) {
                    _error.value = null
                    _isLocked.value = false
                } else {
                    _error.value = ERROR_WRONG_PASSWORD
                }
            }
        }

        fun setupPassword(password: String) {
            viewModelScope.launch {
                authRepository.setMasterPassword(password.toCharArray())
                authRepository.setBiometricEnabled(biometricAvailable)
                _error.value = null
                _isFirstSetup.value = false
                _isLocked.value = false
            }
        }

        fun skipSetup() {
            viewModelScope.launch {
                authRepository.setBiometricEnabled(false)
                authRepository.setAppLockEnabled(false)
                _error.value = null
                _isFirstSetup.value = false
                _isLocked.value = false
            }
        }

        fun triggerBiometric(activity: FragmentActivity) {
            if (!canUseBiometric.value) {
                return
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            _error.value = null
                            _isLocked.value = false
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                errorCode != BiometricPrompt.ERROR_USER_CANCELED
                            ) {
                                _error.value = errString.toString()
                            }
                        }
                    },
                )

            val promptInfo =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(activity.getString(R.string.lock_title))
                    .setSubtitle(activity.getString(R.string.lock_subtitle))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButtonText(activity.getString(R.string.common_cancel))
                    .build()

            prompt.authenticate(promptInfo)
        }

        fun clearError() {
            _error.value = null
        }

        companion object {
            const val ERROR_WRONG_PASSWORD = "wrong_password"
            const val ERROR_PASSWORDS_MISMATCH = "passwords_mismatch"
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
        }
    }
