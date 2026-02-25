package com.opencode.sshterminal.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        private val defaultSecurityKeyApplication by lazy { resolveDefaultSecurityKeyApplication() }

        @Suppress(
            "ReturnCount",
            "ThrowsCount",
            "TooGenericExceptionCaught",
            "LoopWithTooManyJumpStatements",
            "LongMethod",
            "NestedBlockDepth",
        )
        suspend fun enrollSecurityKey(
            application: String,
            comment: String,
        ): EnrolledSecurityKey {
            val effectiveApplication = resolveRequestedApplication(application)
            val candidates =
                listOf(
                    effectiveApplication,
                    defaultSecurityKeyApplication,
                    LEGACY_PLACEHOLDER_APPLICATION,
                    LEGACY_OPENSSH_APPLICATION,
                ).map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
            var lastFailure: Throwable? = null
            val badRequestCandidates = mutableListOf<String>()
            for (candidate in candidates) {
                try {
                    return enrollWithAppId(candidate, comment)
                } catch (failure: U2fEnrollmentException) {
                    lastFailure = failure
                    logU2fFailure(
                        stage = "enroll",
                        appId = candidate,
                        code = failure.code,
                        message = failure.message,
                    )
                    if (failure.code == ErrorCode.BAD_REQUEST) {
                        badRequestCandidates += candidate
                        if (candidate != candidates.last()) {
                            continue
                        }
                        if (badRequestCandidates.size == candidates.size) {
                            val rejectedAppIds = badRequestCandidates.joinToString()
                            throw U2fEnrollmentException(
                                code = ErrorCode.BAD_REQUEST,
                                message =
                                    "U2F registration was rejected for every appId candidate: $rejectedAppIds. " +
                                        "This device or current Google Play services may not support U2F enrollment.",
                            )
                        }
                    }
                    throw failure
                } catch (failure: Exception) {
                    val reason =
                        failure.message?.ifBlank { null } ?: failure.javaClass.simpleName
                    val wrapped =
                        U2fEnrollmentException(
                            code = null,
                            message = "U2F enrollment error for appId=$candidate: $reason",
                        )
                    lastFailure = wrapped
                    logU2fFailure(
                        stage = "enroll_unexpected",
                        appId = candidate,
                        code = null,
                        message = wrapped.message,
                        throwable = failure,
                    )
                    if (candidate != candidates.last()) {
                        continue
                    }
                    throw wrapped
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
            if (response is ErrorResponseData) {
                val responseMessage = response.errorMessageOrNull()
                val reason = responseMessage ?: "code=${response.errorCode}(${response.errorCodeAsInt})"
                logU2fFailure(
                    stage = "sign",
                    appId = application,
                    code = response.errorCode,
                    message = "U2F sign failed: $reason",
                )
                return null
            }
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

        private fun resolveRequestedApplication(application: String): String {
            val normalized = application.trim()
            return if (
                normalized.isBlank() ||
                normalized == LEGACY_OPENSSH_APPLICATION ||
                normalized == LEGACY_PLACEHOLDER_APPLICATION
            ) {
                defaultSecurityKeyApplication
            } else {
                normalized
            }
        }

        private fun resolveDefaultSecurityKeyApplication(): String {
            val signingCertificateBytes =
                readSigningCertificateBytes(
                    packageManager = context.packageManager,
                    packageName = context.packageName,
                )
            val digest = MessageDigest.getInstance("SHA-1").digest(signingCertificateBytes)
            val encoded =
                Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest)
            return "$ANDROID_APK_KEY_HASH_PREFIX$encoded"
        }

        @Suppress("DEPRECATION")
        private fun readSigningCertificateBytes(
            packageManager: PackageManager,
            packageName: String,
        ): ByteArray {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo =
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                val signingInfo =
                    checkNotNull(packageInfo.signingInfo) {
                        "Missing signing info for package=$packageName"
                    }
                val signatures =
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                checkNotNull(signatures.firstOrNull()?.toByteArray()) {
                    "Missing signing certificate for package=$packageName"
                }
            } else {
                val packageInfo =
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES,
                    )
                checkNotNull(packageInfo.signatures?.firstOrNull()?.toByteArray()) {
                    "Missing signing certificate for package=$packageName"
                }
            }
        }

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
                val responseMessage = response.errorMessageOrNull()
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
            private const val ANDROID_APK_KEY_HASH_PREFIX = "android:apk-key-hash:"
            private const val LEGACY_OPENSSH_APPLICATION = "ssh:"
            private const val LEGACY_PLACEHOLDER_APPLICATION = "https://andssh.local"
            private val U2F_PROTOCOL_VERSION = ProtocolVersion.V2
            private val secureRandom = SecureRandom()
        }
    }

private const val U2F_LOG_TAG = "AndSSH-U2F"

@Suppress("UNNECESSARY_SAFE_CALL")
private fun ErrorResponseData.errorMessageOrNull(): String? = errorMessage?.takeIf { it.isNotBlank() }

private fun logU2fFailure(
    stage: String,
    appId: String,
    code: ErrorCode?,
    message: String,
    throwable: Throwable? = null,
) {
    val codeLabel = code?.name ?: "NONE"
    val logMessage = "stage=$stage appId=$appId code=$codeLabel message=$message"
    if (throwable == null) {
        Log.w(U2F_LOG_TAG, logMessage)
    } else {
        Log.w(U2F_LOG_TAG, logMessage, throwable)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
