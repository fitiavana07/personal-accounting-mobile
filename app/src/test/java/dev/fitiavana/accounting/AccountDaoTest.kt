package dev.fitiavana.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.dao.InstrumentDao
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var instrumentDao: InstrumentDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountDao = db.accountDao()
        instrumentDao = db.instrumentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // hasAccountsWithInstrument

    @Test
    fun `hasAccountsWithInstrument returns false when no accounts exist`() {
        assertFalse(accountDao.hasAccountsWithInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithInstrument returns false when account has no instrument`() {
        accountDao.insert(Account(id = "1", name = "Cash", type = "asset", instrumentCode = null))
        assertFalse(accountDao.hasAccountsWithInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithInstrument returns true when account has matching instrument`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "Cash", type = "asset", instrumentCode = "USD"))
        assertTrue(accountDao.hasAccountsWithInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithInstrument returns false when account has different instrument`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        instrumentDao.insert(Instrument(code = "EUR", note = "Euro", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "Cash", type = "asset", instrumentCode = "EUR"))
        assertFalse(accountDao.hasAccountsWithInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithInstrument returns true when one of multiple accounts has instrument`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "Savings", type = "asset", instrumentCode = null))
        accountDao.insert(Account(id = "2", name = "Cash USD", type = "asset", instrumentCode = "USD"))
        assertTrue(accountDao.hasAccountsWithInstrument("USD"))
    }

    // hasAccountsWithIntermediaryInstrument

    @Test
    fun `hasAccountsWithIntermediaryInstrument returns false when no accounts exist`() {
        assertFalse(accountDao.hasAccountsWithIntermediaryInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithIntermediaryInstrument returns false when account has no intermediary instrument`() {
        accountDao.insert(Account(id = "1", name = "Cash", type = "asset"))
        assertFalse(accountDao.hasAccountsWithIntermediaryInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithIntermediaryInstrument returns true when account has matching intermediary instrument`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "FX", type = "asset", intermediaryInstrumentCode = "USD"))
        assertTrue(accountDao.hasAccountsWithIntermediaryInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithIntermediaryInstrument returns false when account has different intermediary instrument`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        instrumentDao.insert(Instrument(code = "EUR", note = "Euro", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "FX", type = "asset", intermediaryInstrumentCode = "EUR"))
        assertFalse(accountDao.hasAccountsWithIntermediaryInstrument("USD"))
    }

    @Test
    fun `hasAccountsWithIntermediaryInstrument not confused with instrumentCode`() {
        instrumentDao.insert(Instrument(code = "USD", note = "US Dollar", type = "currency", decimalPlaces = 2))
        accountDao.insert(Account(id = "1", name = "Cash", type = "asset", instrumentCode = "USD", intermediaryInstrumentCode = null))
        assertFalse(accountDao.hasAccountsWithIntermediaryInstrument("USD"))
    }
}