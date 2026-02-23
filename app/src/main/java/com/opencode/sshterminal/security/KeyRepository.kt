package com.opencode.sshterminal.security

interface KeyRepository {
    suspend fun saveEncryptedPrivateKey(
        alias: String,
        privateKeyPem: ByteArray,
    )

    suspend fun loadEncryptedPrivateKey(alias: String): ByteArray?

    suspend fun delete(alias: String)
}
