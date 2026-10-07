package dev.fitiavana.accounting.ui.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.IncomeStatementBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendSeriesBuilderTest {

    private val accounts = listOf(
        Account(id = "cash", name = "Cash", type = "asset"),
        Account(id = "capital", name = "Capital", type = "equity"),
        Account(id = "salary", name = "Salary", type = "revenue"),
        Account(id = "food", name = "Food", type = "expense")
    )

    private val jan = YearMonth(2025, 0)
    private val feb = YearMonth(2025, 1)

    private val asOf = mapOf(
        jan to mapOf("cash" to 1_100L, "capital" to 1_000L, "salary" to 300L, "food" to 200L),
        feb to mapOf("cash" to 1_500L, "capital" to 1_000L, "salary" to 700L, "food" to 200L)
    )
    private val between = mapOf(
        jan to mapOf("salary" to 300L, "food" to 200L),
        feb to mapOf("salary" to 400L)
    )

    private fun build(months: List<YearMonth>) = TrendSeriesBuilder.build(
        months = months,
        accounts = accounts,
        balancesAsOf = { asOf.getValue(it) },
        balancesBetween = { between.getValue(it) }
    )

    @Test
    fun `returns one point per month in the given order`() {
        val points = build(listOf(jan, feb))

        assertEquals(listOf(jan, feb), points.map { it.yearMonth })
    }

    @Test
    fun `net worth is the balance sheet total equity as of the month`() {
        val points = build(listOf(jan, feb))

        assertEquals(
            BalanceSheetBuilder.totalEquity(accounts, asOf.getValue(jan)),
            points[0].netWorth
        )
        assertEquals(1_100L, points[0].netWorth)
        assertEquals(1_500L, points[1].netWorth)
    }

    @Test
    fun `net income is the income statement net income of the month`() {
        val points = build(listOf(jan, feb))

        assertEquals(
            IncomeStatementBuilder.netIncome(accounts, between.getValue(jan)),
            points[0].netIncome
        )
        assertEquals(100L, points[0].netIncome)
        assertEquals(400L, points[1].netIncome)
    }

    @Test
    fun `no months gives an empty series`() {
        assertTrue(build(emptyList()).isEmpty())
    }
}
