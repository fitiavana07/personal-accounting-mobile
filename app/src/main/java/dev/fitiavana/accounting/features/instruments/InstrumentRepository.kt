package dev.fitiavana.accounting.features.instruments

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.accounts.AccountDao
import dev.fitiavana.accounting.features.instruments.InstrumentDao
import dev.fitiavana.accounting.features.instruments.Instrument

class InstrumentRepository(private val dao: InstrumentDao, private val accountDao: AccountDao) {
    fun getAll(): LiveData<List<Instrument>> = dao.getAll()
    fun getAllSync(): List<Instrument> = dao.getAllSync()
    fun getByCode(code: String): Instrument? = dao.getByCode(code)
    fun insert(instrument: Instrument) = dao.insert(instrument)
    fun update(instrument: Instrument) = dao.update(instrument)
    fun delete(instrument: Instrument) = dao.delete(instrument)
    fun hasAccounts(instrumentCode: String): Boolean = accountDao.hasAccountsWithInstrument(instrumentCode)
    fun hasIntermediaryAccounts(instrumentCode: String): Boolean =
        accountDao.hasAccountsWithIntermediaryInstrument(instrumentCode)
}