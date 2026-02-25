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
import com.opencode.sshterminal.security.BiometricBoundKeyManager
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
        private val biometricBoundKeyManager: BiometricBoundKeyManager,
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
                canOfferBiometricUnlock(
                    biometricEnabled = enabled,
                    biometricAvailable = biometricAvailable,
                    hasBiometricKey = biometricBoundKeyManager.hasKey(),
                    isLocked = locked,
                    isFirstSetup = firstSetup,
                )
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
                val biometricEnabled = biometricAvailable && biometricBoundKeyManager.ensureKey()
                authRepository.setBiometricEnabled(biometricEnabled)
                _error.value = null
                _isFirstSetup.value = false
                _isLocked.value = false
            }
        }

        fun skipSetup() {
            viewModelScope.launch {
                authRepository.setBiometricEnabled(false)
                biometricBoundKeyManager.deleteKey()
                authRepository.setAppLockEnabled(false)
                _error.value = null
                _isFirstSetup.value = false
                _isLocked.value = false
            }
        }

        @Suppress("ReturnCount")
        fun triggerBiometric(activity: FragmentActivity) {
            if (!canUseBiometric.value && !isBiometricEnabled.value) {
                return
            }
            if (!biometricBoundKeyManager.ensureKey()) {
                viewModelScope.launch { authRepository.setBiometricEnabled(false) }
                _error.value = ERROR_BIOMETRIC_KEY_UNAVAILABLE
                return
            }
            val cipher = biometricBoundKeyManager.createUnlockCipher()
            if (cipher == null) {
                viewModelScope.launch { authRepository.setBiometricEnabled(false) }
                _error.value = ERROR_BIOMETRIC_KEY_UNAVAILABLE
                return
            }

            val executor = ContextCompat.getMainExecutor(activity)
            val prompt =
                BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (biometricBoundKeyManager.verifyUnlock(result.cryptoObject?.cipher)) {
                                _error.value = null
                                _isLocked.value = false
                            } else {
                                _error.value = ERROR_BIOMETRIC_KEY_UNAVAILABLE
                            }
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence,
                        ) {
                            if (shouldDisplayBiometricError(errorCode)) {
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

            prompt.authenticate(
                promptInfo,
                BiometricPrompt.CryptoObject(cipher),
            )
        }

        fun clearError() {
            _error.value = null
        }

        companion object {
            const val ERROR_WRONG_PASSWORD = "wrong_password"
            const val ERROR_PASSWORDS_MISMATCH = "passwords_mismatch"
            const val ERROR_BIOMETRIC_KEY_UNAVAILABLE = "biometric_key_unavailable"
            private const val STATE_FLOW_TIMEOUT_MS = 5_000L
        }
    }

internal fun canOfferBiometricUnlock(
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    hasBiometricKey: Boolean,
    isLocked: Boolean,
    isFirstSetup: Boolean,
): Boolean {
    return biometricEnabled &&
        biometricAvailable &&
        hasBiometricKey &&
        isLocked &&
        !isFirstSetup
}

internal fun shouldDisplayBiometricError(errorCode: Int): Boolean {
    return errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
        errorCode != BiometricPrompt.ERROR_USER_CANCELED
}
