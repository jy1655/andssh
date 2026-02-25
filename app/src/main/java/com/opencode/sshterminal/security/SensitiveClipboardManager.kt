package com.opencode.sshterminal.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import com.opencode.sshterminal.data.SettingsRepository
import com.opencode.sshterminal.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitiveClipboardManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val clipboardManager: ClipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingClearRunnable: Runnable? = null
        private var pendingClearJob: Job? = null

        fun copyToClipboard(
            label: String,
            text: String,
        ) {
            cancelPendingClear()
            val clip = ClipData.newPlainText(label, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.setExtras(
                    PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    },
                )
            }
            clipboardManager.setPrimaryClip(clip)

            pendingClearJob =
                scope.launch {
                    val timeoutSeconds =
                        normalizeClipboardTimeoutSeconds(
                            settingsRepository.clipboardTimeoutSeconds.first(),
                        )
                    if (!shouldScheduleClipboardAutoClear(timeoutSeconds)) {
                        return@launch
                    }
                    val clearRunnable =
                        Runnable {
                            clearClipboard()
                            pendingClearRunnable = null
                        }
                    pendingClearRunnable = clearRunnable
                    mainHandler.postDelayed(clearRunnable, timeoutSeconds * 1_000L)
                }
        }

        fun cancelPendingClear() {
            pendingClearJob?.cancel()
            pendingClearJob = null
            pendingClearRunnable?.let(mainHandler::removeCallbacks)
            pendingClearRunnable = null
        }

        private fun clearClipboard() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            } else {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

internal fun normalizeClipboardTimeoutSeconds(timeoutSeconds: Int): Int = timeoutSeconds.coerceAtLeast(0)

internal fun shouldScheduleClipboardAutoClear(timeoutSeconds: Int): Boolean = timeoutSeconds > 0
