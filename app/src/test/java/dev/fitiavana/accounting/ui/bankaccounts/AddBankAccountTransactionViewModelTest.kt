package dev.fitiavana.accounting.ui.bankaccounts

import dev.fitiavana.accounting.features.bankaccounts.BankAccountRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class AddBankAccountTransactionViewModelTest {

    private val repository = mock<BankAccountRepository>()

    private fun viewModel() = AddBankAccountTransactionViewModel(repository, "a1")

    @Test
    fun `a credit is saved as a positive amount`() {
        val result = viewModel().saveSync(Direction.CREDIT, "1500", " salary ", 9_000)

        assertNull(result)
        verify(repository).addTransaction("a1", 1_500, "salary", 9_000)
    }

    @Test
    fun `a debit is saved as a negative amount`() {
        val result = viewModel().saveSync(Direction.DEBIT, "250", "", 9_000)

        assertNull(result)
        verify(repository).addTransaction("a1", -250, "", 9_000)
    }

    @Test
    fun `thousands separators and spaces in the amount are accepted`() {
        viewModel().saveSync(Direction.CREDIT, "1 234 567", "", 1)
        viewModel().saveSync(Direction.DEBIT, "2,000", "", 1)

        verify(repository).addTransaction("a1", 1_234_567, "", 1)
        verify(repository).addTransaction("a1", -2_000, "", 1)
    }

    @Test
    fun `an empty, zero, negative or malformed amount is rejected and saves nothing`() {
        listOf("", "0", "-5", "abc", "1.5").forEach {
            assertEquals(it, AddTransactionError.AMOUNT, viewModel().saveSync(Direction.CREDIT, it, "", 1))
        }

        verify(repository, never()).addTransaction(any(), any(), any(), any())
    }

    @Test
    fun `without a preselected account the picked one is used`() {
        val vm = AddBankAccountTransactionViewModel(repository, null)
        vm.selectedAccountId = "a2"

        assertNull(vm.saveSync(Direction.CREDIT, "10", "", 1))
        verify(repository).addTransaction("a2", 10, "", 1)
    }

    @Test
    fun `without any account selected nothing is saved`() {
        val vm = AddBankAccountTransactionViewModel(repository, null)

        assertEquals(AddTransactionError.ACCOUNT, vm.saveSync(Direction.CREDIT, "10", "", 1))
        verify(repository, never()).addTransaction(any(), any(), any(), any())
    }

    @Test
    fun `parseAmount gives a positive whole number or null`() {
        assertEquals(42L, AddBankAccountTransactionViewModel.parseAmount(" 42 "))
        assertNull(AddBankAccountTransactionViewModel.parseAmount("0"))
        assertNull(AddBankAccountTransactionViewModel.parseAmount("-1"))
        assertNull(AddBankAccountTransactionViewModel.parseAmount("99999999999999999999"))
    }
}
