package com.opencode.sshterminal.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts backup payloads with a user-supplied password.
 */
@Singleton
class PasswordBasedEncryptionManager
    @Inject
    constructor() {
        private val secureRandom = SecureRandom()

        fun encrypt(
            plaintext: String,
            password: CharArray,
        ): String {
            val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            val key = deriveAesKey(password = password, salt = salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(1 + salt.size + 1 + iv.size + ciphertext.size)
            var offset = 0
            combined[offset++] = salt.size.toByte()
            System.arraycopy(salt, 0, combined, offset, salt.size)
            offset += salt.size
            combined[offset++] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, offset, iv.size)
            offset += iv.size
            System.arraycopy(ciphertext, 0, combined, offset, ciphertext.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decrypt(
            encoded: String,
            password: CharArray,
        ): String {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            var offset = 0
            require(combined.size >= 2) { "Invalid encrypted payload" }

            val saltLength = combined[offset++].toInt() and 0xFF
            require(saltLength == SALT_BYTES && combined.size >= 1 + saltLength + 1) {
                "Invalid encrypted payload"
            }
            val salt = combined.copyOfRange(offset, offset + saltLength)
            offset += saltLength

            val ivLength = combined[offset++].toInt() and 0xFF
            require(ivLength == IV_BYTES && combined.size > offset + ivLength) {
                "Invalid encrypted payload"
            }
            val iv = combined.copyOfRange(offset, offset + ivLength)
            offset += ivLength

            val ciphertext = combined.copyOfRange(offset, combined.size)
            val key = deriveAesKey(password = password, salt = salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }

        private fun deriveAesKey(
            password: CharArray,
            salt: ByteArray,
        ): SecretKeySpec {
            val keySpec =
                PBEKeySpec(
                    password,
                    salt,
                    PBKDF2_ITERATIONS,
                    AES_KEY_BITS,
                )
            return try {
                val keyBytes =
                    SecretKeyFactory.getInstance(KDF_ALGORITHM)
                        .generateSecret(keySpec)
                        .encoded
                SecretKeySpec(keyBytes, AES_ALGORITHM)
            } finally {
                keySpec.clearPassword()
            }
        }

        companion object {
            const val PBKDF2_ITERATIONS = 600_000

            private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
            private const val AES_ALGORITHM = "AES"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val AES_KEY_BITS = 256
            private const val SALT_BYTES = 32
            private const val IV_BYTES = 12
            private const val GCM_TAG_BITS = 128
        }
    }
