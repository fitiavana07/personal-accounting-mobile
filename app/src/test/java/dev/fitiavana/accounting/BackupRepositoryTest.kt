package dev.fitiavana.accounting

import dev.fitiavana.accounting.features.balances.AccountBalanceDao
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCacheDao
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.transactions.TransactionDao
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry
import dev.fitiavana.accounting.features.backup.BackupRepository
import dev.fitiavana.accounting.features.backup.RestoreResult
import dev.fitiavana.accounting.db.AppDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BackupRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var instrumentDao: InstrumentDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var balanceDao: AccountBalanceDao
    private lateinit var exchangeRateCacheDao: ExchangeRateCacheDao
    private lateinit var repository: BackupRepository

    private val instrument = Instrument(
        code = "BTC", note = "Bitcoin", type = "crypto", decimalPlaces = 8, coingeckoId = "bitcoin"
    )
    private val instrumentNoCoingeckoId = Instrument(code = "AR", note = "Ariary", type = "fiat")
    private val account = Account(
        id = "acc1", name = "Wallet", type = "asset",
        instrumentCode = "BTC", intermediaryInstrumentCode = "AR"
    )
    private val accountNoInstrument = Account(id = "acc2", name = "Cash", type = "asset")
    private val transaction = Transaction(
        id = "tx1", createdAt = 100L, transactionDatetime = 200L, note = "Buy"
    )
    private val entry = TransactionEntry(
        id = "entry1", transactionId = "tx1", accountId = "acc1",
        debitAmount = 500L, creditAmount = null,
        instrumentDebitAmount = 1L, instrumentCreditAmount = null,
        intermediaryDebitAmount = 500L, intermediaryCreditAmount = null
    )
    private val entryNoOptionalAmounts = TransactionEntry(
        id = "entry2", transactionId = "tx1", accountId = "acc2",
        debitAmount = null, creditAmount = 500L
    )
    private val balance = AccountBalance(
        accountId = "acc1", balance = 500L, instrumentBalance = 1L, intermediaryBalance = 500L,
        updatedAt = 300L, createdAt = 100L
    )
    private val rate = ExchangeRateCache(
        pairKey = "BTC:AR", instrumentCode = "BTC", intermediaryCode = "AR",
        rate = 12345.0, fetchedAt = 400L
    )

    @Before
    fun setUp() {
        database = mock()
        accountDao = mock()
        instrumentDao = mock()
        transactionDao = mock()
        balanceDao = mock()
        exchangeRateCacheDao = mock()
        repository = BackupRepository(database, accountDao, instrumentDao, transactionDao, balanceDao, exchangeRateCacheDao)

        whenever(database.runInTransaction(any<Runnable>())).thenAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
        }

        whenever(instrumentDao.getAllSync()).thenReturn(listOf(instrument, instrumentNoCoingeckoId))
        whenever(accountDao.getAllSync()).thenReturn(listOf(account, accountNoInstrument))
        whenever(transactionDao.getAllTransactionsSync()).thenReturn(listOf(transaction))
        whenever(transactionDao.getAllEntriesSync()).thenReturn(listOf(entry, entryNoOptionalAmounts))
        whenever(balanceDao.getAllSync()).thenReturn(listOf(balance))
        whenever(exchangeRateCacheDao.getAllSync()).thenReturn(listOf(rate))
    }

    // --- export ---

    @Test
    fun `export includes current schema version`() {
        val json = JSONObject(repository.export())
        assertEquals(AppDatabase.SCHEMA_VERSION, json.getInt("schemaVersion"))
    }

    @Test
    fun `export includes all rows from every table`() {
        val json = JSONObject(repository.export())
        assertEquals(2, json.getJSONArray("instruments").length())
        assertEquals(2, json.getJSONArray("accounts").length())
        assertEquals(1, json.getJSONArray("transactions").length())
        assertEquals(2, json.getJSONArray("transactionEntries").length())
        assertEquals(1, json.getJSONArray("accountBalances").length())
        assertEquals(1, json.getJSONArray("exchangeRateCache").length())
    }

    @Test
    fun `export omits null optional fields rather than writing them as null`() {
        val json = JSONObject(repository.export())
        val accountJson = json.getJSONArray("accounts").getJSONObject(1)
        assertEquals("acc2", accountJson.getString("id"))
        assertTrue(!accountJson.has("instrumentCode"))
    }

    // --- restore: round trip ---

    @Test
    fun `restore round trip reproduces exported data via insertAll calls`() {
        val exported = repository.export()
        val result = repository.restore(exported)

        assertEquals(RestoreResult.Success, result)
        verify(instrumentDao).insertAll(listOf(instrument, instrumentNoCoingeckoId))
        verify(accountDao).insertAll(listOf(account, accountNoInstrument))
        verify(transactionDao).insertAllTransactions(listOf(transaction))
        verify(transactionDao).insertAllEntries(listOf(entry, entryNoOptionalAmounts))
        verify(balanceDao).insertAll(listOf(balance))
        verify(exchangeRateCacheDao).insertAll(listOf(rate))
    }

    @Test
    fun `restore deletes children before parents and inserts parents before children`() {
        val exported = repository.export()
        repository.restore(exported)

        val order = inOrder(transactionDao, balanceDao, accountDao, exchangeRateCacheDao, instrumentDao)
        order.verify(transactionDao).deleteAllEntries()
        order.verify(transactionDao).deleteAll()
        order.verify(balanceDao).deleteAll()
        order.verify(accountDao).deleteAll()
        order.verify(exchangeRateCacheDao).deleteAll()
        order.verify(instrumentDao).deleteAll()
        order.verify(instrumentDao).insertAll(any())
        order.verify(accountDao).insertAll(any())
        order.verify(transactionDao).insertAllTransactions(any())
        order.verify(transactionDao).insertAllEntries(any())
        order.verify(balanceDao).insertAll(any())
        order.verify(exchangeRateCacheDao).insertAll(any())
    }

    // --- restore: schema mismatch ---

    @Test
    fun `restore with different schema version returns SchemaMismatch`() {
        val json = JSONObject(repository.export())
        json.put("schemaVersion", AppDatabase.SCHEMA_VERSION + 1)

        val result = repository.restore(json.toString())

        assertEquals(RestoreResult.SchemaMismatch(AppDatabase.SCHEMA_VERSION + 1, AppDatabase.SCHEMA_VERSION), result)
    }

    @Test
    fun `restore with schema mismatch does not touch the database`() {
        val json = JSONObject(repository.export())
        json.put("schemaVersion", AppDatabase.SCHEMA_VERSION + 1)

        repository.restore(json.toString())

        verify(instrumentDao, never()).deleteAll()
        verify(instrumentDao, never()).insertAll(any())
    }

    // --- restore: malformed input ---

    @Test
    fun `restore with invalid JSON returns Error`() {
        val result = repository.restore("not valid json")
        assertTrue(result is RestoreResult.Error)
    }

    @Test
    fun `restore with missing schema version returns Error`() {
        val result = repository.restore("{}")
        assertTrue(result is RestoreResult.Error)
    }

    @Test
    fun `restore with missing table array returns Error`() {
        val json = JSONObject().put("schemaVersion", AppDatabase.SCHEMA_VERSION)
        val result = repository.restore(json.toString())
        assertTrue(result is RestoreResult.Error)
    }

    @Test
    fun `restore with malformed input does not touch the database`() {
        repository.restore("not valid json")
        verify(instrumentDao, never()).deleteAll()
    }
}
