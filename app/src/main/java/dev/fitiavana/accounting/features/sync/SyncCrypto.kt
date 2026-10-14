package dev.fitiavana.accounting.features.sync

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.modes.GCMModeCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
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
 * The derivation is deliberately slow, so create one instance per passphrase and reuse it. The
 * platform has no PBKDF2-SHA256 before API 26, hence Bouncy Castle (used directly, not as a provider).
 */
class SyncCrypto(passphrase: String, identity: SyncIdentity) {
    class AuthenticationException(message: String) : Exception(message)

    private val key: SecretKeySpec

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
        key = SecretKeySpec(derived, "AES")
        val tag = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(derived, "HmacSHA256"))
            doFinal("${identity.cryptoNamespace}-group".toByteArray())
        }
        groupTag = tag.take(GROUP_TAG_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** The nonce followed by the ciphertext and its authentication tag. */
    fun encrypt(plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return nonce + gcm(true, nonce).process(plain, 0, plain.size)
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (data.size < NONCE_BYTES + TAG_BITS / 8) throw AuthenticationException("The message is too short")
        return try {
            gcm(false, data.copyOfRange(0, NONCE_BYTES)).process(data, NONCE_BYTES, data.size - NONCE_BYTES)
        } catch (e: InvalidCipherTextException) {
            throw AuthenticationException("The message could not be authenticated")
        }
    }

    /** Bouncy Castle's own GCM: the platform provider rejects GCMParameterSpec before API 21. */
    private fun gcm(encrypt: Boolean, nonce: ByteArray) = GCMBlockCipher.newInstance(AESEngine.newInstance()).apply {
        init(encrypt, AEADParameters(KeyParameter(key.encoded), TAG_BITS, nonce))
    }

    private fun GCMModeCipher.process(input: ByteArray, offset: Int, length: Int): ByteArray {
        val out = ByteArray(getOutputSize(length))
        val written = processBytes(input, offset, length, out, 0)
        doFinal(out, written)
        return out
    }

    companion object {
        private val random = SecureRandom()
        private const val ITERATIONS = 100_000
        private const val KEY_BITS = 256
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val GROUP_TAG_BYTES = 8

        internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(password, salt, iterations)
            return (generator.generateDerivedParameters(keyBytes * 8) as KeyParameter).key
        }
    }
}
