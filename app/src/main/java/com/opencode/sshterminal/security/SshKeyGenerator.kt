package com.opencode.sshterminal.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
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

object SshKeyGenerator {
    fun generatePrivateKeyPem(algorithm: SshKeyAlgorithm): String {
        val keyPairGenerator =
            when (algorithm) {
                SshKeyAlgorithm.ED25519 -> KeyPairGenerator.getInstance("Ed25519", BouncyCastleProvider())
                SshKeyAlgorithm.RSA -> KeyPairGenerator.getInstance("RSA")
                SshKeyAlgorithm.ECDSA -> KeyPairGenerator.getInstance("EC")
            }
        when (algorithm) {
            SshKeyAlgorithm.ED25519 -> Unit
            SshKeyAlgorithm.RSA -> keyPairGenerator.initialize(4096, SecureRandom())
            SshKeyAlgorithm.ECDSA -> keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        }
        val privateKey = keyPairGenerator.generateKeyPair().private.encoded
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

    private const val PEM_BEGIN = "-----BEGIN PRIVATE KEY-----"
    private const val PEM_END = "-----END PRIVATE KEY-----"
}
