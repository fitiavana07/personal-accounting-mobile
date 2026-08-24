package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.ui.home.AssetPalette
import dev.fitiavana.accounting.ui.home.AssetSliceBuilder
import dev.fitiavana.accounting.ui.home.BalanceSheetPresenter
import dev.fitiavana.accounting.ui.home.BalanceSheetRow
import org.junit.Assert.assertEquals
import org.junit.Test
import dev.fitiavana.accounting.features.reports.BalanceSheetRow as RawRow

class BalanceSheetPresenterTest {

    private fun account(id: String, name: String, type: String) =
        Account(id = id, name = name, type = type)

    private fun balance(accountId: String, balance: Long) =
        AccountBalance(accountId = accountId, balance = balance, updatedAt = 0L, createdAt = 0L)

    @Test
    fun `passes through Title, SectionHeader and SubsectionHeader unchanged`() {
        val result = BalanceSheetPresenter.present(
            listOf(
                RawRow.Title("Instant Balance Sheet"),
                RawRow.SectionHeader("Assets"),
                RawRow.SubsectionHeader("Original Equity")
            )
        )

        assertEquals(
            listOf(
                BalanceSheetRow.Title("Instant Balance Sheet"),
                BalanceSheetRow.SectionHeader("Assets"),
                BalanceSheetRow.SubsectionHeader("Original Equity")
            ),
            result
        )
    }

    @Test
    fun `formats a plain AccountLine without Ar prefix or parens`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.AccountLine("Cash", 10_000)))
        assertEquals(listOf(BalanceSheetRow.AccountLine("Cash", "10,000", null)), result)
    }

    @Test
    fun `formats a contra AccountLine with parens and absolute value`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.AccountLine("Rent", 150, contra = true)))
        assertEquals(listOf(BalanceSheetRow.AccountLine("Rent", "(150)", null)), result)
    }

    @Test
    fun `formats an arPrefixed AccountLine with the Ar prefix`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.AccountLine("Income", 300, arPrefixed = true)))
        assertEquals(listOf(BalanceSheetRow.AccountLine("Income", "Ar 300", null)), result)
    }

    @Test
    fun `formats an arPrefixed contra AccountLine with Ar prefix and parens`() {
        val result = BalanceSheetPresenter.present(
            listOf(RawRow.AccountLine("Expense", 150, contra = true, arPrefixed = true))
        )
        assertEquals(listOf(BalanceSheetRow.AccountLine("Expense", "(Ar 150)", null)), result)
    }

    @Test
    fun `maps assetIndex to a palette color`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.AccountLine("Cash", 10_000, assetIndex = 2)))
        assertEquals(AssetPalette.colorFor(2), (result.single() as BalanceSheetRow.AccountLine).color)
    }

    @Test
    fun `formats a TotalLine with Ar prefix always, signed when not contra`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.TotalLine("Total Equity", -500, emphasized = true)))
        assertEquals(
            listOf(BalanceSheetRow.TotalLine("Total Equity", "Ar -500", emphasized = true)),
            result
        )
    }

    @Test
    fun `formats a contra TotalLine with parens and absolute value regardless of sign`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.TotalLine("Total Changes in Equity", 140, contra = true)))
        assertEquals(
            listOf(BalanceSheetRow.TotalLine("Total Changes in Equity", "(Ar 140)", emphasized = false)),
            result
        )
    }

    @Test
    fun `formats a DateLine timestamp into a display string`() {
        val result = BalanceSheetPresenter.present(listOf(RawRow.DateLine(0L)))
        assertEquals(listOf(BalanceSheetRow.DateLine("Balances at Jan 1, 1970")), result)
    }

    @Test
    fun `asset line colors follow the same order as the pie chart slices`() {
        val accounts = listOf(
            account("a", "Bank", "asset"),
            account("b", "Petty Cash", "asset"),
            account("c", "Coin Jar", "asset")
        )
        val balances = listOf(balance("a", 15_000), balance("b", 4_000), balance("c", 2_500))

        val slices = AssetSliceBuilder.assetSlices(accounts, balances)
        val accountLines = BalanceSheetPresenter.present(BalanceSheetBuilder.build(accounts, balances))
            .filterIsInstance<BalanceSheetRow.AccountLine>()

        assertEquals(slices.map { it.name }, accountLines.map { it.name })
        assertEquals(
            slices.indices.map { AssetPalette.colorFor(it) },
            accountLines.map { it.color }
        )
    }
}
