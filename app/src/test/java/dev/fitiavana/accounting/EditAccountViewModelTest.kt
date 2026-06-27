package dev.fitiavana.accounting

import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.ui.editaccount.EditAccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EditAccountViewModelTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var viewModel: EditAccountViewModel

    @Before
    fun setUp() {
        accountRepository = mock()
        instrumentRepository = mock()
        viewModel = EditAccountViewModel(accountRepository, instrumentRepository)
    }

    // --- Account data class ---

    @Test
    fun `Account instrumentCode defaults to null`() {
        val account = Account(id = "1", name = "Cash", type = "asset")
        assertNull(account.instrumentCode)
    }

    // --- saveAccount: new account ---

    @Test
    fun `new account with instrument sets instrumentCode`() {
        viewModel.saveAccount(id = null, name = "Cash", type = "asset", instrumentCode = "USD", intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).insert(captor.capture())
        assertEquals("USD", captor.firstValue.instrumentCode)
    }

    @Test
    fun `new account without instrument sets instrumentCode to null`() {
        viewModel.saveAccount(id = null, name = "Cash", type = "asset", instrumentCode = null, intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).insert(captor.capture())
        assertNull(captor.firstValue.instrumentCode)
    }

    @Test
    fun `new account name is trimmed`() {
        viewModel.saveAccount(id = null, name = "  Cash  ", type = "asset", instrumentCode = null, intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).insert(captor.capture())
        assertEquals("Cash", captor.firstValue.name)
    }

    @Test
    fun `new account fields are set correctly`() {
        viewModel.saveAccount(id = null, name = "Revenue", type = "revenue", instrumentCode = "EUR", intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).insert(captor.capture())
        assertEquals("revenue", captor.firstValue.type)
        assertEquals("EUR", captor.firstValue.instrumentCode)
    }

    // --- saveAccount: existing account ---

    @Test
    fun `existing account update preserves instrumentCode`() {
        viewModel.saveAccount(id = "abc", name = "Cash", type = "asset", instrumentCode = "USD", intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).update(captor.capture())
        assertEquals("abc", captor.firstValue.id)
        assertEquals("USD", captor.firstValue.instrumentCode)
    }

    @Test
    fun `existing account update clears instrumentCode when null passed`() {
        viewModel.saveAccount(id = "abc", name = "Cash", type = "asset", instrumentCode = null, intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).update(captor.capture())
        assertNull(captor.firstValue.instrumentCode)
    }

    @Test
    fun `existing account name is trimmed on update`() {
        viewModel.saveAccount(id = "abc", name = "  Savings  ", type = "asset", instrumentCode = null, intermediaryInstrumentCode = null)

        val captor = argumentCaptor<Account>()
        verify(accountRepository).update(captor.capture())
        assertEquals("Savings", captor.firstValue.name)
    }
}