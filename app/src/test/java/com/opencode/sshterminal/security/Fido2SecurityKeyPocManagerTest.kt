package com.opencode.sshterminal.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Fido2SecurityKeyPocManagerTest {
    @Test
    fun `resolveRpId uses host for https application`() {
        val rpId =
            resolveRpIdForFido2(
                application = "https://bastion.example.com:8443/login",
                packageName = "com.opencode.sshterminal",
            )

        assertEquals("bastion.example.com", rpId)
    }

    @Test
    fun `resolveRpId falls back to package name for android app hash`() {
        val rpId =
            resolveRpIdForFido2(
                application = "android:apk-key-hash:sample",
                packageName = "com.opencode.sshterminal",
            )

        assertEquals("com.opencode.sshterminal", rpId)
    }

    @Test
    fun `parseFido2 assertion material decodes flags and counter`() {
        val authData =
            ByteArray(37).also { bytes ->
                bytes[32] = 0x01
                bytes[33] = 0x00
                bytes[34] = 0x00
                bytes[35] = 0x01
                bytes[36] = 0x01
            }
        val derSignature = byteArrayOf(0x30, 0x44)

        val parsed =
            parseFido2AssertionSignatureMaterial(
                authenticatorData = authData,
                derSignature = derSignature,
            )

        requireNotNull(parsed)
        assertEquals(1, parsed.flags)
        assertEquals(257L, parsed.counter)
        assertArrayEquals(derSignature, parsed.derSignature)
    }

    @Test
    fun `parseFido2 assertion material returns null for short auth data`() {
        val parsed =
            parseFido2AssertionSignatureMaterial(
                authenticatorData = ByteArray(10),
                derSignature = byteArrayOf(0x30),
            )

        assertNull(parsed)
    }

    @Test
    fun `extract credential public key from attestation object`() {
        val xCoordinate = ByteArray(32) { 0x11 }
        val yCoordinate = ByteArray(32) { 0x22 }
        val credentialId = byteArrayOf(0x10, 0x11, 0x12, 0x13)
        val coseKey =
            cborMap(
                listOf(
                    cborUnsigned(1) to cborUnsigned(2),
                    cborUnsigned(3) to cborNegative(-7),
                    cborNegative(-1) to cborUnsigned(1),
                    cborNegative(-2) to cborByteString(xCoordinate),
                    cborNegative(-3) to cborByteString(yCoordinate),
                ),
            )
        val authData =
            ByteArray(32) + // rpIdHash
                byteArrayOf(0x41) + // flags (UP + AT)
                byteArrayOf(0x00, 0x00, 0x00, 0x02) + // signCount
                ByteArray(16) + // aaguid
                byteArrayOf(0x00, credentialId.size.toByte()) +
                credentialId +
                coseKey
        val attestationObject =
            cborMap(
                listOf(
                    cborTextString("fmt") to cborTextString("none"),
                    cborTextString("attStmt") to cborMap(emptyList()),
                    cborTextString("authData") to cborByteString(authData),
                ),
            )

        val parsed = extractCredentialPublicKeyUncompressedFromAttestationObject(attestationObject)

        val expected = byteArrayOf(0x04) + xCoordinate + yCoordinate
        assertArrayEquals(expected, parsed)
    }
}

private fun cborMap(entries: List<Pair<ByteArray, ByteArray>>): ByteArray {
    val header = cborTypeHeader(majorType = 5, length = entries.size.toLong())
    val payload =
        entries.fold(ByteArray(0)) { accumulator, (key, value) ->
            accumulator + key + value
        }
    return header + payload
}

private fun cborTextString(value: String): ByteArray {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return cborTypeHeader(majorType = 3, length = bytes.size.toLong()) + bytes
}

private fun cborByteString(value: ByteArray): ByteArray {
    return cborTypeHeader(majorType = 2, length = value.size.toLong()) + value
}

private fun cborUnsigned(value: Long): ByteArray {
    require(value >= 0) { "Unsigned CBOR value must be >= 0" }
    return cborTypeHeader(majorType = 0, length = value)
}

private fun cborNegative(value: Long): ByteArray {
    require(value < 0) { "Negative CBOR value must be < 0" }
    val encodedMagnitude = -1L - value
    return cborTypeHeader(majorType = 1, length = encodedMagnitude)
}

private fun cborTypeHeader(
    majorType: Int,
    length: Long,
): ByteArray {
    require(majorType in 0..7) { "Invalid CBOR major type: $majorType" }
    require(length >= 0) { "CBOR length must be >= 0" }
    return when {
        length <= 23L -> byteArrayOf(((majorType shl 5) or length.toInt()).toByte())
        length <= 0xFFL -> byteArrayOf(((majorType shl 5) or 24).toByte(), length.toByte())
        length <= 0xFFFFL ->
            byteArrayOf(
                ((majorType shl 5) or 25).toByte(),
                ((length ushr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte(),
            )

        else ->
            byteArrayOf(
                ((majorType shl 5) or 26).toByte(),
                ((length ushr 24) and 0xFF).toByte(),
                ((length ushr 16) and 0xFF).toByte(),
                ((length ushr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte(),
            )
    }
}
