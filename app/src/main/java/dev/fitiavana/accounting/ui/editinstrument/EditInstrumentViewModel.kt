package dev.fitiavana.accounting.ui.editinstrument

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.InstrumentRepository

class EditInstrumentViewModel(private val repository: InstrumentRepository) : ViewModel() {

    fun getInstrument(code: String): Instrument? = repository.getByCode(code)

    fun saveInstrument(code: String?, note: String, type: String) {
        if (code == null) {
            repository.insert(Instrument(code = "", note = note.trim(), type = type))
        } else {
            repository.update(Instrument(code = code, note = note.trim(), type = type))
        }
    }

    fun saveNewInstrument(code: String, note: String, type: String) {
        repository.insert(Instrument(code = code.trim(), note = note.trim(), type = type))
    }

    fun hasAccounts(instrumentCode: String): Boolean = repository.hasAccounts(instrumentCode)

    fun deleteInstrument(instrument: Instrument) {
        repository.delete(instrument)
    }
}
