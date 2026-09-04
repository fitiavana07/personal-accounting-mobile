package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.features.reports.EquityStatement
import dev.fitiavana.accounting.features.reports.EquityStatementRow
import org.junit.Assert.assertEquals
import org.junit.Test

class EquityStatementPresenterTest {

    @Test
    fun `carries column titles through unchanged`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("Owner Capital", "Unclosed IS Accounts", "Drawing", "Total"),
                rows = emptyList()
            )
        )

        assertEquals(
            listOf("Owner Capital", "Unclosed IS Accounts", "Drawing", "Total"),
            result.columnTitles
        )
    }

    @Test
    fun `formats a null cell as a dash placeholder`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("A", "Total"),
                rows = listOf(EquityStatementRow("Changes in A", listOf(null, 0L)))
            )
        )

        assertEquals("-", result.rows.single().cellTexts[0])
    }

    @Test
    fun `formats a positive amount without parentheses`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("A", "Total"),
                rows = listOf(EquityStatementRow("Balance", listOf(10_000L, 10_000L)))
            )
        )

        assertEquals("10,000", result.rows.single().cellTexts[0])
    }

    @Test
    fun `formats a negative amount parenthesized using the absolute value`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("Drawing", "Total"),
                rows = listOf(EquityStatementRow("Balance", listOf(-1_000L, -1_000L)))
            )
        )

        assertEquals("(1,000)", result.rows.single().cellTexts[0])
    }

    @Test
    fun `formats a zero amount as a plain zero, not a dash`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("A", "Total"),
                rows = listOf(EquityStatementRow("Changes in A", listOf(0L, 0L)))
            )
        )

        assertEquals("0", result.rows.single().cellTexts[0])
    }

    @Test
    fun `carries label and emphasized flag through unchanged`() {
        val result = EquityStatementPresenter.present(
            EquityStatement(
                columnTitles = listOf("Total"),
                rows = listOf(EquityStatementRow("Balance at Dec 31", listOf(0L), emphasized = true))
            )
        )

        val row = result.rows.single()
        assertEquals("Balance at Dec 31", row.label)
        assertEquals(true, row.emphasized)
    }
}
