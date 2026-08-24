package dev.fitiavana.accounting.features.balances

import dev.fitiavana.accounting.features.balances.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    // --- Asset: balance = debits - credits ---

    @Test
    fun `asset debit increases balance`() {
        assertEquals(1000, BalanceCalculator.compute("asset", totalDebits = 1000, totalCredits = 0))
    }

    @Test
    fun `asset credit decreases balance`() {
        assertEquals(800, BalanceCalculator.compute("asset", totalDebits = 1000, totalCredits = 200))
    }

    @Test
    fun `asset credits exceed debits gives negative balance`() {
        assertEquals(-300, BalanceCalculator.compute("asset", totalDebits = 200, totalCredits = 500))
    }

    @Test
    fun `asset balanced debits and credits gives zero`() {
        assertEquals(0, BalanceCalculator.compute("asset", totalDebits = 500, totalCredits = 500))
    }

    // --- Expense: same rule as asset ---

    @Test
    fun `expense debit increases balance`() {
        assertEquals(800, BalanceCalculator.compute("expense", totalDebits = 800, totalCredits = 0))
    }

    @Test
    fun `expense credit decreases balance`() {
        assertEquals(300, BalanceCalculator.compute("expense", totalDebits = 800, totalCredits = 500))
    }

    // --- Liability: balance = credits - debits ---

    @Test
    fun `liability credit increases balance`() {
        assertEquals(5000, BalanceCalculator.compute("liability", totalDebits = 0, totalCredits = 5000))
    }

    @Test
    fun `liability debit decreases balance`() {
        assertEquals(4000, BalanceCalculator.compute("liability", totalDebits = 1000, totalCredits = 5000))
    }

    @Test
    fun `liability debits exceed credits gives negative balance`() {
        assertEquals(-200, BalanceCalculator.compute("liability", totalDebits = 700, totalCredits = 500))
    }

    // --- Equity: same rule as liability ---

    @Test
    fun `equity credit increases balance`() {
        assertEquals(10000, BalanceCalculator.compute("equity", totalDebits = 0, totalCredits = 10000))
    }

    @Test
    fun `equity debit decreases balance`() {
        assertEquals(9000, BalanceCalculator.compute("equity", totalDebits = 1000, totalCredits = 10000))
    }

    // --- Revenue: same rule as liability ---

    @Test
    fun `revenue credit increases balance`() {
        assertEquals(3000, BalanceCalculator.compute("revenue", totalDebits = 0, totalCredits = 3000))
    }

    @Test
    fun `revenue debit decreases balance`() {
        assertEquals(2500, BalanceCalculator.compute("revenue", totalDebits = 500, totalCredits = 3000))
    }

    // --- Unknown type falls through to credit-normal rule ---

    @Test
    fun `unknown type treated as credit-normal`() {
        assertEquals(200, BalanceCalculator.compute("unknown", totalDebits = 300, totalCredits = 500))
    }

    // --- Drawing: same rule as asset (debit-normal) ---

    @Test
    fun `drawing debit increases balance`() {
        assertEquals(400, BalanceCalculator.compute("drawing", totalDebits = 400, totalCredits = 0))
    }

    @Test
    fun `drawing credit decreases balance`() {
        assertEquals(100, BalanceCalculator.compute("drawing", totalDebits = 400, totalCredits = 300))
    }

    // --- Loss: same rule as asset (debit-normal) ---

    @Test
    fun `loss debit increases balance`() {
        assertEquals(750, BalanceCalculator.compute("loss", totalDebits = 750, totalCredits = 0))
    }

    @Test
    fun `loss credit decreases balance`() {
        assertEquals(250, BalanceCalculator.compute("loss", totalDebits = 750, totalCredits = 500))
    }

    // --- Zero inputs ---

    @Test
    fun `all zeros gives zero for asset`() {
        assertEquals(0, BalanceCalculator.compute("asset", totalDebits = 0, totalCredits = 0))
    }

    @Test
    fun `all zeros gives zero for liability`() {
        assertEquals(0, BalanceCalculator.compute("liability", totalDebits = 0, totalCredits = 0))
    }
}
