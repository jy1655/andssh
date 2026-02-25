package com.opencode.sshterminal.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.u2f.api.common.ErrorCode
import com.google.android.gms.fido.u2f.api.common.ErrorResponseData
import com.google.android.gms.fido.u2f.api.common.KeyHandle
import com.google.android.gms.fido.u2f.api.common.ProtocolVersion
import com.google.android.gms.fido.u2f.api.common.RegisterRequest
import com.google.android.gms.fido.u2f.api.common.RegisterRequestParams
import com.google.android.gms.fido.u2f.api.common.RegisterResponseData
import com.google.android.gms.fido.u2f.api.common.RegisteredKey
import com.google.android.gms.fido.u2f.api.common.ResponseData
import com.google.android.gms.fido.u2f.api.common.SignRequestParams
import com.google.android.gms.fido.u2f.api.common.SignResponseData
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class U2fSecurityKeyManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val u2fActivityBridge: U2fActivityBridge,
    ) {
        @Suppress("ReturnCount", "ThrowsCount")
        suspend fun enrollSecurityKey(
            application: String,
            comment: String,
        ): EnrolledSecurityKey {
            val normalizedApplication = application.trim().ifBlank { DEFAULT_SECURITY_KEY_APPLICATION }
            val effectiveApplication =
                if (normalizedApplication == LEGACY_OPENSSH_APPLICATION) {
                    DEFAULT_SECURITY_KEY_APPLICATION
                } else {
                    normalizedApplication
                }
            val candidates =
                buildList {
                    add(effectiveApplication)
                    if (effectiveApplication != GOOGLE_FALLBACK_APPLICATION) {
                        add(GOOGLE_FALLBACK_APPLICATION)
                    }
                }
            var lastFailure: Throwable? = null
            for (candidate in candidates) {
                try {
                    return enrollWithAppId(candidate, comment)
                } catch (failure: U2fEnrollmentException) {
                    lastFailure = failure
                    Log.w(TAG, "U2F enroll failed for appId=$candidate (${failure.code}): ${failure.message}")
                    if (failure.code == ErrorCode.BAD_REQUEST && candidate != candidates.last()) {
                        continue
                    }
                    throw failure
                }
            }
            throw (lastFailure ?: error("U2F registration failed"))
        }

        @Suppress("ReturnCount")
        internal suspend fun signForSsh(
            application: String,
            keyHandleBase64: String,
            message: ByteArray,
        ): U2fSignatureMaterial? {
            val keyHandleBytes = keyHandleBase64.fromBase64OrNull() ?: return null
            val challenge = sha256(message)
            val registeredKey =
                RegisteredKey(
                    KeyHandle(
                        keyHandleBytes,
                        U2F_PROTOCOL_VERSION,
                        emptyList<Transport>(),
                    ),
                )
            val params =
                SignRequestParams
                    .Builder()
                    .setAppId(Uri.parse(application))
                    .setDefaultSignChallenge(challenge)
                    .setRegisteredKeys(listOf(registeredKey))
                    .build()

            val pendingIntent = Fido.getU2fApiClient(context).getSignIntent(params).awaitResult()
            if (!pendingIntent.hasPendingIntent()) return null

            val result = u2fActivityBridge.launchPendingIntent(pendingIntent)
            if (result.resultCode != Activity.RESULT_OK) return null
            val response = result.data.extractResponseData() ?: return null
            if (response is ErrorResponseData) return null
            val signResponse = response as? SignResponseData ?: return null
            return parseU2fSignatureData(signResponse.signatureData)
        }

        private fun Intent?.extractResponseData(): ResponseData? {
            val intent = this ?: return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Fido.KEY_RESPONSE_EXTRA, ResponseData::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Fido.KEY_RESPONSE_EXTRA) as? ResponseData
            }
        }

        private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

        private fun String.fromBase64OrNull(): ByteArray? = runCatching { Base64.getDecoder().decode(this) }.getOrNull()

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

        @Suppress("ReturnCount", "LongMethod", "ThrowsCount")
        private suspend fun enrollWithAppId(
            appId: String,
            comment: String,
        ): EnrolledSecurityKey {
            val appUri = Uri.parse(appId)
            val challenge = ByteArray(U2F_CHALLENGE_BYTES).also(secureRandom::nextBytes)
            val registerRequest = RegisterRequest(U2F_PROTOCOL_VERSION, challenge, appId)
            val params =
                RegisterRequestParams
                    .Builder()
                    .setAppId(appUri)
                    .setRegisterRequests(listOf(registerRequest))
                    .setRegisteredKeys(emptyList())
                    .build()

            val pendingIntent = Fido.getU2fApiClient(context).getRegisterIntent(params).awaitResult()
            if (!pendingIntent.hasPendingIntent()) {
                throw U2fEnrollmentException(
                    code = null,
                    message = "U2F registration is unavailable on this device (appId=$appId)",
                )
            }

            val result = u2fActivityBridge.launchPendingIntent(pendingIntent)
            if (result.resultCode != Activity.RESULT_OK) {
                throw U2fEnrollmentException(
                    code = null,
                    message = "Security key prompt was cancelled (appId=$appId)",
                )
            }
            val response =
                result.data.extractResponseData()
                    ?: throw U2fEnrollmentException(
                        code = null,
                        message = "Missing U2F registration response (appId=$appId)",
                    )
            if (response is ErrorResponseData) {
                val errorCode = response.errorCode
                val responseMessage = response.errorMessage.takeIf { it.isNotBlank() }
                val reason = responseMessage ?: "code=$errorCode(${response.errorCodeAsInt})"
                throw U2fEnrollmentException(
                    code = errorCode,
                    message = "U2F registration failed: $reason (appId=$appId)",
                )
            }
            val registerResponse =
                response as? RegisterResponseData
                    ?: throw U2fEnrollmentException(
                        code = null,
                        message = "Unexpected U2F response type: ${response.javaClass.simpleName} (appId=$appId)",
                    )
            val material =
                parseU2fRegisterData(registerResponse.registerData)
                    ?: throw U2fEnrollmentException(
                        code = null,
                        message = "Invalid U2F registration payload (appId=$appId)",
                    )
            return EnrolledSecurityKey(
                application = appId,
                keyHandleBase64 = material.keyHandle.toBase64(),
                publicKeyBase64 = material.publicKeyUncompressed.toBase64(),
                authorizedKey =
                    buildSshSkEcdsaAuthorizedKey(
                        publicKeyUncompressed = material.publicKeyUncompressed,
                        application = appId,
                        comment = comment,
                    ),
            )
        }

        data class EnrolledSecurityKey(
            val application: String,
            val keyHandleBase64: String,
            val publicKeyBase64: String,
            val authorizedKey: String,
        )

        private class U2fEnrollmentException(
            val code: ErrorCode?,
            override val message: String,
        ) : IllegalStateException(message)

        companion object {
            private const val U2F_CHALLENGE_BYTES = 32
            private const val DEFAULT_SECURITY_KEY_APPLICATION = "https://andssh.local"
            private const val LEGACY_OPENSSH_APPLICATION = "ssh:"
            private const val GOOGLE_FALLBACK_APPLICATION = "https://www.gstatic.com/securitykey/origins.json"
            private const val TAG = "AndSSH-U2F"
            private val U2F_PROTOCOL_VERSION = ProtocolVersion.V2
            private val secureRandom = SecureRandom()
        }
    }

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
