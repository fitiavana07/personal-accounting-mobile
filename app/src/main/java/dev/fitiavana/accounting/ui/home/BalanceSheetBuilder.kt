package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BalanceSheetRow {
    data class Title(val text: String) : BalanceSheetRow()
    data class SectionHeader(val title: String) : BalanceSheetRow()
    data class SubsectionHeader(val title: String) : BalanceSheetRow()
    data class AccountLine(val name: String, val amountText: String) : BalanceSheetRow()
    data class TotalLine(
        val label: String,
        val amountText: String,
        val emphasized: Boolean = false
    ) : BalanceSheetRow()
    data class DateLine(val text: String) : BalanceSheetRow()
}

object BalanceSheetBuilder {

    private data class NamedBalance(val name: String, val balance: Long)

    private const val OTHER_ASSET_THRESHOLD = 10_000L

    fun build(accounts: List<Account>, balances: List<AccountBalance>): List<BalanceSheetRow> {
        val accountMap = accounts.associateBy { it.id }
        val includedBalances = balances.filter { accountMap.containsKey(it.accountId) }
        if (includedBalances.isEmpty()) return emptyList()

        fun linesFor(type: String): List<NamedBalance> = includedBalances
            .filter { accountMap.getValue(it.accountId).type == type }
            .filter { it.balance != 0L }
            .map { NamedBalance(accountMap.getValue(it.accountId).name, it.balance) }
            .sortedBy { it.name }

        val assetLines = linesFor("asset")
        val liabilityLines = linesFor("liability")
        val equityLines = linesFor("equity")
        val incomeLines = linesFor("revenue")
        val expenseLines = linesFor("expense")
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")
        val drawingLines = linesFor("drawing")

        val totalAssets = assetLines.sumOf { it.balance }
        val totalLiabilities = liabilityLines.sumOf { it.balance }
        val totalOriginalEquity = equityLines.sumOf { it.balance }
        val totalIncome = incomeLines.sumOf { it.balance }
        val totalExpense = expenseLines.sumOf { it.balance }
        val totalGain = gainLines.sumOf { it.balance }
        val totalLoss = lossLines.sumOf { it.balance }
        val totalDrawing = drawingLines.sumOf { it.balance }
        val totalEquity = totalOriginalEquity + totalIncome - totalExpense + totalGain - totalLoss - totalDrawing

        val rows = mutableListOf<BalanceSheetRow>()
        rows += BalanceSheetRow.Title("Instant Balance Sheet")

        if (assetLines.isNotEmpty()) {
            val (mainAssetLines, otherAssetLines) = assetLines.partition {
                Math.abs(
                    it.balance
                ) >= OTHER_ASSET_THRESHOLD
            }

            rows += BalanceSheetRow.SectionHeader("Assets")
            mainAssetLines.forEach {
                rows += BalanceSheetRow.AccountLine(
                    it.name,
                    formatAr(it.balance)
                )
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += BalanceSheetRow.AccountLine(
                    "Other",
                    formatAr(otherAssetLines.sumOf { it.balance })
                )
            }
            rows += BalanceSheetRow.TotalLine("Total Assets", formatAr(totalAssets), emphasized = true)
        }

        if (liabilityLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Liabilities")
            liabilityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatAr(it.balance)) }
            rows += BalanceSheetRow.TotalLine("Total Liabilities", formatAr(totalLiabilities), emphasized = true)
        }

        val hasEquitySection = listOf(
            equityLines, incomeLines, expenseLines, gainLines, lossLines, drawingLines
        ).any { it.isNotEmpty() }

        if (hasEquitySection) {
            rows += BalanceSheetRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Original Equity")
                equityLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatAr(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Original Equity", formatAr(totalOriginalEquity))
            }
            if (incomeLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Income")
                incomeLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatAr(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Income", formatAr(totalIncome))
            }
            if (expenseLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Expense")
                expenseLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatArParens(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Expense", formatArParens(totalExpense))
            }
            if (gainLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Gain")
                gainLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatAr(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Gain", formatAr(totalGain))
            }
            if (lossLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Loss")
                lossLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatArParens(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Loss", formatArParens(totalLoss))
            }
            if (drawingLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Drawing")
                drawingLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, formatArParens(it.balance)) }
                rows += BalanceSheetRow.TotalLine("Total Drawing", formatArParens(totalDrawing))
            }

            val totalChangesInEquity = totalIncome - totalExpense + totalGain - totalLoss - totalDrawing
            rows += BalanceSheetRow.TotalLine("Total Changes in Equity", formatSignedAr(totalChangesInEquity))

            rows += BalanceSheetRow.TotalLine("Total Equity", formatSignedAr(totalEquity), emphasized = true)
        }

        val balanceDate = includedBalances.maxOf { it.updatedAt }
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        rows += BalanceSheetRow.DateLine("Balances at ${dateFormat.format(Date(balanceDate))}")

        return rows
    }

    private fun formatAr(amount: Long): String = "Ar ${TransactionDisplay.formatAmount(amount)}"

    private fun formatArParens(amount: Long): String = "(Ar ${TransactionDisplay.formatAmount(Math.abs(amount))})"

    private fun formatSignedAr(amount: Long): String =
        if (amount < 0) "-Ar ${TransactionDisplay.formatAmount(-amount)}" else formatAr(amount)
}
