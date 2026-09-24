package dev.fitiavana.accounting.features.balances

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountBalanceDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var balanceDao: AccountBalanceDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        balanceDao = db.accountBalanceDao()
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

    private fun balance(accountId: String, amount: Long) = AccountBalance(
        accountId = accountId,
        balance = amount,
        updatedAt = 1_000L,
        createdAt = 1_000L
    )

    @Test
    fun `getByAccountId returns null when no balance recorded`() {
        assertNull(balanceDao.getByAccountId("acc1"))
    }

    @Test
    fun `getByAccountId returns the matching balance`() {
        balanceDao.insert(balance("acc1", 500L))
        balanceDao.insert(balance("acc2", 900L))

        val result = balanceDao.getByAccountId("acc1")

        assertEquals(500L, result?.balance)
    }

    @Test
    fun `insert with REPLACE conflict strategy overwrites the existing balance for the account`() {
        balanceDao.insert(balance("acc1", 100L))
        balanceDao.insert(balance("acc1", 250L))

        assertEquals(250L, balanceDao.getByAccountId("acc1")?.balance)
        assertEquals(1, balanceDao.getAllSync().size)
    }

    @Test
    fun `insertAll replaces existing balances and adds new ones`() {
        balanceDao.insert(balance("acc1", 100L))
        balanceDao.insertAll(listOf(balance("acc1", 111L), balance("acc2", 222L)))

        assertEquals(111L, balanceDao.getByAccountId("acc1")?.balance)
        assertEquals(222L, balanceDao.getByAccountId("acc2")?.balance)
        assertEquals(2, balanceDao.getAllSync().size)
    }

    @Test
    fun `getAllSync and getAll LiveData return every stored balance`() {
        balanceDao.insertAll(listOf(balance("acc1", 100L), balance("acc2", 200L)))

        assertEquals(2, balanceDao.getAllSync().size)
        assertEquals(2, balanceDao.getAll().getOrAwaitValue().size)
    }

    @Test
    fun `deleteAll clears every balance`() {
        balanceDao.insertAll(listOf(balance("acc1", 100L), balance("acc2", 200L)))

        balanceDao.deleteAll()

        assertTrue(balanceDao.getAllSync().isEmpty())
    }
}
