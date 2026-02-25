package com.opencode.sshterminal.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.opencode.sshterminal.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoLockManager
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val authRepository: AuthRepository,
    ) : DefaultLifecycleObserver {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var appLockEnabled = false
        private var backgroundTimestamp: Long = 0L
        private var timeoutSeconds: Int = SettingsRepository.DEFAULT_AUTO_LOCK_TIMEOUT

        init {
            scope.launch {
                settingsRepository.autoLockTimeoutSeconds.collectLatest { timeoutSeconds = it }
            }
            scope.launch {
                authRepository.isAppLockEnabled.collectLatest { appLockEnabled = it }
            }
        }

        fun updateTimeout(seconds: Int) {
            timeoutSeconds = seconds
        }

        override fun onStop(owner: LifecycleOwner) {
            backgroundTimestamp =
                if (appLockEnabled) {
                    System.currentTimeMillis()
                } else {
                    0L
                }
        }

        fun shouldLockOnResume(): Boolean {
            val shouldLock =
                shouldAutoLockAfterResume(
                    appLockEnabled = appLockEnabled,
                    timeoutSeconds = timeoutSeconds,
                    backgroundTimestamp = backgroundTimestamp,
                    nowMillis = System.currentTimeMillis(),
                )
            backgroundTimestamp = 0L
            return shouldLock
        }
    }

internal fun shouldAutoLockAfterResume(
    appLockEnabled: Boolean,
    timeoutSeconds: Int,
    backgroundTimestamp: Long,
    nowMillis: Long,
): Boolean {
    if (!appLockEnabled || timeoutSeconds <= 0 || backgroundTimestamp == 0L) {
        return false
    }
    val elapsed = nowMillis - backgroundTimestamp
    return elapsed >= timeoutSeconds * 1000L
}
