package com.opencode.sshterminal.app

import android.Manifest
import android.content.pm.PackageManager
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

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

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
                val canUseBiometric by lockViewModel.canUseBiometric.collectAsState()

                if (isLocked || isFirstSetup) {
                    LockScreen(
                        isFirstSetup = isFirstSetup,
                        error = error,
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
}
