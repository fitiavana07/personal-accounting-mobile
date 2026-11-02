package dev.fitiavana.accounting.network.evm

import java.io.IOException

/** Why a JSON-RPC call failed, so callers can decide whether retrying makes sense. */
sealed class RpcException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** The device is offline or the host cannot be reached. */
    class NoNetwork(cause: Throwable?) : RpcException("No network", cause)

    class Timeout(cause: Throwable?) : RpcException("Request timed out", cause)

    class HttpStatus(val code: Int) : RpcException("HTTP $code")

    /** The node answered with a JSON-RPC `error` object (e.g. execution reverted). */
    class Rpc(val rpcMessage: String) : RpcException("RPC error: $rpcMessage")

    /** The answer could not be understood. */
    class Malformed(detail: String, cause: Throwable?) : RpcException("Malformed response: $detail", cause)
}
