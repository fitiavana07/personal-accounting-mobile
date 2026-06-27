package dev.fitiavana.accounting.data.model

object BalanceCalculator {
    fun compute(
        accountType: String,
        totalDebits: Long,
        totalCredits: Long
    ): Long {
        return when (accountType) {
            "asset", "expense", "drawing", "loss" -> totalDebits - totalCredits
            else -> totalCredits - totalDebits
        }
    }
}
