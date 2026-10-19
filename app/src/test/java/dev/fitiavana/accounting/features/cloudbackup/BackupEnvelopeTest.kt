package dev.fitiavana.accounting.features.cloudbackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupEnvelopeTest {
    private val json = """{"schemaVersion":22,"accounts":[{"name":"Cash"}]}"""

    // A few iterations keep the tests fast; the iteration count travels in the header anyway.
    private fun encode(passphrase: String = "correct horse", text: String = json) =
        BackupEnvelope.encode(text, passphrase, iterations = 10)

    private fun reasonOf(block: () -> Unit): BackupEnvelopeException.Reason =
        try {
            block()
            throw AssertionError("expected a BackupEnvelopeException")
        } catch (e: BackupEnvelopeException) {
            e.reason
        }

    @Test
    fun `decodes what was encoded`() {
        assertEquals(json, BackupEnvelope.decode(encode(), "correct horse"))
    }

    @Test
    fun `keeps non ascii text`() {
        val text = """{"name":"Épargne – 日本"}"""

        assertEquals(text, BackupEnvelope.decode(encode(text = text), "correct horse"))
    }

    @Test
    fun `starts with the magic, the format version and the key derivation id`() {
        val data = encode()

        assertEquals("ACCBK", String(data, 0, 5, Charsets.US_ASCII))
        assertEquals(1, data[5].toInt())
        assertEquals(1, data[6].toInt())
    }

    @Test
    fun `the header holds the iteration count and a random salt`() {
        val first = encode()
        val second = encode()

        val iterations = java.nio.ByteBuffer.wrap(first, 7, 4).int
        assertEquals(10, iterations)
        assertFalse(first.copyOfRange(11, 27).contentEquals(second.copyOfRange(11, 27)))
    }

    @Test
    fun `the same backup encrypts differently each time`() {
        assertFalse(encode().contentEquals(encode()))
    }

    @Test
    fun `the json is not readable in the file`() {
        assertFalse(String(encode(), Charsets.ISO_8859_1).contains("schemaVersion"))
    }

    @Test
    fun `the payload is compressed`() {
        val big = """{"rows":[${(1..2000).joinToString(",") { """{"name":"Account $it"}""" }}]}"""

        assertTrue(encode(text = big).size < big.length / 3)
    }

    @Test
    fun `the payload is standard gzip inside standard AES-256-GCM, with the header as associated data`() {
        val data = encode()
        val header = data.copyOfRange(0, 27)
        val key = SecretKeySpec(
            dev.fitiavana.accounting.features.crypto.pbkdf2Sha256("correct horse".toByteArray(), header.copyOfRange(11, 27), 10, 32),
            "AES"
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(27, 39)))
        cipher.updateAAD(header)
        val gzip = cipher.doFinal(data, 39, data.size - 39)

        assertEquals(json, GZIPInputStream(ByteArrayInputStream(gzip)).readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `a wrong passphrase is reported`() {
        assertEquals(
            BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED,
            reasonOf { BackupEnvelope.decode(encode(), "another one") }
        )
    }

    @Test
    fun `a tampered ciphertext is reported`() {
        val data = encode()
        data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()

        assertEquals(
            BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED,
            reasonOf { BackupEnvelope.decode(data, "correct horse") }
        )
    }

    @Test
    fun `a tampered header is reported, even the iteration count`() {
        val data = encode()
        data[10] = (data[10].toInt() xor 1).toByte()

        assertEquals(
            BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED,
            reasonOf { BackupEnvelope.decode(data, "correct horse") }
        )
    }

    @Test
    fun `an absurd iteration count is refused instead of burning the cpu`() {
        val data = encode()
        java.nio.ByteBuffer.wrap(data, 7, 4).putInt(Int.MAX_VALUE)

        assertEquals(
            BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED,
            reasonOf { BackupEnvelope.decode(data, "correct horse") }
        )
    }

    @Test
    fun `a truncated file is reported`() {
        assertEquals(
            BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED,
            reasonOf { BackupEnvelope.decode(encode().copyOf(30), "correct horse") }
        )
    }

    @Test
    fun `a file without the magic is not an envelope`() {
        assertEquals(
            BackupEnvelopeException.Reason.NOT_AN_ENVELOPE,
            reasonOf { BackupEnvelope.decode(json.toByteArray(), "correct horse") }
        )
        assertEquals(
            BackupEnvelopeException.Reason.NOT_AN_ENVELOPE,
            reasonOf { BackupEnvelope.decode(ByteArray(0), "correct horse") }
        )
    }

    @Test
    fun `a newer format version is reported as unsupported`() {
        val data = encode()
        data[5] = 2

        assertEquals(
            BackupEnvelopeException.Reason.UNSUPPORTED_VERSION,
            reasonOf { BackupEnvelope.decode(data, "correct horse") }
        )
    }

    @Test
    fun `an unknown key derivation is reported as unsupported`() {
        val data = encode()
        data[6] = 9

        assertEquals(
            BackupEnvelopeException.Reason.UNSUPPORTED_VERSION,
            reasonOf { BackupEnvelope.decode(data, "correct horse") }
        )
    }

    @Test
    fun `isEnvelope recognises the magic only`() {
        assertTrue(BackupEnvelope.isEnvelope(encode()))
        assertFalse(BackupEnvelope.isEnvelope(json.toByteArray()))
        assertFalse(BackupEnvelope.isEnvelope(ByteArray(0)))
    }

    @Test
    fun `an empty backup text round trips`() {
        assertArrayEquals(ByteArray(0), BackupEnvelope.decode(encode(text = ""), "correct horse").toByteArray())
    }
}
