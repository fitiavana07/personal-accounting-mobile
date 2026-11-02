package dev.fitiavana.accounting.network.evm

import java.math.BigDecimal
import java.math.BigInteger

/** ABI encoding/decoding for the three read-only ERC-20 calls the chain monitors need. Pure, no I/O. */
object Erc20Codec {
    /** Native USDC on Base. */
    const val BASE_USDC = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"

    const val DECIMALS_DATA = "0x313ce567"
    const val SYMBOL_DATA = "0x95d89b41"
    private const val BALANCE_OF_SELECTOR = "0x70a08231"

    private val ADDRESS = Regex("0x[0-9a-fA-F]{40}")
    private val HEX = Regex("[0-9a-fA-F]+")
    private const val WORD = 64

    /** A `0x` followed by 40 hex digits. The EIP-55 checksum casing is not enforced. */
    fun isValidAddress(address: String): Boolean = ADDRESS.matches(address)

    fun balanceOfData(holder: String): String {
        require(isValidAddress(holder)) { "Invalid address" }
        return BALANCE_OF_SELECTOR + holder.substring(2).lowercase().padStart(WORD, '0')
    }

    /** The uint256 returned by an `eth_call`. An empty result (`0x`) means there is no contract at the address. */
    fun parseUint(result: String): BigInteger = BigInteger(digits(result), 16)

    fun parseDecimals(result: String): Int = parseUint(result).intValueExact()

    /** Accepts the standard dynamic `string` and the `bytes32` older tokens (e.g. MKR) return. */
    fun parseSymbol(result: String): String {
        val hex = digits(result)
        val data = if (hex.length == WORD) {
            hex
        } else {
            val length = BigInteger(hex.substring(WORD, 2 * WORD), 16).intValueExact()
            hex.substring(2 * WORD, 2 * WORD + 2 * length)
        }
        val bytes = data.chunked(2).map { it.toInt(16).toByte() }.takeWhile { it != 0.toByte() }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    fun scale(raw: BigInteger, decimals: Int): BigDecimal = BigDecimal(raw).movePointLeft(decimals)

    private fun digits(result: String): String {
        val hex = result.removePrefix("0x")
        require(hex.isNotEmpty()) { "Empty result: no contract at that address" }
        require(HEX.matches(hex)) { "Result is not hexadecimal" }
        return hex
    }
}
