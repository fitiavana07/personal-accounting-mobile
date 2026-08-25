package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HomeDetailViewModelTest {

    private lateinit var balanceRepository: BalanceRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var exchangeRateRepository: ExchangeRateRepository
    private lateinit var viewModel: HomeDetailViewModel

    private val btc = Instrument(
        code = "BTC",
        note = "Bitcoin",
        type = "cryptocurrency",
        decimalPlaces = 8
    )
    private val ar = Instrument(code = "AR", note = "Ariary", type = "fiat")
    private val account = Account(
        id = "acc1",
        name = "Crypto Wallet",
        type = "asset",
        instrumentCode = "BTC",
        intermediaryInstrumentCode = "AR"
    )
    private val balance = AccountBalance(
        accountId = "acc1",
        balance = 50_000L,
        instrumentBalance = 100_000_000L,
        intermediaryBalance = 50_000L,
        updatedAt = 0L,
        createdAt = 0L
    )

    @Before
    fun setUp() {
        balanceRepository = mock()
        accountRepository = mock()
        instrumentRepository = mock()
        exchangeRateRepository = mock()
        viewModel = HomeDetailViewModel(
            balanceRepository,
            accountRepository,
            instrumentRepository,
            exchangeRateRepository
        )

        whenever(balanceRepository.getAllSync()).thenReturn(listOf(balance))
        whenever(accountRepository.getAllSync()).thenReturn(listOf(account))
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(btc, ar))
        whenever(exchangeRateRepository.getAllCachedSync()).thenReturn(emptyList())
    }

    @Test
    fun `findItem returns the item matching the account id`() {
        val item = viewModel.findItem("acc1")
        assertEquals("acc1", item?.accountId)
        assertEquals("Crypto Wallet", item?.accountName)
    }

    @Test
    fun `findItem returns null when no item matches the account id`() {
        assertNull(viewModel.findItem("missing"))
    }
}
