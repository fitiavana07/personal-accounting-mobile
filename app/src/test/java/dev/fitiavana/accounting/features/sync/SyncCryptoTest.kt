package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncCryptoTest {
    private val identity = SyncIdentity.RELEASE
    private val crypto = SyncCrypto("correct horse battery", identity)

    @Test
    fun pbkdf2MatchesTheRfc7914TestVector() {
        val key = SyncCrypto.pbkdf2Sha256("passwd".toByteArray(), "salt".toByteArray(), iterations = 1, keyBytes = 64)

        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            key.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun aMessageDecryptsToWhatWasEncrypted() {
        val plain = "the ledger".toByteArray()

        assertArrayEquals(plain, crypto.decrypt(crypto.encrypt(plain)))
    }

    @Test
    fun theWireFormIsStandardAesGcmSoItIsIndependentOfThePlatformProvider() {
        val plain = "the ledger".toByteArray()
        val data = crypto.encrypt(plain)
        val key = SecretKeySpec(
            SyncCrypto.pbkdf2Sha256(
                "correct horse battery".toByteArray(),
                identity.cryptoNamespace.toByteArray(),
                100_000,
                32,
            ),
            "AES",
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, data.copyOfRange(0, 12)))

        assertArrayEquals(plain, cipher.doFinal(data, 12, data.size - 12))
    }

    @Test
    fun theSamePlainTextEncryptsDifferentlyEachTime() {
        val plain = "same".toByteArray()

        assertFalse(crypto.encrypt(plain).contentEquals(crypto.encrypt(plain)))
    }

    @Test
    fun theWireFormIsNonceThenCiphertextThenTag() {
        val plain = ByteArray(10)

        assertEquals(12 + 10 + 16, crypto.encrypt(plain).size)
    }

    @Test
    fun anotherPassphraseCannotDecrypt() {
        val other = SyncCrypto("another passphrase", identity)

        assertThrows(SyncCrypto.AuthenticationException::class.java) {
            other.decrypt(crypto.encrypt("secret".toByteArray()))
        }
    }

    @Test
    fun aTamperedMessageIsRejected() {
        val data = crypto.encrypt("secret".toByteArray())
        data[data.size - 1] = (data[data.size - 1].toInt() xor 1).toByte()

        assertThrows(SyncCrypto.AuthenticationException::class.java) { crypto.decrypt(data) }
    }

    @Test
    fun aTooShortMessageIsRejected() {
        assertThrows(SyncCrypto.AuthenticationException::class.java) { crypto.decrypt(ByteArray(10)) }
    }

    @Test
    fun theGroupTagIsStableShortAndHexAndDependsOnThePassphrase() {
        val again = SyncCrypto("correct horse battery", identity)

        assertEquals(crypto.groupTag, again.groupTag)
        assertEquals(16, crypto.groupTag.length)
        assertEquals(true, crypto.groupTag.all { it in "0123456789abcdef" })
        assertNotEquals(crypto.groupTag, SyncCrypto("another passphrase", identity).groupTag)
    }

    @Test
    fun aBlankPassphraseIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { SyncCrypto("   ", identity) }
    }

    @Test
    fun debugAndReleaseNeverShareAKeyNorAGroupEvenWithTheSamePassphrase() {
        val release = SyncCrypto("same passphrase", SyncIdentity.RELEASE)
        val debug = SyncCrypto("same passphrase", SyncIdentity.DEBUG)

        assertNotEquals(release.groupTag, debug.groupTag)
        assertThrows(SyncCrypto.AuthenticationException::class.java) {
            release.decrypt(debug.encrypt("secret".toByteArray()))
        }
    }
}
