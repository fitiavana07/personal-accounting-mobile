package dev.fitiavana.accounting.features.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Messages on a stream: a 4-byte big-endian length, then that many bytes. */
object FrameIO {
    /** A whole ledger snapshot fits well below this; anything larger is not from a peer of ours. */
    const val DEFAULT_MAX_SIZE = 16 * 1024 * 1024

    fun write(out: OutputStream, payload: ByteArray) {
        val data = DataOutputStream(out)
        data.writeInt(payload.size)
        data.write(payload)
        data.flush()
    }

    /** The next frame; throws [java.io.EOFException] if the stream ends first, [IOException] if it is too large. */
    fun read(input: InputStream, maxSize: Int = DEFAULT_MAX_SIZE): ByteArray {
        val data = DataInputStream(input)
        val size = data.readInt()
        if (size < 0 || size > maxSize) throw IOException("A message of $size bytes is not accepted")
        return ByteArray(size).also(data::readFully)
    }
}
