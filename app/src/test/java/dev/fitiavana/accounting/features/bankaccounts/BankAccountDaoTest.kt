package dev.fitiavana.accounting.features.bankaccounts

import dev.fitiavana.accounting.db.AppDatabase
import dev.fitiavana.accounting.db.inMemoryDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BankAccountDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BankAccountDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.bankAccountDao()
    }

    @After
    fun tearDown() = db.close()

    private fun account(id: String, name: String = "Bank $id", balance: Long = 0, balanceDate: Long? = null) =
        BankAccount(id, name, balance, balanceDate)

    private fun tx(id: String, accountId: String, amount: Long, date: Long, createdAt: Long = date) =
        BankAccountTransaction(id, accountId, amount, "note $id", createdAt, date)

    @Test
    fun `accounts come back sorted by name`() {
        dao.insertAccounts(listOf(account("1", "Zed"), account("2", "Alpha")))

        assertEquals(listOf("Alpha", "Zed"), dao.getAllAccountsSync().map { it.name })
    }

    @Test
    fun `getAccount returns one account or null`() {
        dao.insertAccount(account("a", balance = 5, balanceDate = 9))

        assertEquals(account("a", balance = 5, balanceDate = 9), dao.getAccountSync("a"))
        assertNull(dao.getAccountSync("other"))
    }

    @Test
    fun `inserting an existing id replaces the account`() {
        dao.insertAccount(account("a", "Old"))

        dao.insertAccount(account("a", "New"))

        assertEquals(listOf("New"), dao.getAllAccountsSync().map { it.name })
    }

    @Test
    fun `updating an account keeps its transactions`() {
        dao.insertAccount(account("a", "Old"))
        dao.insertTransactions(listOf(tx("1", "a", 1, 1)))

        dao.insertAccount(account("a", "New", balance = 1, balanceDate = 1))

        assertEquals(listOf("1"), dao.getTransactionsSync("a").map { it.id })
    }

    @Test
    fun `transactions of an account come back newest first then by creation`() {
        dao.insertAccounts(listOf(account("a"), account("b")))
        dao.insertTransactions(
            listOf(
                tx("old", "a", 1, date = 10),
                tx("new", "a", 1, date = 30),
                tx("same-date-late", "a", 1, date = 20, createdAt = 99),
                tx("same-date-early", "a", 1, date = 20, createdAt = 5),
                tx("other", "b", 1, date = 40)
            )
        )

        assertEquals(
            listOf("new", "same-date-late", "same-date-early", "old"),
            dao.getTransactionsSync("a").map { it.id }
        )
    }

    @Test
    fun `transactions sum gives the balance and the latest date`() {
        dao.insertAccount(account("a"))
        dao.insertTransactions(listOf(tx("1", "a", 100, 10), tx("2", "a", -30, 50), tx("3", "a", 5, 20)))

        assertEquals(75L, dao.sumOfTransactions("a"))
        assertEquals(50L, dao.latestTransactionDate("a"))
    }

    @Test
    fun `sum and latest date are empty without transactions`() {
        dao.insertAccount(account("a"))

        assertEquals(0L, dao.sumOfTransactions("a"))
        assertNull(dao.latestTransactionDate("a"))
    }

    @Test
    fun `deleting all accounts also removes their transactions`() {
        dao.insertAccount(account("a"))
        dao.insertTransactions(listOf(tx("1", "a", 1, 1)))

        dao.deleteAllAccounts()

        assertEquals(emptyList<BankAccountTransaction>(), dao.getAllTransactionsSync())
    }
}
