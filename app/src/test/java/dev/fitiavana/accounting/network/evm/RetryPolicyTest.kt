package dev.fitiavana.accounting.network.evm

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `no internet is never retried`() {
        assertFalse(RetryPolicy.isRetryable(RpcException.NoNetwork(null)))
    }

    @Test
    fun `timeouts, server errors and rate limiting are retried`() {
        assertTrue(RetryPolicy.isRetryable(RpcException.Timeout(null)))
        assertTrue(RetryPolicy.isRetryable(RpcException.HttpStatus(500)))
        assertTrue(RetryPolicy.isRetryable(RpcException.HttpStatus(503)))
        assertTrue(RetryPolicy.isRetryable(RpcException.HttpStatus(429)))
    }

    @Test
    fun `client errors, rpc errors and malformed answers are not retried`() {
        assertFalse(RetryPolicy.isRetryable(RpcException.HttpStatus(404)))
        assertFalse(RetryPolicy.isRetryable(RpcException.Rpc("execution reverted")))
        assertFalse(RetryPolicy.isRetryable(RpcException.Malformed("bad json", null)))
        assertFalse(RetryPolicy.isRetryable(IOException("other")))
    }

    @Test
    fun `a call that succeeds is not retried`() {
        var calls = 0
        val result = RetryPolicy.run(sleep = { fail("must not sleep") }) { calls++; "ok" }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `a timeout is retried with a growing backoff then succeeds`() {
        var calls = 0
        val sleeps = mutableListOf<Long>()

        val result = RetryPolicy.run(sleep = { sleeps += it }) {
            if (++calls < 3) throw RpcException.Timeout(null)
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun `it gives up after two retries and rethrows the last error`() {
        var calls = 0

        try {
            RetryPolicy.run(sleep = {}) { calls++; throw RpcException.HttpStatus(503) }
            fail("expected an exception")
        } catch (e: RpcException.HttpStatus) {
            assertEquals(503, e.code)
        }
        assertEquals(3, calls)
    }

    @Test
    fun `no internet fails straight away`() {
        var calls = 0

        try {
            RetryPolicy.run(sleep = { fail("must not sleep") }) { calls++; throw RpcException.NoNetwork(null) }
            fail("expected an exception")
        } catch (e: RpcException.NoNetwork) {
            // expected
        }
        assertEquals(1, calls)
    }
}
