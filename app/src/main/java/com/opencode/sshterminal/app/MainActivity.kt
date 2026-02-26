package com.opencode.sshterminal.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.navigation.SSHNavHost
import com.opencode.sshterminal.security.U2fActivityBridge
import com.opencode.sshterminal.session.SessionManager
import com.opencode.sshterminal.ui.lock.LockScreen
import com.opencode.sshterminal.ui.lock.LockViewModel
import com.opencode.sshterminal.ui.theme.AppTheme
import com.opencode.sshterminal.ui.theme.ThemePreset
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var sessionManager: SessionManager

    @Inject
    lateinit var u2fActivityBridge: U2fActivityBridge

    private var networkCallbackRegistered = false
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                sessionManager.reconnectTabsOnNetworkAvailable()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        registerNetworkMonitoring()

        setContent {
            val themePresetId by settingsRepository.themePresetId.collectAsState(
                initial = SettingsRepository.DEFAULT_THEME_PRESET,
            )
            val screenshotProtectionEnabled by settingsRepository.screenshotProtectionEnabled.collectAsState(
                initial = SettingsRepository.DEFAULT_SCREENSHOT_PROTECTION_ENABLED,
            )
            SideEffect {
                if (screenshotProtectionEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            AppTheme(themePreset = ThemePreset.fromId(themePresetId)) {
                val lockViewModel: LockViewModel = hiltViewModel()
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer =
                        LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                lockViewModel.checkAutoLock()
                            }
                        }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val isLocked by lockViewModel.isLocked.collectAsState()
                val isFirstSetup by lockViewModel.isFirstSetup.collectAsState()
                val error by lockViewModel.error.collectAsState()
                val isBiometricEnabled by lockViewModel.isBiometricEnabled.collectAsState()
                val canUseBiometric by lockViewModel.canUseBiometric.collectAsState()

                if (isLocked || isFirstSetup) {
                    LockScreen(
                        isFirstSetup = isFirstSetup,
                        error = error,
                        isBiometricEnabled = isBiometricEnabled,
                        canUseBiometric = canUseBiometric,
                        onUnlock = lockViewModel::unlock,
                        onSetupPassword = lockViewModel::setupPassword,
                        onSkipSetup = lockViewModel::skipSetup,
                        onUseBiometric = { lockViewModel.triggerBiometric(this@MainActivity) },
                        onClearError = lockViewModel::clearError,
                    )
                } else {
                    val navController = rememberNavController()
                    SSHNavHost(navController = navController)
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterNetworkMonitoring()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        u2fActivityBridge.setForegroundActivity(this)
    }

    override fun onPause() {
        u2fActivityBridge.setForegroundActivity(null)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?,
    ) {
        if (u2fActivityBridge.onActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerNetworkMonitoring() {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkMonitoring() {
        if (!networkCallbackRegistered) return
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        networkCallbackRegistered = false
    }
}
