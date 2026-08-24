package dev.fitiavana.accounting.features.balances

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
