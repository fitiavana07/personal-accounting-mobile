package dev.fitiavana.accounting.features.sync

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Why a session did not sync, in terms the user can act on. */
sealed interface SessionFailure {
    /** The other device hung up without answering: most likely it uses another passphrase. */
    object NoAnswer : SessionFailure

    /** What was received could not be decrypted: another passphrase, or tampering. */
    object WrongPassphrase : SessionFailure

    /** The other app speaks another protocol version. */
    data class Incompatible(val message: String) : SessionFailure

    /** The other device could not use our state, or we could not use its. */
    data class Refused(val message: String) : SessionFailure

    /** What arrived was not a valid message. */
    data class Invalid(val message: String) : SessionFailure

    /** The connection failed or timed out. */
    data class Network(val message: String) : SessionFailure
}

sealed interface SessionResult {
    /** Both devices now hold the merge; [changed] tells whether this device's data is different now. */
    data class Synced(val peer: PeerInfo, val changed: Boolean) : SessionResult

    data class Failed(val failure: SessionFailure) : SessionResult
}

/**
 * One sync exchange over an already open connection, in one round trip: the initiator sends its
 * state, the responder merges it and answers with the merged state, which the initiator merges in
 * turn. Every message is encrypted with the shared passphrase.
 *
 * The streams and the repository block: run these on an IO thread, with a read timeout on the socket.
 */
class SyncSession(
    private val repository: SyncRepository,
    private val crypto: SyncCrypto,
    private val self: PeerInfo,
) {
    fun initiate(input: InputStream, output: OutputStream): SessionResult {
        try {
            send(output, SyncMessage.Request(self, repository.snapshot()))
        } catch (e: IOException) {
            return network(e)
        }
        val plain = try {
            crypto.decrypt(FrameIO.read(input))
        } catch (e: EOFException) {
            return SessionResult.Failed(SessionFailure.NoAnswer)
        } catch (e: IOException) {
            return network(e)
        } catch (e: SyncCrypto.AuthenticationException) {
            return SessionResult.Failed(SessionFailure.WrongPassphrase)
        }
        return when (val message = decode(plain)) {
            is Decoded.Failed -> SessionResult.Failed(message.failure)
            is Decoded.Message -> when (val reply = message.message) {
                is SyncMessage.Failure -> SessionResult.Failed(SessionFailure.Refused(reply.message))
                is SyncMessage.Response -> when (val result = repository.apply(reply.snapshot)) {
                    is ApplyResult.Applied -> SessionResult.Synced(reply.peer, result.changed)
                    is ApplyResult.Rejected -> SessionResult.Failed(SessionFailure.Refused(result.message))
                }
                is SyncMessage.Request -> SessionResult.Failed(SessionFailure.Invalid("Unexpected message"))
            }
        }
    }

    fun respond(input: InputStream, output: OutputStream): SessionResult {
        val plain = try {
            crypto.decrypt(FrameIO.read(input))
        } catch (e: IOException) {
            return network(e)
        } catch (e: SyncCrypto.AuthenticationException) {
            // Without the passphrase there is nobody to answer to: hang up.
            return SessionResult.Failed(SessionFailure.WrongPassphrase)
        }
        val request = when (val decoded = decode(plain)) {
            is Decoded.Failed -> return refuse(output, decoded.failure)
            is Decoded.Message -> decoded.message as? SyncMessage.Request
                ?: return refuse(output, SessionFailure.Invalid("Unexpected message"))
        }
        return when (val result = repository.apply(request.snapshot)) {
            is ApplyResult.Rejected -> refuse(output, SessionFailure.Refused(result.message))
            is ApplyResult.Applied -> {
                // The merge is done whether or not the answer gets through; the initiator can retry.
                runCatching { send(output, SyncMessage.Response(self, repository.snapshot())) }
                SessionResult.Synced(request.peer, result.changed)
            }
        }
    }

    private sealed interface Decoded {
        data class Message(val message: SyncMessage) : Decoded
        data class Failed(val failure: SessionFailure) : Decoded
    }

    private fun decode(plain: ByteArray): Decoded = when (val decoded = SyncMessageJson.decode(String(plain))) {
        is SyncMessageJson.DecodeResult.Success -> Decoded.Message(decoded.message)
        is SyncMessageJson.DecodeResult.Incompatible -> Decoded.Failed(
            SessionFailure.Incompatible("The other device uses sync protocol ${decoded.version}, this one $PROTOCOL_VERSION"),
        )
        is SyncMessageJson.DecodeResult.Invalid -> Decoded.Failed(SessionFailure.Invalid(decoded.message))
    }

    /** Tells the initiator why its request was not used, then reports the same failure here. */
    private fun refuse(output: OutputStream, failure: SessionFailure): SessionResult {
        val reason = when (failure) {
            is SessionFailure.Incompatible -> failure.message
            is SessionFailure.Refused -> failure.message
            is SessionFailure.Invalid -> failure.message
            else -> "The request was refused"
        }
        runCatching { send(output, SyncMessage.Failure(reason)) }
        return SessionResult.Failed(failure)
    }

    private fun send(output: OutputStream, message: SyncMessage) =
        FrameIO.write(output, crypto.encrypt(SyncMessageJson.encode(message).toByteArray()))

    private fun network(e: IOException) =
        SessionResult.Failed(SessionFailure.Network(e.message ?: "The connection failed"))
}
