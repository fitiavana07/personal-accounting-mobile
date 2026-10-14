package dev.fitiavana.accounting.features.instruments

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.RecordKey
import dev.fitiavana.accounting.features.sync.SyncKind

class InstrumentRepository(
    private val dao: InstrumentDao,
    private val accountDao: AccountDao,
    private val tracker: ChangeTracker
) {
    fun getAll(): LiveData<List<Instrument>> = dao.getAll()
    fun getCryptocurrencies(): LiveData<List<Instrument>> = dao.getByType(Instrument.TYPE_CRYPTOCURRENCY)
    fun getAllSync(): List<Instrument> = dao.getAllSync()
    fun getByCode(code: String): Instrument? = dao.getByCode(code)

    fun insert(instrument: Instrument) = tracker.inTransaction {
        dao.insert(instrument)
        tracker.touch(SyncKind.INSTRUMENT, instrument.code)
    }

    fun update(instrument: Instrument) = tracker.inTransaction {
        dao.update(instrument)
        tracker.touch(SyncKind.INSTRUMENT, instrument.code)
    }

    fun delete(instrument: Instrument) = tracker.inTransaction {
        dao.delete(instrument)
        tracker.deleted(listOf(RecordKey(SyncKind.INSTRUMENT, instrument.code)))
    }

    fun hasAccounts(instrumentCode: String): Boolean =
        accountDao.hasAccountsWithInstrument(instrumentCode)

    fun hasIntermediaryAccounts(instrumentCode: String): Boolean =
        accountDao.hasAccountsWithIntermediaryInstrument(instrumentCode)
}
