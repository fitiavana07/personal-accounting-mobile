package dev.fitiavana.accounting.features.crypto

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import javax.crypto.AEADBadTagException

/**
 * AES-GCM with a fresh random 12-byte nonce per message, wire form: nonce, ciphertext, 16-byte tag.
 * Bouncy Castle's own GCM is used directly (not as a provider): the platform provider rejects
 * GCMParameterSpec before API 21. Shared by P2P sync and Cloud Backup.
 */
class AesGcm(private val key: ByteArray) {

    fun encrypt(plain: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return nonce + process(true, nonce, plain, 0, plain.size, associatedData)
    }

    /** Throws [AEADBadTagException] for a wrong key, other associated data, tampering or a too short message. */
    fun decrypt(data: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray {
        if (data.size < NONCE_BYTES + TAG_BITS / 8) throw AEADBadTagException("The message is too short")
        return try {
            process(false, data.copyOfRange(0, NONCE_BYTES), data, NONCE_BYTES, data.size - NONCE_BYTES, associatedData)
        } catch (e: InvalidCipherTextException) {
            throw AEADBadTagException("The message could not be authenticated")
        }
    }

    private fun process(
        encrypt: Boolean, nonce: ByteArray, input: ByteArray, offset: Int, length: Int, associatedData: ByteArray
    ): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG_BITS, nonce, associatedData))
        val out = ByteArray(cipher.getOutputSize(length))
        val written = cipher.processBytes(input, offset, length, out, 0)
        cipher.doFinal(out, written)
        return out
    }

    private companion object {
        val random = SecureRandom()
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** PBKDF2-HMAC-SHA256: the platform has none before API 26, hence Bouncy Castle. */
fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyBytes: Int): ByteArray {
    val generator = PKCS5S2ParametersGenerator(SHA256Digest())
    generator.init(password, salt, iterations)
    return (generator.generateDerivedParameters(keyBytes * 8) as KeyParameter).key
}
