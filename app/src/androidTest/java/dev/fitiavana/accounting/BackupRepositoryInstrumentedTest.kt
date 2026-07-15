package dev.fitiavana.accounting

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.model.Transaction
import dev.fitiavana.accounting.data.model.TransactionEntry
import dev.fitiavana.accounting.data.repository.BackupRepository
import dev.fitiavana.accounting.data.repository.RestoreResult
import dev.fitiavana.accounting.db.AppDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs backup/restore against a real in-memory SQLite database (not mocked DAOs), to verify
 * the delete/insert ordering actually satisfies the app's real foreign key constraints and
 * that a full export/restore round trip preserves data through the real Room + SQLite stack.
 */
@RunWith(AndroidJUnit4::class)
class BackupRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepository

    private val instrument = Instrument(code = "BTC", note = "Bitcoin", type = "crypto", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val intermediaryInstrument = Instrument(code = "AR", note = "Ariary", type = "fiat")
    private val account = Account(
        id = "acc1", name = "Wallet", type = "asset",
        instrumentCode = "BTC", intermediaryInstrumentCode = "AR"
    )
    private val transaction = Transaction(id = "tx1", createdAt = 100L, transactionDatetime = 200L, note = "Buy")
    private val entry = TransactionEntry(
        id = "entry1", transactionId = "tx1", accountId = "acc1",
        debitAmount = 500L, creditAmount = null,
        instrumentDebitAmount = 1L, intermediaryDebitAmount = 500L
    )
    private val balance = AccountBalance(
        accountId = "acc1", balance = 500L, instrumentBalance = 1L, intermediaryBalance = 500L,
        updatedAt = 300L, createdAt = 100L
    )
    private val rate = ExchangeRateCache(
        pairKey = "BTC:AR", instrumentCode = "BTC", intermediaryCode = "AR", rate = 12345.0, fetchedAt = 400L
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(
            db, db.accountDao(), db.instrumentDao(), db.transactionDao(),
            db.accountBalanceDao(), db.exchangeRateCacheDao()
        )

        db.instrumentDao().insert(instrument)
        db.instrumentDao().insert(intermediaryInstrument)
        db.accountDao().insert(account)
        db.transactionDao().insert(transaction)
        db.transactionDao().insertEntry(entry)
        db.accountBalanceDao().insert(balance)
        db.exchangeRateCacheDao().upsert(rate)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun exportProducesJsonContainingAllSeededRows() {
        val json = JSONObject(repository.export())

        assertEquals(AppDatabase.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals(2, json.getJSONArray("instruments").length())
        assertEquals(1, json.getJSONArray("accounts").length())
        assertEquals(1, json.getJSONArray("transactions").length())
        assertEquals(1, json.getJSONArray("transactionEntries").length())
        assertEquals(1, json.getJSONArray("accountBalances").length())
        assertEquals(1, json.getJSONArray("exchangeRateCache").length())
    }

    @Test
    fun restoreRoundTripPreservesDataThroughRealDatabase() {
        val exported = repository.export()

        val result = repository.restore(exported)

        assertEquals(RestoreResult.Success, result)
        assertEquals(listOf(account), db.accountDao().getAllSync())
        assertEquals(listOf(instrument, intermediaryInstrument).sortedBy { it.code }, db.instrumentDao().getAllSync())
        assertEquals(listOf(transaction), db.transactionDao().getAllTransactionsSync())
        assertEquals(listOf(entry), db.transactionDao().getAllEntriesSync())
        assertEquals(listOf(balance), db.accountBalanceDao().getAllSync())
        assertEquals(listOf(rate), db.exchangeRateCacheDao().getAllSync())
    }

    @Test
    fun restoreReplacesExistingDataNotPresentInBackupFile() {
        val exported = repository.export()
        // Data created after the backup was taken, absent from the file being restored.
        db.instrumentDao().insert(Instrument(code = "EUR", note = "Euro", type = "fiat"))
        db.accountDao().insert(Account(id = "acc2", name = "Extra", type = "asset"))

        repository.restore(exported)

        assertEquals(listOf(account), db.accountDao().getAllSync())
        assertEquals(setOf("BTC", "AR"), db.instrumentDao().getAllSync().map { it.code }.toSet())
    }

    @Test
    fun restoreWithMismatchedSchemaVersionLeavesExistingDataIntact() {
        val json = JSONObject(repository.export())
        json.put("schemaVersion", AppDatabase.SCHEMA_VERSION + 1)

        val result = repository.restore(json.toString())

        assertTrue(result is RestoreResult.SchemaMismatch)
        assertEquals(listOf(account), db.accountDao().getAllSync())
        assertEquals(1, db.transactionDao().getAllEntriesSync().size)
    }

    @Test
    fun restoreWithMalformedJsonLeavesExistingDataIntact() {
        val result = repository.restore("not valid json")

        assertTrue(result is RestoreResult.Error)
        assertEquals(listOf(account), db.accountDao().getAllSync())
    }
}
