package dev.fitiavana.accounting.ui.instruments

import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

class EditInstrumentViewModel(private val repository: InstrumentRepository) : ViewModel() {

    fun getInstrument(code: String): Instrument? = repository.getByCode(code)

    fun saveInstrument(
        code: String,
        note: String,
        type: String,
        decimalPlaces: Int,
        coingeckoId: String? = null,
        stockApiSymbol: String? = null
    ) {
        repository.update(
            Instrument(
                code = code,
                note = note.trim(),
                type = type,
                decimalPlaces = decimalPlaces,
                coingeckoId = coingeckoId,
                stockApiSymbol = stockApiSymbol
            )
        )
    }

    fun saveNewInstrument(
        code: String,
        note: String,
        type: String,
        decimalPlaces: Int,
        coingeckoId: String? = null,
        stockApiSymbol: String? = null
    ) {
        repository.insert(
            Instrument(
                code = code.trim(),
                note = note.trim(),
                type = type,
                decimalPlaces = decimalPlaces,
                coingeckoId = coingeckoId,
                stockApiSymbol = stockApiSymbol
            )
        )
    }

    fun hasAccounts(instrumentCode: String): Boolean = repository.hasAccounts(instrumentCode)
    fun hasIntermediaryAccounts(instrumentCode: String): Boolean =
        repository.hasIntermediaryAccounts(instrumentCode)

    fun deleteInstrument(instrument: Instrument) {
        repository.delete(instrument)
    }
}
