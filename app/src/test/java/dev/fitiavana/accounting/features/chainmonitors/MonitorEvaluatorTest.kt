package dev.fitiavana.accounting.features.chainmonitors

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorEvaluatorTest {

    private fun monitor(threshold: String = "0", id: String = "m") = ChainMonitor(
        id = id,
        label = "Acres 40 available",
        holderAddress = "0xB99B6dF96d4d5448cC0a5B3e0ef7896df9507Cf5",
        tokenAddress = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
        tokenSymbol = "USDC",
        tokenDecimals = 6,
        threshold = threshold,
        intervalMinutes = 15,
        createdAt = 1L
    )

    private fun state(m: ChainMonitor, armed: Boolean) =
        mapOf(m.id to MonitorState(m.id, armed, m.signature, 0L, null))

    private fun evaluate(
        m: ChainMonitor,
        balance: String?,
        states: Map<String, MonitorState> = emptyMap(),
        now: Long = 1000L
    ) = MonitorEvaluator.evaluate(
        listOf(m),
        mapOf(m.id to balance?.let { BigDecimal(it) }),
        states,
        now
    )

    @Test
    fun `a new monitor already above the threshold fires on its first check`() {
        val m = monitor("0")

        val result = evaluate(m, "12.5")

        assertEquals(listOf(FiredMonitor(m, BigDecimal("12.5"))), result.fired)
        assertEquals(listOf(MonitorState("m", false, m.signature, 1000L, "12.5")), result.states)
    }

    @Test
    fun `a new monitor not above the threshold is armed and does not fire`() {
        val m = monitor("0")

        val result = evaluate(m, "0")

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(MonitorState("m", true, m.signature, 1000L, "0")), result.states)
    }

    @Test
    fun `the condition is strictly greater than`() {
        val m = monitor("5")

        assertTrue(evaluate(m, "5", state(m, true)).fired.isEmpty())
        assertEquals(1, evaluate(m, "5.000001", state(m, true)).fired.size)
    }

    @Test
    fun `an armed monitor fires once and disarms`() {
        val m = monitor("0")

        val result = evaluate(m, "3", state(m, true))

        assertEquals(listOf(FiredMonitor(m, BigDecimal("3"))), result.fired)
        assertEquals(false, result.states.single().armed)
    }

    @Test
    fun `a disarmed monitor stays quiet while the balance stays above the threshold`() {
        val m = monitor("0")

        val result = evaluate(m, "7", state(m, false))

        assertTrue(result.fired.isEmpty())
        assertEquals(false, result.states.single().armed)
        assertEquals("7", result.states.single().lastBalance)
    }

    @Test
    fun `a disarmed monitor re-arms when the balance is back at or below the threshold`() {
        val m = monitor("0")

        val result = evaluate(m, "0", state(m, false))

        assertTrue(result.fired.isEmpty())
        assertEquals(true, result.states.single().armed)
    }

    @Test
    fun `it fires again after re-arming`() {
        val m = monitor("0")
        val rearmed = evaluate(m, "0", state(m, false)).states.single()

        val result = evaluate(m, "1", mapOf(m.id to rearmed))

        assertEquals(1, result.fired.size)
    }

    @Test
    fun `an unavailable balance changes nothing`() {
        val m = monitor("0")

        val result = evaluate(m, null, state(m, true))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `a state written for an edited monitor is discarded and re-seeded`() {
        val m = monitor("0")
        val stale = mapOf(m.id to MonitorState(m.id, false, "other|signature", 0L, "9"))

        val result = evaluate(m, "9", stale)

        assertEquals(1, result.fired.size)
        assertEquals(m.signature, result.states.single().signature)
    }

    @Test
    fun `the signature changes with holder, token and threshold`() {
        val base = monitor("0")

        assertTrue(base.signature != base.copy(holderAddress = "0x" + "1".repeat(40)).signature)
        assertTrue(base.signature != base.copy(tokenAddress = "0x" + "2".repeat(40)).signature)
        assertTrue(base.signature != base.copy(threshold = "1").signature)
        assertEquals(base.signature, base.copy(label = "renamed", intervalMinutes = 30).signature)
    }

    @Test
    fun `a monitor is due once its interval has elapsed since the last check`() {
        val m = monitor().copy(intervalMinutes = 30)
        val minute = 60_000L
        fun due(lastChecked: Long?, now: Long) = MonitorEvaluator.isDue(
            m,
            lastChecked?.let { MonitorState(m.id, true, m.signature, it, null) },
            now
        )

        assertTrue(due(null, 0L))
        assertTrue(!due(0L, 10 * minute))
        assertTrue(due(0L, 30 * minute))
        assertTrue("a tick a little early still counts", due(0L, 29 * minute + 30_000))
    }
}
