package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.data.model.Account

/**
 * Income Statement: "Income", "Expense", "Gain" and "Loss" are the main sections (no
 * Assets/Liabilities/Equity), each with its detailed accounts and a per-section total. The last
 * row is "Net Income" = total_income - total_expense + total_gain - total_loss.
 */
object IncomeStatementBuilder {

    fun build(accounts: List<Account>, balancesByAccountId: Map<String, Long>): List<BalanceSheetRow> {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedBalance> =
            linesFor(accountMap, balancesByAccountId, type)

        val incomeLines = linesFor("revenue").sortedByDescending { it.balance }
        val expenseLines = linesFor("expense").sortedByDescending { it.balance }
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")

        val totalIncome = incomeLines.sumOf { it.balance }
        val totalExpense = expenseLines.sumOf { it.balance }
        val totalGain = gainLines.sumOf { it.balance }
        val totalLoss = lossLines.sumOf { it.balance }
        val netIncome = totalIncome - totalExpense + totalGain - totalLoss

        val rows = mutableListOf<BalanceSheetRow>()

        if (incomeLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Income")
            incomeLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
            rows += BalanceSheetRow.TotalLine("Total Income", totalIncome, emphasized = true)
        }
        if (expenseLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Expense")
            expenseLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
            rows += BalanceSheetRow.TotalLine("Total Expense", totalExpense, emphasized = true, contra = true)
        }
        if (gainLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Gain")
            gainLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance) }
            rows += BalanceSheetRow.TotalLine("Total Gain", totalGain, emphasized = true)
        }
        if (lossLines.isNotEmpty()) {
            rows += BalanceSheetRow.SectionHeader("Loss")
            lossLines.forEach { rows += BalanceSheetRow.AccountLine(it.name, it.balance, contra = true) }
            rows += BalanceSheetRow.TotalLine("Total Loss", totalLoss, emphasized = true, contra = true)
        }

        if (rows.isNotEmpty()) {
            rows += BalanceSheetRow.TotalLine("Net Income", netIncome, emphasized = true)
        }

        return rows
    }
}
