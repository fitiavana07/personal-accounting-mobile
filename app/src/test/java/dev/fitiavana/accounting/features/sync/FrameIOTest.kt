package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

class FrameIOTest {
    private fun framed(vararg payloads: ByteArray) = ByteArrayOutputStream().also { out ->
        payloads.forEach { FrameIO.write(out, it) }
    }.toByteArray()

    @Test
    fun aFrameReadsBackAsWritten() {
        val payload = "hello".toByteArray()

        assertArrayEquals(payload, FrameIO.read(ByteArrayInputStream(framed(payload))))
    }

    @Test
    fun framesAreReadOneAfterTheOther() {
        val input = ByteArrayInputStream(framed("one".toByteArray(), ByteArray(0), "three".toByteArray()))

        assertArrayEquals("one".toByteArray(), FrameIO.read(input))
        assertArrayEquals(ByteArray(0), FrameIO.read(input))
        assertArrayEquals("three".toByteArray(), FrameIO.read(input))
    }

    @Test
    fun aFrameLargerThanTheLimitIsRefusedBeforeReadingIt() {
        val input = ByteArrayInputStream(framed(ByteArray(100)))

        assertThrows(IOException::class.java) { FrameIO.read(input, maxSize = 99) }
    }

    @Test
    fun aNegativeLengthIsRefused() {
        val input = ByteArrayInputStream(byteArrayOf(-1, -1, -1, -1))

        assertThrows(IOException::class.java) { FrameIO.read(input) }
    }

    @Test
    fun aTruncatedHeaderOrPayloadFails() {
        assertThrows(EOFException::class.java) { FrameIO.read(ByteArrayInputStream(byteArrayOf(0, 0))) }
        assertThrows(EOFException::class.java) {
            FrameIO.read(ByteArrayInputStream(framed(ByteArray(10)).copyOf(8)))
        }
        assertThrows(EOFException::class.java) { FrameIO.read(ByteArrayInputStream(ByteArray(0))) }
    }
}
