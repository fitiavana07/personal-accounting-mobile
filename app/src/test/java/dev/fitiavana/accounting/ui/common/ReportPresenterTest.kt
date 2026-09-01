package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.ui.home.AssetPalette
import org.junit.Assert.assertEquals
import org.junit.Test
import dev.fitiavana.accounting.features.reports.ReportRow as RawRow

class ReportPresenterTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long) =
        AccountBalance(
            accountId = accountId,
            balance = balance,
            updatedAt = 0L,
            createdAt = 0L
        )

    @Test
    fun `passes through Title, SectionHeader and SubsectionHeader unchanged`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.Title("Instant Balance Sheet"),
                RawRow.SectionHeader("Assets"),
                RawRow.SubsectionHeader("Original Equity")
            )
        )

        assertEquals(
            listOf(
                ReportDisplayRow.Title("Instant Balance Sheet"),
                ReportDisplayRow.SectionHeader("Assets"),
                ReportDisplayRow.SubsectionHeader("Original Equity")
            ),
            result
        )
    }

    @Test
    fun `assigns a color dot to a SubsectionHeader with an assetIndex`() {
        val result = ReportPresenter.present(
            listOf(RawRow.SubsectionHeader("Cash & Cash Equivalents", assetIndex = 0))
        )

        assertEquals(
            listOf(
                ReportDisplayRow.SubsectionHeader(
                    "Cash & Cash Equivalents",
                    AssetPalette.colorFor(0)
                )
            ),
            result
        )
    }

    @Test
    fun `formats a plain AccountLine without Ar prefix or parens`() {
        val result =
            ReportPresenter.present(listOf(RawRow.AccountLine("Cash", 10_000)))
        assertEquals(
            listOf(
                ReportDisplayRow.AccountLine(
                    "Cash",
                    "10,000 ",
                    null
                )
            ), result
        )
    }

    @Test
    fun `formats a contra AccountLine with parens and absolute value`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.AccountLine(
                    "Rent",
                    150,
                    contra = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.AccountLine(
                    "Rent",
                    "(150)",
                    null
                )
            ), result
        )
    }

    @Test
    fun `formats an arPrefixed AccountLine with the Ar prefix`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.AccountLine(
                    "Income",
                    300,
                    arPrefixed = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.AccountLine(
                    "Income",
                    "Ar 300 ",
                    null
                )
            ), result
        )
    }

    @Test
    fun `formats an arPrefixed contra AccountLine with Ar prefix and parens`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.AccountLine(
                    "Expense",
                    150,
                    contra = true,
                    arPrefixed = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.AccountLine(
                    "Expense",
                    "(Ar 150)",
                    null
                )
            ), result
        )
    }

    @Test
    fun `maps assetIndex to a palette color`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.AccountLine(
                    "Cash",
                    10_000,
                    assetIndex = 2
                )
            )
        )
        assertEquals(
            AssetPalette.colorFor(2),
            (result.single() as ReportDisplayRow.AccountLine).color
        )
    }

    @Test
    fun `formats a TotalLine with Ar prefix always, signed when not contra`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.TotalLine(
                    "Total Equity",
                    -500,
                    emphasized = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.TotalLine(
                    "Total Equity",
                    "Ar -500 ",
                    emphasized = true
                )
            ),
            result
        )
    }

    @Test
    fun `formats a contra TotalLine with parens and absolute value regardless of sign`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.TotalLine(
                    "Total Changes in Equity",
                    140,
                    contra = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.TotalLine(
                    "Total Changes in Equity",
                    "(Ar 140)",
                    emphasized = false
                )
            ),
            result
        )
    }

    @Test
    fun `formats a parenthesizeNegative TotalLine with parens when negative`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.TotalLine(
                    "Total Unclosed IS accounts",
                    -200,
                    parenthesizeNegative = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.TotalLine(
                    "Total Unclosed IS accounts",
                    "(Ar 200)",
                    emphasized = false
                )
            ),
            result
        )
    }

    @Test
    fun `formats a parenthesizeNegative TotalLine without parens when positive`() {
        val result = ReportPresenter.present(
            listOf(
                RawRow.TotalLine(
                    "Total Unclosed IS accounts",
                    200,
                    parenthesizeNegative = true
                )
            )
        )
        assertEquals(
            listOf(
                ReportDisplayRow.TotalLine(
                    "Total Unclosed IS accounts",
                    "Ar 200 ",
                    emphasized = false
                )
            ),
            result
        )
    }

    @Test
    fun `formats a DateLine timestamp into a display string`() {
        val result = ReportPresenter.present(listOf(RawRow.DateLine(0L)))
        assertEquals(
            listOf(ReportDisplayRow.DateLine("Balances at Jan 1, 1970")),
            result
        )
    }

    @Test
    fun `asset line colors follow the account lines' own render order`() {
        // Since assets are now grouped by liquidity level, this order no longer
        // matches the (differently grouped, "Other"-collapsing) pie chart slices —
        // each line's dot color is assigned by its position in the grouped list itself.
        val accounts = listOf(
            account("a", "Bank", "asset"),
            account("b", "Petty Cash", "asset"),
            account("c", "Coin Jar", "asset")
        )
        val balances = listOf(
            balance("a", 15_000),
            balance("b", 4_000),
            balance("c", 2_500)
        )

        val accountLines = ReportPresenter.present(
            BalanceSheetBuilder.build(
                accounts,
                balances
            )
        )
            .filterIsInstance<ReportDisplayRow.AccountLine>()

        assertEquals(listOf("Bank", "Petty Cash", "Coin Jar"), accountLines.map { it.name })
        assertEquals(
            accountLines.indices.map { AssetPalette.colorFor(it) },
            accountLines.map { it.color }
        )
    }
}
