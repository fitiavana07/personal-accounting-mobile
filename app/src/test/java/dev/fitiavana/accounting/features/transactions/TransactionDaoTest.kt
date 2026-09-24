package dev.fitiavana.accounting.features.transactions

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class TransactionDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = db.transactionDao()
        accountDao = db.accountDao()

        accountDao.insert(Account(id = "acc1", name = "Cash", type = "asset"))
        accountDao.insert(Account(id = "acc2", name = "Bank", type = "asset"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun <T> LiveData<T>.getOrAwaitValue(): T {
        var data: T? = null
        val observer = Observer<T> { data = it }
        observeForever(observer)
        try {
            @Suppress("UNCHECKED_CAST")
            return data as T
        } finally {
            removeObserver(observer)
        }
    }

    private fun transaction(id: String, datetimeMs: Long) = Transaction(
        id = id,
        createdAt = datetimeMs,
        transactionDatetime = datetimeMs,
        note = "note-$id"
    )

    private fun entry(id: String, transactionId: String, accountId: String, debit: Long? = null, credit: Long? = null) =
        TransactionEntry(
            id = id,
            transactionId = transactionId,
            accountId = accountId,
            debitAmount = debit,
            creditAmount = credit
        )

    // getFilteredWithEntries

    @Test
    fun `getFilteredWithEntries returns transactions within date range`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 2_000L))
        transactionDao.insert(transaction("t3", 3_000L))

        val result = transactionDao.getFilteredWithEntries(startMs = 1_500L, endMs = 2_500L, accountId = null)
            .getOrAwaitValue()

        assertEquals(listOf("t2"), result.map { it.transaction.id })
    }

    @Test
    fun `getFilteredWithEntries excludes transactions outside date range`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 5_000L))

        val result = transactionDao.getFilteredWithEntries(startMs = 0L, endMs = 4_000L, accountId = null)
            .getOrAwaitValue()

        assertEquals(listOf("t1"), result.map { it.transaction.id })
    }

    @Test
    fun `getFilteredWithEntries orders results by datetime descending`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 3_000L))
        transactionDao.insert(transaction("t3", 2_000L))

        val result = transactionDao.getFilteredWithEntries(startMs = 0L, endMs = 10_000L, accountId = null)
            .getOrAwaitValue()

        assertEquals(listOf("t2", "t3", "t1"), result.map { it.transaction.id })
    }

    @Test
    fun `getFilteredWithEntries with null accountId returns transactions for all accounts`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 2_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", debit = 100L))
        transactionDao.insertEntry(entry("e2", "t2", "acc2", credit = 200L))

        val result = transactionDao.getFilteredWithEntries(startMs = 0L, endMs = 10_000L, accountId = null)
            .getOrAwaitValue()

        assertEquals(setOf("t1", "t2"), result.map { it.transaction.id }.toSet())
    }

    @Test
    fun `getFilteredWithEntries with accountId filters to transactions touching that account`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 2_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", debit = 100L))
        transactionDao.insertEntry(entry("e2", "t2", "acc2", credit = 200L))

        val result = transactionDao.getFilteredWithEntries(startMs = 0L, endMs = 10_000L, accountId = "acc1")
            .getOrAwaitValue()

        assertEquals(listOf("t1"), result.map { it.transaction.id })
    }

    @Test
    fun `getFilteredWithEntries eagerly loads entries for each transaction`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", debit = 100L))
        transactionDao.insertEntry(entry("e2", "t1", "acc2", credit = 100L))

        val result = transactionDao.getFilteredWithEntries(startMs = 0L, endMs = 10_000L, accountId = null)
            .getOrAwaitValue()

        assertEquals(2, result.single().entries.size)
    }

    // sum*ForAccountBetween (date-range aggregation)

    @Test
    fun `sumDebitsForAccountBetween only sums entries whose transaction falls within range`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 5_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", debit = 100L))
        transactionDao.insertEntry(entry("e2", "t2", "acc1", debit = 900L))

        val sum = transactionDao.sumDebitsForAccountBetween("acc1", startMs = 0L, endMs = 2_000L)

        assertEquals(100L, sum)
    }

    @Test
    fun `sumCreditsForAccountBetween returns zero when no entries in range`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", credit = 100L))

        val sum = transactionDao.sumCreditsForAccountBetween("acc1", startMs = 5_000L, endMs = 9_000L)

        assertEquals(0L, sum)
    }

    @Test
    fun `sumDebitsForAccountUpTo includes entries at or before the cutoff, excludes after`() {
        transactionDao.insert(transaction("t1", 1_000L))
        transactionDao.insert(transaction("t2", 2_000L))
        transactionDao.insertEntry(entry("e1", "t1", "acc1", debit = 100L))
        transactionDao.insertEntry(entry("e2", "t2", "acc1", debit = 200L))

        assertEquals(100L, transactionDao.sumDebitsForAccountUpTo("acc1", asOfMs = 1_000L))
        assertEquals(300L, transactionDao.sumDebitsForAccountUpTo("acc1", asOfMs = 2_000L))
    }

    // MIN/MAX transactionDatetime

    @Test
    fun `getMinTransactionDatetime and getMaxTransactionDatetime span the recorded range`() {
        transactionDao.insert(transaction("t1", 3_000L))
        transactionDao.insert(transaction("t2", 1_000L))
        transactionDao.insert(transaction("t3", 2_000L))

        assertEquals(1_000L, transactionDao.getMinTransactionDatetime())
        assertEquals(3_000L, transactionDao.getMaxTransactionDatetime())
    }

    @Test
    fun `getMinTransactionDatetime and getMaxTransactionDatetime are null when no transactions exist`() {
        assertNull(transactionDao.getMinTransactionDatetime())
        assertNull(transactionDao.getMaxTransactionDatetime())
    }
}
