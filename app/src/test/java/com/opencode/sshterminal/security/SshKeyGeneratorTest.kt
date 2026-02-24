package com.opencode.sshterminal.security

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class SshKeyGeneratorTest {
    @Test
    fun `generates valid pem for all algorithms`() {
        SshKeyAlgorithm.entries.forEach { algorithm ->
            val pem = SshKeyGenerator.generatePrivateKeyPem(algorithm)
            assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"))
            assertTrue(pem.trimEnd().endsWith("-----END PRIVATE KEY-----"))
        }
    }

    @Test
    fun `generated pem is parseable as pkcs8`() {
        SshKeyAlgorithm.entries.forEach { algorithm ->
            val pem = SshKeyGenerator.generatePrivateKeyPem(algorithm)
            val pkcs8 = parsePkcs8FromPem(pem)
            val keyFactory =
                KeyFactory.getInstance(
                    when (algorithm) {
                        SshKeyAlgorithm.ED25519 -> "Ed25519"
                        SshKeyAlgorithm.RSA -> "RSA"
                        SshKeyAlgorithm.ECDSA -> "EC"
                    },
                )
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(pkcs8))
            assertNotNull(privateKey)
        }
    }

    private fun parsePkcs8FromPem(pem: String): ByteArray {
        val body =
            pem.lineSequence()
                .filter { line -> line.isNotBlank() && !line.startsWith("-----") }
                .joinToString(separator = "")
        return Base64.getDecoder().decode(body)
    }
}
