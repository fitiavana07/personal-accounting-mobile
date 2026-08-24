package dev.fitiavana.accounting.features.balances

import dev.fitiavana.accounting.features.balances.AccountBalanceDao
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BalanceRepositoryTest {

    private lateinit var accountDao: AccountDao
    private lateinit var balanceDao: AccountBalanceDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: BalanceRepository

    @Before
    fun setUp() {
        accountDao = mock()
        balanceDao = mock()
        transactionDao = mock()
        repository = BalanceRepository(accountDao, balanceDao, transactionDao)

        whenever(transactionDao.sumDebitsForAccount(any())).thenReturn(0L)
        whenever(transactionDao.sumCreditsForAccount(any())).thenReturn(0L)
        whenever(transactionDao.sumInstrumentDebitsForAccount(any())).thenReturn(0L)
        whenever(transactionDao.sumInstrumentCreditsForAccount(any())).thenReturn(0L)
        whenever(transactionDao.sumIntermediaryDebitsForAccount(any())).thenReturn(0L)
        whenever(transactionDao.sumIntermediaryCreditsForAccount(any())).thenReturn(0L)
        whenever(balanceDao.getByAccountId(any())).thenReturn(null)
    }

    // --- recalculateForAccount: balance calculation ---

    @Test
    fun `recalculateForAccount computes asset balance as debits minus credits`() {
        whenever(transactionDao.sumDebitsForAccount("acc1")).thenReturn(1000L)
        whenever(transactionDao.sumCreditsForAccount("acc1")).thenReturn(300L)

        repository.recalculateForAccount("acc1", "asset")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals(700L, captor.firstValue.balance)
    }

    @Test
    fun `recalculateForAccount computes liability balance as credits minus debits`() {
        whenever(transactionDao.sumDebitsForAccount("acc1")).thenReturn(200L)
        whenever(transactionDao.sumCreditsForAccount("acc1")).thenReturn(500L)

        repository.recalculateForAccount("acc1", "liability")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals(300L, captor.firstValue.balance)
    }

    @Test
    fun `recalculateForAccount computes instrumentBalance correctly`() {
        whenever(transactionDao.sumInstrumentDebitsForAccount("acc1")).thenReturn(400L)
        whenever(transactionDao.sumInstrumentCreditsForAccount("acc1")).thenReturn(100L)

        repository.recalculateForAccount("acc1", "asset")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals(300L, captor.firstValue.instrumentBalance)
    }

    @Test
    fun `recalculateForAccount computes intermediaryBalance correctly`() {
        whenever(transactionDao.sumIntermediaryDebitsForAccount("acc1")).thenReturn(600L)
        whenever(transactionDao.sumIntermediaryCreditsForAccount("acc1")).thenReturn(150L)

        repository.recalculateForAccount("acc1", "asset")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals(450L, captor.firstValue.intermediaryBalance)
    }

    // --- recalculateForAccount: createdAt preservation ---

    @Test
    fun `recalculateForAccount sets createdAt to now when no existing balance`() {
        whenever(balanceDao.getByAccountId("acc1")).thenReturn(null)

        val before = System.currentTimeMillis()
        repository.recalculateForAccount("acc1", "asset")
        val after = System.currentTimeMillis()

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        val createdAt = captor.firstValue.createdAt
        assert(createdAt in before..after) { "createdAt=$createdAt not in [$before, $after]" }
    }

    @Test
    fun `recalculateForAccount preserves existing createdAt on update`() {
        val originalCreatedAt = 123456789L
        val existing = AccountBalance(
            accountId = "acc1", balance = 0, updatedAt = 0, createdAt = originalCreatedAt
        )
        whenever(balanceDao.getByAccountId("acc1")).thenReturn(existing)

        repository.recalculateForAccount("acc1", "asset")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals(originalCreatedAt, captor.firstValue.createdAt)
    }

    @Test
    fun `recalculateForAccount sets updatedAt to approximately now`() {
        val before = System.currentTimeMillis()
        repository.recalculateForAccount("acc1", "asset")
        val after = System.currentTimeMillis()

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        val updatedAt = captor.firstValue.updatedAt
        assert(updatedAt in before..after) { "updatedAt=$updatedAt not in [$before, $after]" }
    }

    @Test
    fun `recalculateForAccount sets accountId correctly`() {
        repository.recalculateForAccount("acc1", "asset")

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao).insert(captor.capture())
        assertEquals("acc1", captor.firstValue.accountId)
    }

    // --- recalculateAll ---

    @Test
    fun `recalculateAll calls recalculate for each account`() {
        val accounts = listOf(
            Account(id = "acc1", name = "Cash", type = "asset"),
            Account(id = "acc2", name = "Revenue", type = "revenue")
        )
        whenever(accountDao.getAllSync()).thenReturn(accounts)

        repository.recalculateAll()

        verify(balanceDao, times(2)).insert(any())
    }

    @Test
    fun `recalculateAll with no accounts does nothing`() {
        whenever(accountDao.getAllSync()).thenReturn(emptyList())

        repository.recalculateAll()

        verify(balanceDao, never()).insert(any())
    }

    @Test
    fun `recalculateAll uses each account type for balance calculation`() {
        val accounts = listOf(
            Account(id = "acc1", name = "Cash", type = "asset"),
            Account(id = "acc2", name = "Loan", type = "liability")
        )
        whenever(accountDao.getAllSync()).thenReturn(accounts)
        whenever(transactionDao.sumDebitsForAccount("acc1")).thenReturn(500L)
        whenever(transactionDao.sumCreditsForAccount("acc1")).thenReturn(100L)
        whenever(transactionDao.sumDebitsForAccount("acc2")).thenReturn(200L)
        whenever(transactionDao.sumCreditsForAccount("acc2")).thenReturn(800L)

        repository.recalculateAll()

        val captor = argumentCaptor<AccountBalance>()
        verify(balanceDao, times(2)).insert(captor.capture())
        val balanceMap = captor.allValues.associateBy { it.accountId }
        assertEquals(400L, balanceMap["acc1"]?.balance)
        assertEquals(600L, balanceMap["acc2"]?.balance)
    }

    // --- hasTransactions ---

    @Test
    fun `hasTransactions returns true when entries exist`() {
        whenever(transactionDao.countEntriesForAccount("acc1")).thenReturn(3)
        assertEquals(true, repository.hasTransactions("acc1"))
    }

    @Test
    fun `hasTransactions returns false when no entries`() {
        whenever(transactionDao.countEntriesForAccount("acc1")).thenReturn(0)
        assertEquals(false, repository.hasTransactions("acc1"))
    }

    // --- computeBalancesAsOf ---

    @Test
    fun `computeBalancesAsOf computes balance for each account using up-to-date sums`() {
        val accounts = listOf(
            Account(id = "acc1", name = "Cash", type = "asset"),
            Account(id = "acc2", name = "Loan", type = "liability")
        )
        whenever(accountDao.getAllSync()).thenReturn(accounts)
        whenever(transactionDao.sumDebitsForAccountUpTo("acc1", 500L)).thenReturn(1000L)
        whenever(transactionDao.sumCreditsForAccountUpTo("acc1", 500L)).thenReturn(300L)
        whenever(transactionDao.sumDebitsForAccountUpTo("acc2", 500L)).thenReturn(200L)
        whenever(transactionDao.sumCreditsForAccountUpTo("acc2", 500L)).thenReturn(800L)

        val result = repository.computeBalancesAsOf(500L)

        assertEquals(700L, result["acc1"])
        assertEquals(600L, result["acc2"])
    }

    @Test
    fun `computeBalancesAsOf with no accounts returns empty map`() {
        whenever(accountDao.getAllSync()).thenReturn(emptyList())
        assertEquals(emptyMap<String, Long>(), repository.computeBalancesAsOf(500L))
    }

    // --- getTransactionDateRange ---

    @Test
    fun `getTransactionDateRange returns null when there are no transactions`() {
        whenever(transactionDao.getMinTransactionDatetime()).thenReturn(null)
        whenever(transactionDao.getMaxTransactionDatetime()).thenReturn(null)
        assertNull(repository.getTransactionDateRange())
    }

    @Test
    fun `getTransactionDateRange returns min and max transaction datetimes`() {
        whenever(transactionDao.getMinTransactionDatetime()).thenReturn(100L)
        whenever(transactionDao.getMaxTransactionDatetime()).thenReturn(900L)
        assertEquals(100L to 900L, repository.getTransactionDateRange())
    }
}