package dev.fitiavana.accounting.features.cloudbackup

import dev.fitiavana.accounting.features.crypto.AesGcm
import dev.fitiavana.accounting.features.crypto.pbkdf2Sha256
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException

/** Why a cloud backup file could not be opened. */
class BackupEnvelopeException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason {
        /** Not a Cloud Backup file at all (no magic). */
        NOT_AN_ENVELOPE,

        /** Written by a newer app: a format version or key derivation this app does not know. */
        UNSUPPORTED_VERSION,

        /** AES-GCM cannot tell a wrong passphrase from damage: both fail authentication. */
        WRONG_PASSPHRASE_OR_DAMAGED
    }
}

/**
 * The `.accbak` file format: a versioned header, then the backup JSON gzipped and encrypted with a key
 * derived from the passphrase.
 *
 * ```
 * "ACCBK" | version u8 (1) | kdf u8 (1 = PBKDF2-HMAC-SHA256) | iterations u32 | salt 16 | nonce 12 | AES-256-GCM(gzip(json)) + tag
 * ```
 *
 * The 27 header bytes are authenticated as GCM associated data, so changing the iteration count (or
 * anything else) fails like a wrong passphrase. A fresh salt and nonce are drawn for every file.
 */
object BackupEnvelope {
    const val DEFAULT_ITERATIONS = 100_000

    private val MAGIC = "ACCBK".toByteArray(Charsets.US_ASCII)
    private const val FORMAT_VERSION = 1
    private const val KDF_PBKDF2_SHA256 = 1
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32
    private const val HEADER_BYTES = 5 + 1 + 1 + 4 + SALT_BYTES

    /** Above this a header is not trusted: it would only make opening the file burn the cpu. */
    private const val MAX_ITERATIONS = 2_000_000

    private val random = SecureRandom()

    fun isEnvelope(data: ByteArray): Boolean = data.size >= MAGIC.size && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun encode(json: String, passphrase: String, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT_VERSION.toByte()).put(KDF_PBKDF2_SHA256.toByte()).putInt(iterations).put(salt)
            .array()
        val key = pbkdf2Sha256(passphrase.toByteArray(Charsets.UTF_8), salt, iterations, KEY_BYTES)
        return header + AesGcm(key).encrypt(gzip(json.toByteArray(Charsets.UTF_8)), header)
    }

    /** Returns the backup JSON, or throws a [BackupEnvelopeException] telling why not. */
    fun decode(data: ByteArray, passphrase: String): String {
        if (!isEnvelope(data)) throw BackupEnvelopeException(BackupEnvelopeException.Reason.NOT_AN_ENVELOPE, "Not a Cloud Backup file")
        if (data.size < HEADER_BYTES) throw damaged()
        val version = data[5].toInt()
        val kdf = data[6].toInt()
        if (version != FORMAT_VERSION || kdf != KDF_PBKDF2_SHA256) {
            throw BackupEnvelopeException(
                BackupEnvelopeException.Reason.UNSUPPORTED_VERSION, "Unsupported format $version (key derivation $kdf)"
            )
        }
        val iterations = ByteBuffer.wrap(data, 7, 4).int
        if (iterations !in 1..MAX_ITERATIONS) throw damaged()
        val header = data.copyOfRange(0, HEADER_BYTES)
        val key = pbkdf2Sha256(passphrase.toByteArray(Charsets.UTF_8), header.copyOfRange(11, HEADER_BYTES), iterations, KEY_BYTES)
        return try {
            gunzip(AesGcm(key).decrypt(data.copyOfRange(HEADER_BYTES, data.size), header)).toString(Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw damaged()
        } catch (e: java.io.IOException) {
            // Authenticated but not gzip: written by something that is not this app.
            throw damaged()
        }
    }

    private fun damaged() =
        BackupEnvelopeException(BackupEnvelopeException.Reason.WRONG_PASSPHRASE_OR_DAMAGED, "Wrong passphrase, or the file is damaged")

    private fun gzip(plain: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
