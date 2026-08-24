package dev.fitiavana.accounting.ui.editinstrument

import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.ui.editinstrument.EditInstrumentViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EditInstrumentViewModelTest {

    private lateinit var repository: InstrumentRepository
    private lateinit var viewModel: EditInstrumentViewModel

    @Before
    fun setUp() {
        repository = mock()
        viewModel = EditInstrumentViewModel(repository)
    }

    // --- saveInstrument (update) ---

    @Test
    fun `saveInstrument trims note`() {
        viewModel.saveInstrument(code = "USD", note = "  US Dollar  ", type = "currency", decimalPlaces = 2)

        val captor = argumentCaptor<Instrument>()
        verify(repository).update(captor.capture())
        assertEquals("US Dollar", captor.firstValue.note)
    }

    @Test
    fun `saveInstrument passes all fields correctly`() {
        viewModel.saveInstrument(code = "JPY", note = "Yen", type = "currency", decimalPlaces = 0)

        val captor = argumentCaptor<Instrument>()
        verify(repository).update(captor.capture())
        assertEquals("JPY", captor.firstValue.code)
        assertEquals("Yen", captor.firstValue.note)
        assertEquals("currency", captor.firstValue.type)
        assertEquals(0, captor.firstValue.decimalPlaces)
    }

    @Test
    fun `saveInstrument does not trim code`() {
        viewModel.saveInstrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)

        val captor = argumentCaptor<Instrument>()
        verify(repository).update(captor.capture())
        assertEquals("USD", captor.firstValue.code)
    }

    // --- saveNewInstrument (insert) ---

    @Test
    fun `saveNewInstrument trims code`() {
        viewModel.saveNewInstrument(code = "  EUR  ", note = "Euro", type = "currency", decimalPlaces = 2)

        val captor = argumentCaptor<Instrument>()
        verify(repository).insert(captor.capture())
        assertEquals("EUR", captor.firstValue.code)
    }

    @Test
    fun `saveNewInstrument trims note`() {
        viewModel.saveNewInstrument(code = "EUR", note = "  Euro  ", type = "currency", decimalPlaces = 2)

        val captor = argumentCaptor<Instrument>()
        verify(repository).insert(captor.capture())
        assertEquals("Euro", captor.firstValue.note)
    }

    @Test
    fun `saveNewInstrument passes all fields correctly`() {
        viewModel.saveNewInstrument(code = "BHD", note = "Dinar", type = "currency", decimalPlaces = 3)

        val captor = argumentCaptor<Instrument>()
        verify(repository).insert(captor.capture())
        assertEquals("BHD", captor.firstValue.code)
        assertEquals("Dinar", captor.firstValue.note)
        assertEquals("currency", captor.firstValue.type)
        assertEquals(3, captor.firstValue.decimalPlaces)
    }

    // --- hasAccounts ---

    @Test
    fun `hasAccounts delegates to repository`() {
        whenever(repository.hasAccounts("USD")).thenReturn(true)
        assertEquals(true, viewModel.hasAccounts("USD"))
        verify(repository).hasAccounts("USD")
    }

    @Test
    fun `hasAccounts returns false when no accounts`() {
        whenever(repository.hasAccounts("USD")).thenReturn(false)
        assertEquals(false, viewModel.hasAccounts("USD"))
    }

    // --- hasIntermediaryAccounts ---

    @Test
    fun `hasIntermediaryAccounts delegates to repository`() {
        whenever(repository.hasIntermediaryAccounts("EUR")).thenReturn(true)
        assertEquals(true, viewModel.hasIntermediaryAccounts("EUR"))
        verify(repository).hasIntermediaryAccounts("EUR")
    }

    @Test
    fun `hasIntermediaryAccounts returns false when none`() {
        whenever(repository.hasIntermediaryAccounts("EUR")).thenReturn(false)
        assertEquals(false, viewModel.hasIntermediaryAccounts("EUR"))
    }

    // --- deleteInstrument ---

    @Test
    fun `deleteInstrument calls repository delete`() {
        val instrument = Instrument(code = "USD", note = "", type = "currency", decimalPlaces = 2)
        viewModel.deleteInstrument(instrument)
        verify(repository).delete(instrument)
    }
}