package com.opencode.sshterminal.security

import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.util.Base64

enum class SshKeyAlgorithm(
    val fileNameSuffix: String,
) {
    ED25519(fileNameSuffix = "ed25519"),
    RSA(fileNameSuffix = "rsa"),
    ECDSA(fileNameSuffix = "ecdsa"),
}

data class SshKeyMaterial(
    val privateKeyPem: String,
    val publicKeyAuthorized: String,
)

object SshKeyGenerator {
    fun generateSshKeyMaterial(
        algorithm: SshKeyAlgorithm,
        comment: String = "andssh",
    ): SshKeyMaterial {
        val keyPair = generateKeyPair(algorithm)
        val privateKeyPem = encodePrivateKeyToPem(keyPair.private.encoded)
        val publicKeyAuthorized = encodePublicKeyToAuthorizedKeyLine(keyPair.public, comment)
        return SshKeyMaterial(
            privateKeyPem = privateKeyPem,
            publicKeyAuthorized = publicKeyAuthorized,
        )
    }

    fun generatePrivateKeyPem(algorithm: SshKeyAlgorithm): String {
        return generateSshKeyMaterial(algorithm).privateKeyPem
    }

    private fun generateKeyPair(algorithm: SshKeyAlgorithm) =
        when (algorithm) {
            SshKeyAlgorithm.ED25519 -> {
                val generator = KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider())
                generator.generateKeyPair()
            }
            SshKeyAlgorithm.RSA -> {
                val generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(4096, SecureRandom())
                generator.generateKeyPair()
            }
            SshKeyAlgorithm.ECDSA -> {
                val generator = KeyPairGenerator.getInstance("EC")
                generator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
                generator.generateKeyPair()
            }
        }

    private fun encodePrivateKeyToPem(privateKey: ByteArray): String {
        val payload = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey)
        return buildString {
            append(PEM_BEGIN)
            append('\n')
            append(payload)
            append('\n')
            append(PEM_END)
            append('\n')
        }
    }

    private fun encodePublicKeyToAuthorizedKeyLine(
        publicKey: PublicKey,
        comment: String,
    ): String {
        val keyParameter = PublicKeyFactory.createKey(publicKey.encoded)
        val openSshBlob = OpenSSHPublicKeyUtil.encodePublicKey(keyParameter)
        val keyType = readSshString(openSshBlob, 0).first
        val encoded = Base64.getEncoder().encodeToString(openSshBlob)
        return "$keyType $encoded $comment".trim()
    }

    private fun readSshString(
        blob: ByteArray,
        start: Int,
    ): Pair<String, Int> {
        require(blob.size >= start + 4) { "Invalid SSH blob: missing length prefix" }
        val length =
            ((blob[start].toInt() and 0xFF) shl 24) or
                ((blob[start + 1].toInt() and 0xFF) shl 16) or
                ((blob[start + 2].toInt() and 0xFF) shl 8) or
                (blob[start + 3].toInt() and 0xFF)
        require(length >= 0 && blob.size >= start + 4 + length) { "Invalid SSH blob: truncated string" }
        val value = blob.copyOfRange(start + 4, start + 4 + length).toString(Charsets.UTF_8)
        return value to (start + 4 + length)
    }

    private const val PEM_BEGIN = "-----BEGIN PRIVATE KEY-----"
    private const val PEM_END = "-----END PRIVATE KEY-----"
}
