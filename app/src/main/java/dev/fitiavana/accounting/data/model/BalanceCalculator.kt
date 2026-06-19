package dev.fitiavana.accounting.data.model

object BalanceCalculator {
    fun compute(accountType: String, totalDebits: Int, totalCredits: Int): Int {
        return when (accountType) {
            "asset", "expense" -> totalDebits - totalCredits
            else -> totalCredits - totalDebits // liability, equity, revenue
        }
    }
}
