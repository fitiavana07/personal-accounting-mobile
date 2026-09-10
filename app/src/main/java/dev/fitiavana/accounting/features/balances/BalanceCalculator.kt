package dev.fitiavana.accounting.features.balances

object BalanceCalculator {
    /**
     * Computes an account's balance based on its normal balance side.
     *
     * Asset, expense, drawing, and loss accounts have a normal debit
     * balance, so debits increase and credits decrease them. All other
     * account types (liability, equity, revenue, gain) have a normal
     * credit balance, so the subtraction is reversed.
     */
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

    /**
     * The balance [currentBalance] would become once a pending [debit] and
     * [credit] are applied, following the account's normal balance side.
     * Used to preview the effect of a transaction that is still being entered.
     */
    fun project(
        accountType: String,
        currentBalance: Long,
        debit: Long,
        credit: Long
    ): Long = currentBalance + compute(accountType, debit, credit)
}
