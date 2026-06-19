package dev.fitiavana.accounting.ui.transactions

object TransactionValidator {

    data class EntryData(
        val accountId: String,
        val debitAmount: Int?,
        val creditAmount: Int?
    )

    sealed class ValidationResult {
        object Valid : ValidationResult()
        sealed class Error : ValidationResult() {
            object DuplicateAccount : Error()
            object BothFilled : Error()
            object Incomplete : Error()
            object Unbalanced : Error()
        }
    }

    fun validate(entries: List<EntryData>): ValidationResult {
        val accountIds = entries.map { it.accountId }
        if (accountIds.size != accountIds.toSet().size) return ValidationResult.Error.DuplicateAccount

        for (entry in entries) {
            if (entry.debitAmount != null && entry.creditAmount != null) return ValidationResult.Error.BothFilled
            if (entry.debitAmount == null && entry.creditAmount == null) return ValidationResult.Error.Incomplete
        }

        val totalDebit = entries.sumOf { it.debitAmount ?: 0 }
        val totalCredit = entries.sumOf { it.creditAmount ?: 0 }
        if (totalDebit != totalCredit) return ValidationResult.Error.Unbalanced

        return ValidationResult.Valid
    }
}
