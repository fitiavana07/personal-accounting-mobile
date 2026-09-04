package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class EquityStatementBuilderTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private val previousLabel = "Balance at December 31, 2025"
    private val currentLabel = "Balance at January 31, 2026"

    @Test
    fun `no accounts of any relevant type yields zeroed balance rows and the two change rows`() {
        val result = EquityStatementBuilder.build(
            accounts = emptyList(),
            previousMonthEndBalances = emptyMap(),
            periodChangeBalances = emptyMap(),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        assertEquals(
            listOf("Unclosed IS Accounts", "Drawing", "Total"),
            result.columnTitles
        )
        assertEquals(
            listOf(
                EquityStatementRow(previousLabel, listOf(0L, 0L, 0L), emphasized = true),
                EquityStatementRow("Changes in Unclosed IS Accounts", listOf(0L, null, 0L)),
                EquityStatementRow("Changes in Drawing", listOf(null, 0L, 0L)),
                EquityStatementRow(currentLabel, listOf(0L, 0L, 0L), emphasized = true)
            ),
            result.rows
        )
    }

    @Test
    fun `equity accounts become columns sorted by name, each with their own Changes row`() {
        val accounts = listOf(
            account("e2", "Zed Capital", "equity"),
            account("e1", "Alice Capital", "equity")
        )

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = mapOf("e1" to 100L, "e2" to 200L),
            periodChangeBalances = mapOf("e1" to 10L, "e2" to 20L),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        assertEquals(
            listOf("Alice Capital", "Zed Capital", "Unclosed IS Accounts", "Drawing", "Total"),
            result.columnTitles
        )

        val previousRow = result.rows.first { it.label == previousLabel }
        // Total = 100 + 200 = 300
        assertEquals(listOf(100L, 200L, 0L, 0L, 300L), previousRow.cells)

        val aliceChange = result.rows.single { it.label == "Changes in Alice Capital" }
        assertEquals(listOf(10L, null, null, null, 10L), aliceChange.cells)

        val zedChange = result.rows.single { it.label == "Changes in Zed Capital" }
        assertEquals(listOf(null, 20L, null, null, 20L), zedChange.cells)

        val currentRow = result.rows.first { it.label == currentLabel }
        // 100+10 = 110, 200+20 = 220, total = 330
        assertEquals(listOf(110L, 220L, 0L, 0L, 330L), currentRow.cells)
    }

    @Test
    fun `drawing balances and changes are stored negative`() {
        val accounts = listOf(account("d", "Owner Drawing", "drawing"))

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = mapOf("d" to 500L),
            periodChangeBalances = mapOf("d" to 60L),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        val previousRow = result.rows.first { it.label == previousLabel }
        // [Unclosed IS, Drawing, Total] = [0, -500, -500]
        assertEquals(listOf(0L, -500L, -500L), previousRow.cells)

        val drawingChange = result.rows.single { it.label == "Changes in Drawing" }
        assertEquals(listOf(null, -60L, -60L), drawingChange.cells)

        val currentRow = result.rows.first { it.label == currentLabel }
        // -500 + -60 = -560
        assertEquals(listOf(0L, -560L, -560L), currentRow.cells)
    }

    @Test
    fun `multiple drawing accounts are combined into the single Drawing column`() {
        val accounts = listOf(
            account("d1", "Owner Drawing", "drawing"),
            account("d2", "Partner Drawing", "drawing")
        )

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = mapOf("d1" to 100L, "d2" to 50L),
            periodChangeBalances = mapOf("d1" to 10L, "d2" to 5L),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        val previousRow = result.rows.first { it.label == previousLabel }
        assertEquals(listOf(0L, -150L, -150L), previousRow.cells)

        val drawingChange = result.rows.single { it.label == "Changes in Drawing" }
        assertEquals(listOf(null, -15L, -15L), drawingChange.cells)
    }

    @Test
    fun `Changes in Unclosed IS Accounts uses netIncome and can be a net loss`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense")
        )

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = emptyMap(),
            periodChangeBalances = mapOf("r" to 100L, "x" to 300L),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        val unclosedChange = result.rows.single { it.label == "Changes in Unclosed IS Accounts" }
        // Net Loss: 100 - 300 = -200
        assertEquals(listOf(-200L, null, -200L), unclosedChange.cells)

        val currentRow = result.rows.first { it.label == currentLabel }
        assertEquals(listOf(-200L, 0L, -200L), currentRow.cells)
    }

    @Test
    fun `previous balance for Unclosed IS Accounts folds income, expense, gain and loss as of that date`() {
        val accounts = listOf(
            account("r", "Salary", "revenue"),
            account("x", "Rent", "expense"),
            account("g", "Stock Gain", "gain"),
            account("o", "Stock Loss", "loss")
        )

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = mapOf("r" to 300L, "x" to 150L, "g" to 80L, "o" to 30L),
            periodChangeBalances = emptyMap(),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        val previousRow = result.rows.first { it.label == previousLabel }
        // 300 - 150 + 80 - 30 = 200
        assertEquals(listOf(200L, 0L, 200L), previousRow.cells)
    }

    @Test
    fun `an account with no entry in the balance maps is treated as zero`() {
        val accounts = listOf(account("e", "Owner Capital", "equity"))

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = emptyMap(),
            periodChangeBalances = emptyMap(),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        val previousRow = result.rows.first { it.label == previousLabel }
        assertEquals(listOf(0L, 0L, 0L, 0L), previousRow.cells)
    }

    @Test
    fun `only Balance at rows are emphasized`() {
        val accounts = listOf(account("e", "Owner Capital", "equity"))

        val result = EquityStatementBuilder.build(
            accounts = accounts,
            previousMonthEndBalances = mapOf("e" to 100L),
            periodChangeBalances = mapOf("e" to 10L),
            previousBalanceLabel = previousLabel,
            currentBalanceLabel = currentLabel
        )

        assertEquals(
            listOf(previousLabel, currentLabel),
            result.rows.filter { it.emphasized }.map { it.label }
        )
    }
}
