package com.opencode.sshterminal.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRepositoryBlobFormatTest {
    @Test
    fun `encode and decode encrypted blob round-trip`() {
        val iv = byteArrayOf(1, 2, 3, 4)
        val ciphertext = byteArrayOf(11, 12, 13)

        val encoded = encodeEncryptedPrivateKeyBlob(iv = iv, ciphertext = ciphertext)
        val decoded = requireNotNull(decodeEncryptedPrivateKeyBlob(encoded))

        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
        assertEquals(1 + iv.size + ciphertext.size, encoded.size)
        assertEquals(iv.size, encoded[0].toInt() and 0xFF)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encodeEncryptedPrivateKeyBlob rejects empty iv`() {
        encodeEncryptedPrivateKeyBlob(iv = byteArrayOf(), ciphertext = byteArrayOf(1))
    }

    @Test
    fun `decodeEncryptedPrivateKeyBlob returns null for invalid payloads`() {
        assertNull(decodeEncryptedPrivateKeyBlob(byteArrayOf()))
        assertNull(decodeEncryptedPrivateKeyBlob(byteArrayOf(0)))
        assertNull(decodeEncryptedPrivateKeyBlob(byteArrayOf(4, 1, 2, 3, 4)))
    }
}
