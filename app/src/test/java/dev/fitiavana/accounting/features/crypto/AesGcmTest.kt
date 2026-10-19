package dev.fitiavana.accounting.features.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class AesGcmTest {
    private val aesGcm = AesGcm(ByteArray(32) { it.toByte() })

    @Test
    fun `decrypts what was encrypted`() {
        val plain = "the ledger".toByteArray()

        assertArrayEquals(plain, aesGcm.decrypt(aesGcm.encrypt(plain)))
    }

    @Test
    fun `the wire form is nonce, ciphertext, tag`() {
        assertEquals(12 + 10 + 16, aesGcm.encrypt(ByteArray(10)).size)
    }

    @Test
    fun `the same plain text encrypts differently each time`() {
        val plain = "same".toByteArray()

        assertFalse(aesGcm.encrypt(plain).contentEquals(aesGcm.encrypt(plain)))
    }

    @Test
    fun `the associated data must match`() {
        val data = aesGcm.encrypt("secret".toByteArray(), associatedData = "header".toByteArray())

        assertArrayEquals("secret".toByteArray(), aesGcm.decrypt(data, associatedData = "header".toByteArray()))
        assertThrows(AEADBadTagException::class.java) { aesGcm.decrypt(data, associatedData = "other".toByteArray()) }
        assertThrows(AEADBadTagException::class.java) { aesGcm.decrypt(data) }
    }

    @Test
    fun `another key cannot decrypt`() {
        val other = AesGcm(ByteArray(32) { 1 })

        assertThrows(AEADBadTagException::class.java) { other.decrypt(aesGcm.encrypt("secret".toByteArray())) }
    }

    @Test
    fun `a tampered message is rejected`() {
        val data = aesGcm.encrypt("secret".toByteArray())
        data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()

        assertThrows(AEADBadTagException::class.java) { aesGcm.decrypt(data) }
    }

    @Test
    fun `a too short message is rejected`() {
        assertThrows(AEADBadTagException::class.java) { aesGcm.decrypt(ByteArray(10)) }
    }

    @Test
    fun `pbkdf2 matches the RFC 7914 test vector`() {
        val key = pbkdf2Sha256("passwd".toByteArray(), "salt".toByteArray(), iterations = 1, keyBytes = 64)

        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            key.joinToString("") { "%02x".format(it) }
        )
    }
}
