package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.ui.transactions.TransactionDisplay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AssetSlice(val name: String, val amount: Long)

sealed class BalanceSheetRow {
    data class Title(val text: String) : BalanceSheetRow()
    data class SectionHeader(val title: String) : BalanceSheetRow()
    data class SubsectionHeader(val title: String) : BalanceSheetRow()
    data class AccountLine(val name: String, val amountText: String, val color: Int? = null) : BalanceSheetRow()
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

    /**
     * Asset lines grouped the same way as the "Assets" section of the balance sheet:
     * accounts with |balance| below [OTHER_ASSET_THRESHOLD] are collapsed into "Other".
     */
    fun assetSlices(accounts: List<Account>, balances: List<AccountBalance>): List<AssetSlice> {
        val accountMap = accounts.associateBy { it.id }
        val assetLines = linesFor(accountMap, balances, "asset").sortedByDescending { it.balance }
        if (assetLines.isEmpty()) return emptyList()

        val (mainAssetLines, otherAssetLines) = assetLines.partition {
            Math.abs(it.balance) >= OTHER_ASSET_THRESHOLD
        }

        val slices = mainAssetLines.map { AssetSlice(it.name, it.balance) }.toMutableList()
        if (otherAssetLines.isNotEmpty()) {
            slices += AssetSlice("Other", otherAssetLines.sumOf { it.balance })
        }
        return slices
    }

    private fun linesFor(
        accountMap: Map<String, Account>,
        balances: List<AccountBalance>,
        type: String
    ): List<NamedBalance> = balances
        .filter { accountMap.containsKey(it.accountId) }
        .filter { accountMap.getValue(it.accountId).type == type }
        .filter { it.balance != 0L }
        .map { NamedBalance(accountMap.getValue(it.accountId).name, it.balance) }
        .sortedBy { it.name }

    fun build(accounts: List<Account>, balances: List<AccountBalance>): List<BalanceSheetRow> {
        val accountMap = accounts.associateBy { it.id }
        val includedBalances = balances.filter { accountMap.containsKey(it.accountId) }
        if (includedBalances.isEmpty()) return emptyList()

        fun linesFor(type: String): List<NamedBalance> =
            linesFor(accountMap, includedBalances, type)

        val assetLines = linesFor("asset").sortedByDescending { it.balance }
        val liabilityLines = linesFor("liability")
        val equityLines = linesFor("equity")
        val incomeLines = linesFor("revenue").sortedByDescending { it.balance }
        val expenseLines =
            linesFor("expense").sortedByDescending { it.balance }
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
            mainAssetLines.forEachIndexed { index, line ->
                rows += BalanceSheetRow.AccountLine(
                    line.name,
                    formatPlain(line.balance),
                    AssetPalette.colorFor(index)
                )
            }
            if (otherAssetLines.isNotEmpty()) {
                rows += BalanceSheetRow.AccountLine(
                    "Other",
                    formatPlain(otherAssetLines.sumOf { it.balance }),
                    AssetPalette.colorFor(mainAssetLines.size)
                )
            }
            rows += BalanceSheetRow.TotalLine("Total Assets", formatAr(totalAssets), emphasized = true)
        }

        if (liabilityLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Liabilities")
            liabilityLines.forEach {
                rows += BalanceSheetRow.AccountLine(
                    it.name,
                    formatPlain(it.balance)
                )
            }
            rows += BalanceSheetRow.TotalLine("Total Liabilities", formatAr(totalLiabilities), emphasized = true)
        }

        val hasEquitySection = listOf(
            equityLines, incomeLines, expenseLines, gainLines, lossLines, drawingLines
        ).any { it.isNotEmpty() }

        if (hasEquitySection) {
            rows += BalanceSheetRow.SectionHeader("Equity")

            if (equityLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Original Equity")
                equityLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlain(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Original Equity", formatAr(totalOriginalEquity))
            }
            if (incomeLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Income")
                incomeLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlain(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Income", formatAr(totalIncome))
            }
            if (expenseLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Expense")
                expenseLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlainParens(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Expense", formatArParens(totalExpense))
            }
            if (gainLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Gain")
                gainLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlain(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Gain", formatAr(totalGain))
            }
            if (lossLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Loss")
                lossLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlainParens(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Loss", formatArParens(totalLoss))
            }
            if (drawingLines.isNotEmpty()) {
                rows += BalanceSheetRow.SubsectionHeader("Drawing")
                drawingLines.forEach {
                    rows += BalanceSheetRow.AccountLine(
                        it.name,
                        formatPlainParens(it.balance)
                    )
                }
                rows += BalanceSheetRow.TotalLine("Total Drawing", formatArParens(totalDrawing))
            }

            val hasChangesInEquity = listOf(
                incomeLines, expenseLines, gainLines, lossLines, drawingLines
            ).any { it.isNotEmpty() }
            if (hasChangesInEquity) {
                val totalChangesInEquity = totalIncome - totalExpense + totalGain - totalLoss - totalDrawing
                rows += BalanceSheetRow.TotalLine(
                    "Total Changes in Equity",
                    formatArParens(totalChangesInEquity)
                )
            }

            rows += BalanceSheetRow.TotalLine(
                "Total Equity", formatAr(totalEquity), emphasized = true
            )
        }

        val balanceDate = includedBalances.maxOf { it.updatedAt }
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        rows += BalanceSheetRow.DateLine("Balances at ${dateFormat.format(Date(balanceDate))}")

        return rows
    }

    private fun formatPlain(amount: Long): String =
        TransactionDisplay.formatAmount(amount)

    private fun formatPlainParens(amount: Long): String =
        "(${TransactionDisplay.formatAmount(Math.abs(amount))})"

    private fun formatAr(amount: Long): String = "Ar ${TransactionDisplay.formatAmount(amount)}"

    private fun formatArParens(amount: Long): String = "(Ar ${TransactionDisplay.formatAmount(Math.abs(amount))})"

}
