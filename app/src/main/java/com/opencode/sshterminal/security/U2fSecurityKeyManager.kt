package com.opencode.sshterminal.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        @Suppress("ReturnCount")
        suspend fun enrollSecurityKey(
            application: String,
            comment: String,
        ): EnrolledSecurityKey {
            val normalizedApplication = application.trim().ifBlank { DEFAULT_SECURITY_KEY_APPLICATION }
            val appUri = Uri.parse(normalizedApplication)
            val challenge = ByteArray(U2F_CHALLENGE_BYTES).also(secureRandom::nextBytes)
            val registerRequest = RegisterRequest(ProtocolVersion.V1, challenge, normalizedApplication)
            val params =
                RegisterRequestParams
                    .Builder()
                    .setAppId(appUri)
                    .setRegisterRequests(listOf(registerRequest))
                    .setRegisteredKeys(emptyList())
                    .build()

            val pendingIntent = Fido.getU2fApiClient(context).getRegisterIntent(params).awaitResult()
            if (!pendingIntent.hasPendingIntent()) {
                error("U2F registration is unavailable on this device")
            }

            val result = u2fActivityBridge.launchPendingIntent(pendingIntent)
            if (result.resultCode != Activity.RESULT_OK) {
                error("Security key prompt was cancelled")
            }
            val response = result.data.extractResponseData() ?: error("Missing U2F registration response")
            if (response is ErrorResponseData) {
                val errorCode = response.errorCode
                if (errorCode == ErrorCode.BAD_REQUEST && normalizedApplication == DEFAULT_SECURITY_KEY_APPLICATION) {
                    error("U2F BAD_REQUEST for app id 'ssh:'. Try application https://andssh.local")
                }
                val responseMessage = response.errorMessage.takeIf { it.isNotBlank() }
                val reason = responseMessage ?: "code=$errorCode(${response.errorCodeAsInt})"
                error("U2F registration failed: $reason")
            }
            val registerResponse =
                response as? RegisterResponseData
                    ?: error("Unexpected U2F response type: ${response.javaClass.simpleName}")
            val material =
                parseU2fRegisterData(registerResponse.registerData)
                    ?: error("Invalid U2F registration payload")
            return EnrolledSecurityKey(
                application = normalizedApplication,
                keyHandleBase64 = material.keyHandle.toBase64(),
                publicKeyBase64 = material.publicKeyUncompressed.toBase64(),
                authorizedKey =
                    buildSshSkEcdsaAuthorizedKey(
                        publicKeyUncompressed = material.publicKeyUncompressed,
                        application = normalizedApplication,
                        comment = comment,
                    ),
            )
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
                        ProtocolVersion.V1,
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

        data class EnrolledSecurityKey(
            val application: String,
            val keyHandleBase64: String,
            val publicKeyBase64: String,
            val authorizedKey: String,
        )

        companion object {
            private const val U2F_CHALLENGE_BYTES = RegisterRequest.U2F_V1_CHALLENGE_BYTE_LENGTH
            private const val DEFAULT_SECURITY_KEY_APPLICATION = "ssh:"
            private val secureRandom = SecureRandom()
        }
    }

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
