package dev.fitiavana.accounting.ui.transactions

object TransactionValidator {

    data class EntryData(
        val accountId: String,
        val debitAmount: Long?,
        val creditAmount: Long?,
        val instrumentDebitAmount: Long? = null,
        val instrumentCreditAmount: Long? = null,
        val intermediaryDebitAmount: Long? = null,
        val intermediaryCreditAmount: Long? = null
    )

    sealed class ValidationResult {
        object Valid : ValidationResult()
        sealed class Error : ValidationResult() {
            object DuplicateAccount : Error()
            object BothFilled : Error()
            object Incomplete : Error()
            object Unbalanced : Error()
            object MixedDebitCredit : Error()
        }
    }

    fun validate(entries: List<EntryData>): ValidationResult {
        val accountIds = entries.map { it.accountId }
        if (accountIds.size != accountIds.toSet().size) return ValidationResult.Error.DuplicateAccount

        for (entry in entries) {
            if (entry.debitAmount != null && entry.creditAmount != null) return ValidationResult.Error.BothFilled
            if (entry.debitAmount == null && entry.creditAmount == null) return ValidationResult.Error.Incomplete
        }

        for (entry in entries) {
            val baseIsDebit = entry.debitAmount != null
            if (entry.instrumentDebitAmount != null && !baseIsDebit) return ValidationResult.Error.MixedDebitCredit
            if (entry.instrumentCreditAmount != null && baseIsDebit) return ValidationResult.Error.MixedDebitCredit
            if (entry.intermediaryDebitAmount != null && !baseIsDebit) return ValidationResult.Error.MixedDebitCredit
            if (entry.intermediaryCreditAmount != null && baseIsDebit) return ValidationResult.Error.MixedDebitCredit
        }

        val totalDebit = entries.sumOf { it.debitAmount ?: 0L }
        val totalCredit = entries.sumOf { it.creditAmount ?: 0L }
        if (totalDebit != totalCredit) return ValidationResult.Error.Unbalanced

        return ValidationResult.Valid
    }
}
