package dev.fitiavana.accounting.data.repository

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.data.dao.AccountDao
import dev.fitiavana.accounting.data.dao.InstrumentDao
import dev.fitiavana.accounting.data.model.Instrument

class InstrumentRepository(private val dao: InstrumentDao, private val accountDao: AccountDao) {
    fun getAll(): LiveData<List<Instrument>> = dao.getAll()
    fun getByCode(code: String): Instrument? = dao.getByCode(code)
    fun insert(instrument: Instrument) = dao.insert(instrument)
    fun update(instrument: Instrument) = dao.update(instrument)
    fun delete(instrument: Instrument) = dao.delete(instrument)
    fun hasAccounts(instrumentCode: String): Boolean = accountDao.hasAccountsWithInstrument(instrumentCode)
}