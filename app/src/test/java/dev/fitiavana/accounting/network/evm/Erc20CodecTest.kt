package dev.fitiavana.accounting.network.evm

import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Erc20CodecTest {

    private val vault = "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5"

    @Test
    fun `accepts a 0x prefixed 40 hex digit address in any case`() {
        assertTrue(Erc20Codec.isValidAddress(vault))
        assertTrue(Erc20Codec.isValidAddress(vault.lowercase()))
        assertTrue(Erc20Codec.isValidAddress(Erc20Codec.BASE_USDC))
    }

    @Test
    fun `rejects malformed addresses`() {
        assertFalse(Erc20Codec.isValidAddress(""))
        assertFalse(Erc20Codec.isValidAddress("B99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5"))
        assertFalse(Erc20Codec.isValidAddress("0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf"))
        assertFalse(Erc20Codec.isValidAddress("0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507CfZ"))
        assertFalse(Erc20Codec.isValidAddress(" $vault"))
    }

    @Test
    fun `balanceOf call data is the selector followed by the left padded lowercase address`() {
        assertEquals(
            "0x70a08231000000000000000000000000b99b6df96d4d5448cc0a5b3e0ef7896df9507cf5",
            Erc20Codec.balanceOfData(vault)
        )
    }

    @Test
    fun `decimals and symbol call data are the bare selectors`() {
        assertEquals("0x313ce567", Erc20Codec.DECIMALS_DATA)
        assertEquals("0x95d89b41", Erc20Codec.SYMBOL_DATA)
    }

    @Test
    fun `parses a uint256 result`() {
        val hex = "0x" + "0".repeat(56) + "000f4240"
        assertEquals(BigInteger.valueOf(1_000_000), Erc20Codec.parseUint(hex))
    }

    @Test
    fun `parses a zero balance`() {
        assertEquals(BigInteger.ZERO, Erc20Codec.parseUint("0x" + "0".repeat(64)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty result means there is no contract at that address`() {
        Erc20Codec.parseUint("0x")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non hex result is rejected`() {
        Erc20Codec.parseUint("0xzz")
    }

    @Test
    fun `parses decimals`() {
        assertEquals(6, Erc20Codec.parseDecimals("0x" + "0".repeat(62) + "06"))
    }

    @Test
    fun `parses an ABI encoded dynamic string symbol`() {
        val hex = "0x" + "20".padStart(64, '0') + "4".padStart(64, '0') + "55534443".padEnd(64, '0')
        assertEquals("USDC", Erc20Codec.parseSymbol(hex))
    }

    @Test
    fun `parses a bytes32 symbol as returned by older tokens`() {
        val hex = "0x" + "4d4b52".padEnd(64, '0')
        assertEquals("MKR", Erc20Codec.parseSymbol(hex))
    }

    @Test
    fun `scales a raw amount by the token decimals`() {
        assertEquals(
            BigDecimal("1234.567891"),
            Erc20Codec.scale(BigInteger.valueOf(1_234_567_891L), 6)
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(Erc20Codec.scale(BigInteger.ZERO, 6)))
    }

    @Test
    fun `scales an 18 decimals amount without losing precision`() {
        val raw = BigInteger("1000000000000000001")
        assertEquals(BigDecimal("1.000000000000000001"), Erc20Codec.scale(raw, 18))
    }
}
