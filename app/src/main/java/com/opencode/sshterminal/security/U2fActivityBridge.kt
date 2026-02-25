package com.opencode.sshterminal.security

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.fido.fido2.Fido2PendingIntent
import com.google.android.gms.fido.u2f.U2fPendingIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class U2fActivityBridge
    @Inject
    constructor() {
        private val requestCodeGenerator = AtomicInteger(U2F_REQUEST_CODE_BASE)
        private val pendingResults = ConcurrentHashMap<Int, CompletableDeferred<ActivityResultPayload>>()

        @Volatile
        private var activityRef: WeakReference<Activity>? = null

        fun setForegroundActivity(activity: Activity?) {
            activityRef = if (activity == null) null else WeakReference(activity)
        }

        suspend fun launchPendingIntent(
            pendingIntent: U2fPendingIntent,
            timeoutMillis: Long = DEFAULT_U2F_TIMEOUT_MS,
        ): ActivityResultPayload {
            val activity = activityRef?.get() ?: error("No active activity for U2F prompt")
            val requestCode = nextRequestCode()
            val deferred = CompletableDeferred<ActivityResultPayload>()
            pendingResults[requestCode] = deferred

            try {
                withContext(Dispatchers.Main) {
                    pendingIntent.launchPendingIntent(activity, requestCode)
                }
            } catch (error: IntentSender.SendIntentException) {
                pendingResults.remove(requestCode)
                throw error
            }

            return try {
                withTimeout(timeoutMillis) { deferred.await() }
            } finally {
                pendingResults.remove(requestCode)
            }
        }

        suspend fun launchPendingIntent(
            pendingIntent: Fido2PendingIntent,
            timeoutMillis: Long = DEFAULT_U2F_TIMEOUT_MS,
        ): ActivityResultPayload {
            val activity = activityRef?.get() ?: error("No active activity for FIDO2 prompt")
            val requestCode = nextRequestCode()
            val deferred = CompletableDeferred<ActivityResultPayload>()
            pendingResults[requestCode] = deferred

            try {
                withContext(Dispatchers.Main) {
                    pendingIntent.launchPendingIntent(activity, requestCode)
                }
            } catch (error: IntentSender.SendIntentException) {
                pendingResults.remove(requestCode)
                throw error
            }

            return try {
                withTimeout(timeoutMillis) { deferred.await() }
            } finally {
                pendingResults.remove(requestCode)
            }
        }

        fun onActivityResult(
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
        ): Boolean {
            val deferred = pendingResults.remove(requestCode) ?: return false
            deferred.complete(ActivityResultPayload(resultCode = resultCode, data = data))
            return true
        }

        private fun nextRequestCode(): Int {
            val current = requestCodeGenerator.incrementAndGet()
            if (current > U2F_REQUEST_CODE_MAX) {
                requestCodeGenerator.set(U2F_REQUEST_CODE_BASE)
                return requestCodeGenerator.incrementAndGet()
            }
            return current
        }

        data class ActivityResultPayload(
            val resultCode: Int,
            val data: Intent?,
        )

        companion object {
            private const val U2F_REQUEST_CODE_BASE = 61_000
            private const val U2F_REQUEST_CODE_MAX = 62_000
            private const val DEFAULT_U2F_TIMEOUT_MS = 90_000L
        }
    }
