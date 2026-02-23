package com.opencode.sshterminal.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts arbitrary strings using AES-256-GCM backed by Android Keystore.
 * Key never leaves the hardware-backed keystore.
 */
@Singleton
class EncryptionManager
    @Inject
    constructor() {
        private fun getOrCreateKey(): SecretKey {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            ks.getEntry(KEY_ALIAS, null)?.let { entry ->
                return (entry as KeyStore.SecretKeyEntry).secretKey
            }
            val generator =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER,
                )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }

        fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decrypt(encoded: String): String {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        }

        companion object {
            private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
            private const val KEY_ALIAS = "ssh_data_key"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val GCM_TAG_BITS = 128
        }
    }
