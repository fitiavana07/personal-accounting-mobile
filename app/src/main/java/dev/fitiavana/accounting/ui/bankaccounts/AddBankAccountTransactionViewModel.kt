package dev.fitiavana.accounting.ui.bankaccounts

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository

/** A credit increases the balance, a debit decreases it. */
enum class Direction { CREDIT, DEBIT }

enum class AddTransactionError { AMOUNT }

/** Adding a transaction to one bank account from a direction and a positive amount. */
class AddBankAccountTransactionViewModel(
    private val repository: BankAccountRepository,
    private val accountId: String
) : ViewModel() {

    /** Saves in the background; [onDone] runs on the main thread with what is wrong, or null once saved. */
    fun save(
        direction: Direction,
        amountText: String,
        note: String,
        transactionDateTime: Long,
        onDone: (AddTransactionError?) -> Unit
    ) {
        if (parseAmount(amountText) == null) return onDone(AddTransactionError.AMOUNT)
        Thread {
            val result = saveSync(direction, amountText, note, transactionDateTime)
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    internal fun saveSync(
        direction: Direction,
        amountText: String,
        note: String,
        transactionDateTime: Long
    ): AddTransactionError? {
        val amount = parseAmount(amountText) ?: return AddTransactionError.AMOUNT
        val signed = if (direction == Direction.CREDIT) amount else -amount
        repository.addTransaction(accountId, signed, note.trim(), transactionDateTime)
        return null
    }

    companion object {
        /** A whole number above zero, ignoring spaces and thousands separators; null otherwise. */
        fun parseAmount(text: String): Long? =
            text.filter { !it.isWhitespace() && it != ',' }.toLongOrNull()?.takeIf { it > 0 }
    }
}

class AddBankAccountTransactionViewModelFactory(
    private val repository: BankAccountRepository,
    private val accountId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AddBankAccountTransactionViewModel(repository, accountId) as T
    }
}
