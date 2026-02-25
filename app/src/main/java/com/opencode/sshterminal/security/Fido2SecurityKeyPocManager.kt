@file:Suppress(
    "TooManyFunctions",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "MaxLineLength",
)

package com.opencode.sshterminal.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.common.Transport
import com.google.android.gms.fido.fido2.api.common.AttestationConveyancePreference
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAttestationResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.ErrorCode
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialCreationOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialParameters
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRpEntity
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialUserEntity
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class Fido2SecurityKeyPocManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val u2fActivityBridge: U2fActivityBridge,
    ) {
        @Suppress("LongMethod", "ReturnCount", "ThrowsCount")
        suspend fun runEnrollmentAndAssertionPoc(
            application: String,
            userDisplayName: String,
        ): Fido2PocResult {
            val normalizedApplication = application.trim().ifBlank { LEGACY_OPENSSH_APPLICATION }
            val rpId = resolveRpIdForFido2(application = normalizedApplication, packageName = context.packageName)
            val fido2ApiClient = Fido.getFido2ApiClient(context)

            val registrationOptions =
                PublicKeyCredentialCreationOptions
                    .Builder()
                    .setRp(
                        PublicKeyCredentialRpEntity(
                            rpId,
                            RP_ENTITY_NAME,
                            "",
                        ),
                    ).setUser(
                        PublicKeyCredentialUserEntity(
                            randomBytes(USER_ID_BYTES),
                            userDisplayName.ifBlank { DEFAULT_USER_NAME },
                            userDisplayName.ifBlank { DEFAULT_USER_NAME },
                            "",
                        ),
                    ).setChallenge(randomBytes(FIDO2_CHALLENGE_BYTES))
                    .setParameters(
                        listOf(
                            PublicKeyCredentialParameters(
                                PublicKeyCredentialType.PUBLIC_KEY.toString(),
                                ES256_ALGORITHM_ID,
                            ),
                        ),
                    ).setTimeoutSeconds(FIDO2_TIMEOUT_SECONDS)
                    .setAttestationConveyancePreference(AttestationConveyancePreference.NONE)
                    .build()

            val registerPendingIntent = fido2ApiClient.getRegisterIntent(registrationOptions).awaitResult()
            if (!registerPendingIntent.hasPendingIntent()) {
                throw Fido2PocException(
                    code = null,
                    message = "FIDO2 register intent unavailable (rpId=$rpId)",
                )
            }

            val registerResult = u2fActivityBridge.launchPendingIntent(registerPendingIntent)
            val registerError = registerResult.data.extractFido2ErrorResponseOrNull()
            if (registerResult.resultCode != Activity.RESULT_OK || registerError != null) {
                val reason =
                    registerError.toReasonOrNull()
                        ?: "cancelled(resultCode=${registerResult.resultCode})"
                throw Fido2PocException(
                    code = registerError?.errorCode,
                    message = "FIDO2 registration failed: $reason (rpId=$rpId)",
                )
            }

            val registerCredential =
                registerResult.data.extractFido2CredentialOrNull()
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Missing FIDO2 registration credential (rpId=$rpId)",
                    )
            val registerResponse = registerCredential.response
            if (registerResponse is AuthenticatorErrorResponse) {
                throw Fido2PocException(
                    code = registerResponse.errorCode,
                    message = "FIDO2 registration failed: ${registerResponse.toReasonOrNull()} (rpId=$rpId)",
                )
            }
            val attestationResponse =
                registerResponse as? AuthenticatorAttestationResponse
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Unexpected FIDO2 registration response: ${registerResponse.javaClass.simpleName} (rpId=$rpId)",
                    )

            val credentialId =
                registerCredential.rawId?.takeIf { it.isNotEmpty() }
                    ?: attestationResponse.keyHandle.takeIf { it.isNotEmpty() }
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Missing FIDO2 credential ID (rpId=$rpId)",
                    )
            val publicKeyUncompressed =
                extractCredentialPublicKeyUncompressedFromAttestationObject(attestationResponse.attestationObject)
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Unable to parse credential public key from attestationObject (rpId=$rpId)",
                    )

            val signOptions =
                PublicKeyCredentialRequestOptions
                    .Builder()
                    .setRpId(rpId)
                    .setChallenge(randomBytes(FIDO2_CHALLENGE_BYTES))
                    .setAllowList(
                        listOf(
                            PublicKeyCredentialDescriptor(
                                PublicKeyCredentialType.PUBLIC_KEY.toString(),
                                credentialId,
                                emptyList<Transport>(),
                            ),
                        ),
                    ).setTimeoutSeconds(FIDO2_TIMEOUT_SECONDS)
                    .build()
            val signPendingIntent = fido2ApiClient.getSignIntent(signOptions).awaitResult()
            if (!signPendingIntent.hasPendingIntent()) {
                throw Fido2PocException(
                    code = null,
                    message = "FIDO2 sign intent unavailable (rpId=$rpId)",
                )
            }

            val signResult = u2fActivityBridge.launchPendingIntent(signPendingIntent)
            val signError = signResult.data.extractFido2ErrorResponseOrNull()
            if (signResult.resultCode != Activity.RESULT_OK || signError != null) {
                val reason =
                    signError.toReasonOrNull()
                        ?: "cancelled(resultCode=${signResult.resultCode})"
                throw Fido2PocException(
                    code = signError?.errorCode,
                    message = "FIDO2 assertion failed: $reason (rpId=$rpId)",
                )
            }
            val signCredential =
                signResult.data.extractFido2CredentialOrNull()
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Missing FIDO2 assertion credential (rpId=$rpId)",
                    )
            val signResponse = signCredential.response
            if (signResponse is AuthenticatorErrorResponse) {
                throw Fido2PocException(
                    code = signResponse.errorCode,
                    message = "FIDO2 assertion failed: ${signResponse.toReasonOrNull()} (rpId=$rpId)",
                )
            }
            val assertionResponse =
                signResponse as? AuthenticatorAssertionResponse
                    ?: throw Fido2PocException(
                        code = null,
                        message = "Unexpected FIDO2 assertion response: ${signResponse.javaClass.simpleName} (rpId=$rpId)",
                    )
            val signatureMaterial =
                parseFido2AssertionSignatureMaterial(
                    authenticatorData = assertionResponse.authenticatorData,
                    derSignature = assertionResponse.signature,
                ) ?: throw Fido2PocException(
                    code = null,
                    message = "Invalid FIDO2 assertion payload (rpId=$rpId)",
                )

            return Fido2PocResult(
                application = normalizedApplication,
                rpId = rpId,
                credentialIdBase64 = credentialId.toBase64(),
                publicKeyBase64 = publicKeyUncompressed.toBase64(),
                assertionFlags = signatureMaterial.flags,
                assertionCounter = signatureMaterial.counter,
                signatureSize = signatureMaterial.derSignature.size,
            )
        }

        suspend fun runEnrollmentAndAssertionPocOrNull(
            application: String,
            userDisplayName: String,
        ): Fido2PocResult? {
            val result =
                runCatching {
                    runEnrollmentAndAssertionPoc(
                        application = application,
                        userDisplayName = userDisplayName,
                    )
                }
            result.onSuccess { success ->
                val successLogMessage =
                    "stage=poc_success rpId=${success.rpId} flags=${success.assertionFlags} " +
                        "counter=${success.assertionCounter} signatureSize=${success.signatureSize}"
                Log.i(
                    FIDO2_LOG_TAG,
                    successLogMessage,
                )
            }
            result.onFailure { error ->
                logFido2Failure(
                    stage = "poc",
                    rpId = resolveRpIdForFido2(application = application, packageName = context.packageName),
                    code = (error as? Fido2PocException)?.code,
                    message = error.message.orEmpty(),
                    throwable = error,
                )
            }
            return result.getOrNull()
        }

        private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

        private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

        companion object {
            private const val RP_ENTITY_NAME = "AndSSH"
            private const val DEFAULT_USER_NAME = "andssh-user"
            private const val USER_ID_BYTES = 32
            private const val FIDO2_CHALLENGE_BYTES = 32
            private const val FIDO2_TIMEOUT_SECONDS = 90.0
            private const val ES256_ALGORITHM_ID = -7
            private const val LEGACY_OPENSSH_APPLICATION = "ssh:"
            private val secureRandom = SecureRandom()
        }
    }

data class Fido2PocResult(
    val application: String,
    val rpId: String,
    val credentialIdBase64: String,
    val publicKeyBase64: String,
    val assertionFlags: Int,
    val assertionCounter: Long,
    val signatureSize: Int,
)

internal fun resolveRpIdForFido2(
    application: String,
    packageName: String,
): String {
    val normalized = application.trim()
    if (normalized.isBlank()) return packageName
    if (normalized == LEGACY_OPENSSH_APPLICATION || normalized == LEGACY_PLACEHOLDER_APPLICATION) {
        return packageName
    }
    if (normalized.startsWith(ANDROID_APK_KEY_HASH_PREFIX)) return packageName

    val parsed = runCatching { URI(normalized) }.getOrNull()
    val host = parsed?.host?.trim().orEmpty()
    if (host.isNotBlank()) return host.lowercase()

    return normalized
        .takeIf { looksLikeRpId(it) }
        ?.lowercase()
        ?: packageName
}

internal fun parseFido2AssertionSignatureMaterial(
    authenticatorData: ByteArray,
    derSignature: ByteArray,
): U2fSignatureMaterial? {
    if (authenticatorData.size < MIN_FIDO2_AUTH_DATA_SIZE) return null
    if (derSignature.isEmpty()) return null
    val flags = authenticatorData[FIDO2_AUTH_DATA_FLAGS_INDEX].toInt() and 0xFF
    val counter =
        ByteBuffer
            .wrap(
                authenticatorData,
                FIDO2_AUTH_DATA_COUNTER_START_INDEX,
                FIDO2_AUTH_DATA_COUNTER_LENGTH,
            ).order(ByteOrder.BIG_ENDIAN)
            .int
            .toLong()
            .and(0xFFFF_FFFFL)
    return U2fSignatureMaterial(
        flags = flags,
        counter = counter,
        derSignature = derSignature.copyOf(),
    )
}

internal fun extractCredentialPublicKeyUncompressedFromAttestationObject(attestationObject: ByteArray): ByteArray? {
    val reader = CborReader(attestationObject)
    val topLevelSize = reader.readMapSize() ?: return null
    var authData: ByteArray? = null
    repeat(topLevelSize) {
        val key = reader.readTextString() ?: return null
        if (key == CBOR_AUTH_DATA_KEY) {
            authData = reader.readByteString() ?: return null
        } else if (!reader.skipValue()) {
            return null
        }
    }
    return authData?.let(::extractCredentialPublicKeyUncompressedFromAuthData)
}

private fun extractCredentialPublicKeyUncompressedFromAuthData(authData: ByteArray): ByteArray? {
    if (authData.size < MIN_FIDO2_AUTH_DATA_SIZE + FIDO2_AAGUID_BYTES + FIDO2_CREDENTIAL_ID_LENGTH_BYTES) {
        return null
    }
    val flags = authData[FIDO2_AUTH_DATA_FLAGS_INDEX].toInt() and 0xFF
    val hasAttestedCredentialData = flags and FIDO2_FLAG_ATTESTED_CREDENTIAL_DATA != 0
    if (!hasAttestedCredentialData) return null

    var offset = MIN_FIDO2_AUTH_DATA_SIZE
    offset += FIDO2_AAGUID_BYTES
    if (offset + FIDO2_CREDENTIAL_ID_LENGTH_BYTES > authData.size) return null
    val credentialIdLength =
        ByteBuffer
            .wrap(
                authData,
                offset,
                FIDO2_CREDENTIAL_ID_LENGTH_BYTES,
            ).order(ByteOrder.BIG_ENDIAN)
            .short
            .toInt() and 0xFFFF
    offset += FIDO2_CREDENTIAL_ID_LENGTH_BYTES
    if (offset + credentialIdLength > authData.size) return null
    offset += credentialIdLength

    return parseCoseEc2PublicKeyUncompressed(
        source = authData,
        startOffset = offset,
    )
}

private fun parseCoseEc2PublicKeyUncompressed(
    source: ByteArray,
    startOffset: Int,
): ByteArray? {
    if (startOffset !in source.indices) return null
    val reader = CborReader(source = source, startOffset = startOffset)
    val mapSize = reader.readMapSize() ?: return null
    var xCoordinate: ByteArray? = null
    var yCoordinate: ByteArray? = null

    repeat(mapSize) {
        val key = reader.readInt() ?: return null
        when (key) {
            COSE_KEY_EC2_X -> xCoordinate = reader.readByteString()
            COSE_KEY_EC2_Y -> yCoordinate = reader.readByteString()
            else -> if (!reader.skipValue()) return null
        }
    }

    val x = xCoordinate ?: return null
    val y = yCoordinate ?: return null
    if (x.size != FIDO2_EC_COORDINATE_BYTES || y.size != FIDO2_EC_COORDINATE_BYTES) return null
    return byteArrayOf(FIDO2_UNCOMPRESSED_POINT_PREFIX) + x + y
}

private class CborReader(
    private val source: ByteArray,
    startOffset: Int = 0,
) {
    private var position: Int = startOffset

    fun readMapSize(): Int? {
        val (major, additional) = readHeader() ?: return null
        if (major != CBOR_MAJOR_MAP) return null
        return readLength(additional).toIntLengthOrNull()
    }

    fun readTextString(): String? {
        val (major, additional) = readHeader() ?: return null
        if (major != CBOR_MAJOR_TEXT_STRING) return null
        val length = readLength(additional).toIntLengthOrNull() ?: return null
        val bytes = readBytes(length) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    fun readByteString(): ByteArray? {
        val (major, additional) = readHeader() ?: return null
        if (major != CBOR_MAJOR_BYTE_STRING) return null
        val length = readLength(additional).toIntLengthOrNull() ?: return null
        return readBytes(length)
    }

    fun readInt(): Long? {
        val (major, additional) = readHeader() ?: return null
        val value = readLength(additional)
        return when (major) {
            CBOR_MAJOR_UNSIGNED_INTEGER -> value
            CBOR_MAJOR_NEGATIVE_INTEGER -> -1L - value
            else -> null
        }
    }

    fun skipValue(): Boolean {
        val (major, additional) = readHeader() ?: return false
        return when (major) {
            CBOR_MAJOR_UNSIGNED_INTEGER,
            CBOR_MAJOR_NEGATIVE_INTEGER,
            -> {
                readLength(additional) >= 0
            }

            CBOR_MAJOR_BYTE_STRING,
            CBOR_MAJOR_TEXT_STRING,
            -> {
                val byteLength = readLength(additional).toIntLengthOrNull() ?: return false
                skipBytes(byteLength)
            }

            CBOR_MAJOR_ARRAY -> {
                val itemCount = readLength(additional).toIntLengthOrNull() ?: return false
                repeat(itemCount) {
                    if (!skipValue()) return false
                }
                true
            }

            CBOR_MAJOR_MAP -> {
                val pairCount = readLength(additional).toIntLengthOrNull() ?: return false
                repeat(pairCount) {
                    if (!skipValue()) return false
                    if (!skipValue()) return false
                }
                true
            }

            CBOR_MAJOR_TAG -> {
                readLength(additional)
                skipValue()
            }

            CBOR_MAJOR_SIMPLE -> skipSimple(additional)
            else -> false
        }
    }

    private fun skipSimple(additional: Int): Boolean {
        return when (additional) {
            in 0..23 -> true
            24 -> skipBytes(1)
            25 -> skipBytes(2)
            26 -> skipBytes(4)
            27 -> skipBytes(8)
            else -> false
        }
    }

    private fun readHeader(): Pair<Int, Int>? {
        val initial = readByteAsInt() ?: return null
        return (initial ushr 5) to (initial and 0x1F)
    }

    private fun readLength(additional: Int): Long {
        return when (additional) {
            in 0..23 -> additional.toLong()
            24 -> readUnsignedBigEndian(1) ?: INVALID_LENGTH
            25 -> readUnsignedBigEndian(2) ?: INVALID_LENGTH
            26 -> readUnsignedBigEndian(4) ?: INVALID_LENGTH
            27 -> readUnsignedBigEndian(8) ?: INVALID_LENGTH
            else -> INVALID_LENGTH
        }
    }

    private fun readUnsignedBigEndian(length: Int): Long? {
        if (position + length > source.size) return null
        var value = 0L
        repeat(length) {
            val current = source[position].toLong() and 0xFF
            value = (value shl 8) or current
            position += 1
        }
        return value
    }

    private fun readBytes(length: Int): ByteArray? {
        if (length < 0) return null
        if (position + length > source.size) return null
        val value = source.copyOfRange(position, position + length)
        position += length
        return value
    }

    private fun skipBytes(length: Int): Boolean {
        if (length < 0) return false
        if (position + length > source.size) return false
        position += length
        return true
    }

    private fun readByteAsInt(): Int? {
        if (position >= source.size) return null
        val value = source[position].toInt() and 0xFF
        position += 1
        return value
    }

    private fun Long.toIntLengthOrNull(): Int? {
        return takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    }

    companion object {
        private const val INVALID_LENGTH = -1L
    }
}

private fun Intent?.extractFido2CredentialOrNull(): PublicKeyCredential? {
    val intent = this ?: return null
    val parcelableCredential =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA, PublicKeyCredential::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA) as? PublicKeyCredential
        }
    if (parcelableCredential != null) return parcelableCredential

    val serializedCredential = intent.getByteArrayExtra(Fido.FIDO2_KEY_CREDENTIAL_EXTRA) ?: return null
    return runCatching { PublicKeyCredential.deserializeFromBytes(serializedCredential) }.getOrNull()
}

private fun Intent?.extractFido2ErrorResponseOrNull(): AuthenticatorErrorResponse? {
    val intent = this ?: return null
    val parcelableError =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Fido.FIDO2_KEY_ERROR_EXTRA, AuthenticatorErrorResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Fido.FIDO2_KEY_ERROR_EXTRA) as? AuthenticatorErrorResponse
        }
    if (parcelableError != null) return parcelableError

    val serializedError = intent.getByteArrayExtra(Fido.FIDO2_KEY_ERROR_EXTRA) ?: return null
    return runCatching { AuthenticatorErrorResponse.deserializeFromBytes(serializedError) }.getOrNull()
}

private fun AuthenticatorErrorResponse?.toReasonOrNull(): String? {
    val response = this ?: return null
    val message = response.errorMessage?.takeIf { it.isNotBlank() }
    return message ?: "code=${response.errorCode}(${response.errorCodeAsInt})"
}

private class Fido2PocException(
    val code: ErrorCode?,
    override val message: String,
) : IllegalStateException(message)

private fun logFido2Failure(
    stage: String,
    rpId: String,
    code: ErrorCode?,
    message: String,
    throwable: Throwable? = null,
) {
    val codeLabel = code?.name ?: "NONE"
    val normalizedMessage = message.ifBlank { "no-message" }
    val logMessage = "stage=$stage rpId=$rpId code=$codeLabel message=$normalizedMessage"
    if (throwable == null) {
        Log.w(FIDO2_LOG_TAG, logMessage)
    } else {
        Log.w(FIDO2_LOG_TAG, logMessage, throwable)
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

private fun looksLikeRpId(value: String): Boolean {
    if (value.length !in MIN_RP_ID_LENGTH..MAX_RP_ID_LENGTH) return false
    if (!value.contains('.')) return false
    if (value.contains(':')) return false
    return value.all { character ->
        character.isLetterOrDigit() || character == '.' || character == '-'
    }
}

private const val FIDO2_LOG_TAG = "AndSSH-FIDO2"
private const val CBOR_AUTH_DATA_KEY = "authData"
private const val ANDROID_APK_KEY_HASH_PREFIX = "android:apk-key-hash:"
private const val LEGACY_OPENSSH_APPLICATION = "ssh:"
private const val LEGACY_PLACEHOLDER_APPLICATION = "https://andssh.local"
private const val MIN_RP_ID_LENGTH = 3
private const val MAX_RP_ID_LENGTH = 253

private const val MIN_FIDO2_AUTH_DATA_SIZE = 37
private const val FIDO2_AUTH_DATA_FLAGS_INDEX = 32
private const val FIDO2_AUTH_DATA_COUNTER_START_INDEX = 33
private const val FIDO2_AUTH_DATA_COUNTER_LENGTH = 4
private const val FIDO2_FLAG_ATTESTED_CREDENTIAL_DATA = 0x40
private const val FIDO2_AAGUID_BYTES = 16
private const val FIDO2_CREDENTIAL_ID_LENGTH_BYTES = 2
private const val FIDO2_EC_COORDINATE_BYTES = 32
private const val FIDO2_UNCOMPRESSED_POINT_PREFIX: Byte = 0x04

private const val COSE_KEY_EC2_X = -2L
private const val COSE_KEY_EC2_Y = -3L

private const val CBOR_MAJOR_UNSIGNED_INTEGER = 0
private const val CBOR_MAJOR_NEGATIVE_INTEGER = 1
private const val CBOR_MAJOR_BYTE_STRING = 2
private const val CBOR_MAJOR_TEXT_STRING = 3
private const val CBOR_MAJOR_ARRAY = 4
private const val CBOR_MAJOR_MAP = 5
private const val CBOR_MAJOR_TAG = 6
private const val CBOR_MAJOR_SIMPLE = 7
