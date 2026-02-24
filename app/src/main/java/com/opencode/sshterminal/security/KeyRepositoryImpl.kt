package com.opencode.sshterminal.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : KeyRepository {
        private val keyDirectory = File(context.filesDir, KEY_DIRECTORY_NAME)

        override suspend fun saveEncryptedPrivateKey(
            alias: String,
            privateKeyPem: ByteArray,
        ) {
            val plaintextCopy = privateKeyPem.copyOf()
            var iv: ByteArray? = null
            var encryptedBytes: ByteArray? = null
            var combined: ByteArray? = null
            ensureDirectoryExists()
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                val ivBytes = cipher.iv
                val encrypted = cipher.doFinal(plaintextCopy)
                iv = ivBytes
                encryptedBytes = encrypted

                combined = ByteArray(1 + ivBytes.size + encrypted.size)
                combined[0] = ivBytes.size.toByte()
                System.arraycopy(ivBytes, 0, combined, 1, ivBytes.size)
                System.arraycopy(encrypted, 0, combined, 1 + ivBytes.size, encrypted.size)

                fileForAlias(alias).writeBytes(combined)
            } finally {
                plaintextCopy.zeroize()
                iv?.zeroize()
                encryptedBytes?.zeroize()
                combined?.zeroize()
            }
        }

        override suspend fun loadEncryptedPrivateKey(alias: String): ByteArray? {
            val file = fileForAlias(alias)
            if (!file.exists()) {
                return null
            }

            return try {
                val combined = file.readBytes()
                if (combined.isEmpty()) return null
                try {
                    val ivLen = combined[0].toInt() and 0xFF
                    if (ivLen <= 0 || combined.size <= 1 + ivLen) {
                        return null
                    }

                    val iv = combined.copyOfRange(1, 1 + ivLen)
                    val ciphertext = combined.copyOfRange(1 + ivLen, combined.size)
                    try {
                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                        cipher.doFinal(ciphertext)
                    } finally {
                        iv.zeroize()
                        ciphertext.zeroize()
                    }
                } finally {
                    combined.zeroize()
                }
            } catch (_: IOException) {
                null
            } catch (_: GeneralSecurityException) {
                null
            }
        }

        override suspend fun delete(alias: String) {
            fileForAlias(alias).delete()
        }

        override suspend fun listAliases(): List<String> {
            if (!keyDirectory.exists() || !keyDirectory.isDirectory) {
                return emptyList()
            }

            return keyDirectory
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(FILE_EXTENSION) }
                .map { it.name.removeSuffix(FILE_EXTENSION) }
                .sorted()
        }

        private fun ensureDirectoryExists() {
            if (!keyDirectory.exists()) {
                keyDirectory.mkdirs()
            }
        }

        private fun fileForAlias(alias: String): File {
            return File(keyDirectory, "$alias$FILE_EXTENSION")
        }

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.getEntry(KEY_ALIAS, null)?.let { entry ->
                return (entry as KeyStore.SecretKeyEntry).secretKey
            }

            val keyGenerator =
                KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER,
                )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            return keyGenerator.generateKey()
        }

        companion object {
            private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
            private const val KEY_ALIAS = "ssh_key_storage_key"
            private const val TRANSFORMATION = "AES/GCM/NoPadding"
            private const val KEY_SIZE_BITS = 256
            private const val GCM_TAG_BITS = 128
            private const val KEY_DIRECTORY_NAME = "encrypted_keys"
            private const val FILE_EXTENSION = ".enc"
        }
    }
