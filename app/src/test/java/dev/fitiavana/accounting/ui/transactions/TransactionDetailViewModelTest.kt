package dev.fitiavana.accounting.ui.transactions

import androidx.lifecycle.MutableLiveData
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.transactions.TransactionRepository
import dev.fitiavana.accounting.features.transactions.TransactionWithEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TransactionDetailViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var viewModel: TransactionDetailViewModel

    private val account = Account(id = "acc1", name = "Cash", type = "asset")
    private val instrument = Instrument(code = "USD", note = "Dollar", type = "fiat")

    @Before
    fun setUp() {
        transactionRepository = mock()
        accountRepository = mock()
        instrumentRepository = mock()
        viewModel = TransactionDetailViewModel(
            transactionRepository,
            accountRepository,
            instrumentRepository
        )
    }

    @Test
    fun `loadDetail returns null when transaction is not found`() {
        whenever(transactionRepository.getWithEntries("missing")).thenReturn(null)

        assertNull(viewModel.loadDetail("missing"))
    }

    @Test
    fun `loadDetail combines the transaction with accounts and instruments`() {
        val transaction = Transaction(
            id = "t1",
            createdAt = 0L,
            transactionDatetime = 0L,
            note = ""
        )
        val entry = TransactionEntry(
            id = "e1",
            transactionId = "t1",
            accountId = "acc1",
            debitAmount = 100L,
            creditAmount = null
        )
        val twe = TransactionWithEntries(transaction, listOf(entry))

        whenever(transactionRepository.getWithEntries("t1")).thenReturn(twe)
        whenever(accountRepository.getAll()).thenReturn(MutableLiveData(null))
        whenever(accountRepository.getAllSync()).thenReturn(listOf(account))
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(instrument))

        val detail = viewModel.loadDetail("t1")

        assertEquals(twe, detail?.transactionWithEntries)
        assertEquals(account, detail?.accountsById?.get("acc1"))
        assertEquals(instrument, detail?.instrumentsByCode?.get("USD"))
    }
}
