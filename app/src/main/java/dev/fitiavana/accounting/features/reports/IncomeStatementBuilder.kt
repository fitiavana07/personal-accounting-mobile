package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account

/**
 * Income Statement: "Income", "Expense", "Gain" and "Loss" are the main sections (no
 * Assets/Liabilities/Equity), each with its detailed accounts and a per-section total. The last
 * row is "Net Income" = total_income - total_expense + total_gain - total_loss.
 */
object IncomeStatementBuilder {

    fun build(accounts: List<Account>, balancesByAccountId: Map<String, Long>): List<ReportRow> {
        val accountMap = accounts.associateBy { it.id }

        fun linesFor(type: String): List<NamedAmount> =
            linesFor(accountMap, balancesByAccountId, type)

        val incomeLines = linesFor("revenue").sortedByDescending { it.amount }
        val expenseLines = linesFor("expense").sortedByDescending { it.amount }
        val gainLines = linesFor("gain")
        val lossLines = linesFor("loss")

        val totalIncome = incomeLines.sumOf { it.amount }
        val totalExpense = expenseLines.sumOf { it.amount }
        val totalGain = gainLines.sumOf { it.amount }
        val totalLoss = lossLines.sumOf { it.amount }
        val netIncome = totalIncome - totalExpense + totalGain - totalLoss

        val rows = mutableListOf<ReportRow>()

        if (incomeLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Income")
            incomeLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount) }
            rows += ReportRow.TotalLine("Total Income", totalIncome, emphasized = true)
        }
        if (expenseLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Expense")
            expenseLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount, contra = true) }
            rows += ReportRow.TotalLine("Total Expense", totalExpense, emphasized = true, contra = true)
        }
        if (gainLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Gain")
            gainLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount) }
            rows += ReportRow.TotalLine("Total Gain", totalGain, emphasized = true)
        }
        if (lossLines.isNotEmpty()) {
            rows += ReportRow.SectionHeader("Loss")
            lossLines.forEach { rows += ReportRow.AccountLine(it.name, it.amount, contra = true) }
            rows += ReportRow.TotalLine("Total Loss", totalLoss, emphasized = true, contra = true)
        }

        if (rows.isNotEmpty()) {
            rows += ReportRow.TotalLine("Net Income", netIncome, emphasized = true)
        }

        return rows
    }
}
