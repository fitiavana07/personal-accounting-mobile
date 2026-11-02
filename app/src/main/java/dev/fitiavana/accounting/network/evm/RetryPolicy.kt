package dev.fitiavana.accounting.network.evm

/**
 * Retries transient failures (timeouts, HTTP 5xx and 429) up to [MAX_RETRIES] times with a growing backoff.
 * Everything else, notably no internet, fails straight away: the next scheduled check tries again.
 */
object RetryPolicy {
    const val MAX_RETRIES = 2
    private const val BASE_BACKOFF_MILLIS = 1_000L

    fun isRetryable(error: Throwable): Boolean = when (error) {
        is RpcException.Timeout -> true
        is RpcException.HttpStatus -> error.code >= 500 || error.code == 429
        else -> false
    }

    /** Call off the main thread: [sleep] blocks between attempts. */
    fun <T> run(sleep: (Long) -> Unit = { Thread.sleep(it) }, block: () -> T): T {
        var retries = 0
        while (true) {
            try {
                return block()
            } catch (e: RpcException) {
                if (!isRetryable(e) || retries >= MAX_RETRIES) throw e
                sleep(BASE_BACKOFF_MILLIS shl retries)
                retries++
            }
        }
    }
}
