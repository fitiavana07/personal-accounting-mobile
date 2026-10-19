package dev.fitiavana.accounting.features.sync

import dev.fitiavana.accounting.features.crypto.AesGcm
import dev.fitiavana.accounting.features.crypto.pbkdf2Sha256
import javax.crypto.AEADBadTagException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts and authenticates what two devices exchange, with a key derived from the shared
 * passphrase (PBKDF2-HMAC-SHA256, then AES-256-GCM with a fresh random nonce per message). A message
 * that does not decrypt means a wrong passphrase or tampering, and is never used.
 *
 * The salt and the group label come from [SyncIdentity], so a debug and a release build never share
 * a key or a group, even with the same passphrase.
 *
 * The derivation is deliberately slow, so create one instance per passphrase and reuse it. The cipher
 * itself is the shared [AesGcm].
 */
class SyncCrypto(passphrase: String, identity: SyncIdentity) {
    class AuthenticationException(message: String) : Exception(message)

    private val aesGcm: AesGcm

    /** Identifies the passphrase without revealing it, so devices of the same group find each other. */
    val groupTag: String

    init {
        require(passphrase.isNotBlank()) { "The passphrase must not be blank" }
        val derived = pbkdf2Sha256(
            passphrase.toByteArray(Charsets.UTF_8),
            identity.cryptoNamespace.toByteArray(),
            ITERATIONS,
            KEY_BITS / 8,
        )
        aesGcm = AesGcm(derived)
        val tag = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(derived, "HmacSHA256"))
            doFinal("${identity.cryptoNamespace}-group".toByteArray())
        }
        groupTag = tag.take(GROUP_TAG_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** The nonce followed by the ciphertext and its authentication tag. */
    fun encrypt(plain: ByteArray): ByteArray = aesGcm.encrypt(plain)

    fun decrypt(data: ByteArray): ByteArray =
        try {
            aesGcm.decrypt(data)
        } catch (e: AEADBadTagException) {
            throw AuthenticationException(e.message ?: "The message could not be authenticated")
        }

    companion object {
        private const val ITERATIONS = 100_000
        private const val KEY_BITS = 256
        private const val GROUP_TAG_BYTES = 8

        internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray =
            dev.fitiavana.accounting.features.crypto.pbkdf2Sha256(password, salt, iterations, keyBytes)
    }
}
