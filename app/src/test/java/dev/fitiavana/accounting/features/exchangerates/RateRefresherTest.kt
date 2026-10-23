package dev.fitiavana.accounting.features.exchangerates

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RateRefresherTest {

    private val btc = Instrument(code = "BTC", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "bitcoin")
    private val usdt = Instrument(code = "USDT", note = "", type = "currency", decimalPlaces = 2)

    private val balanceRepository = mock<BalanceRepository>()
    private val accountRepository = mock<AccountRepository>()
    private val instrumentRepository = mock<InstrumentRepository>()
    private val exchangeRateRepository = mock<ExchangeRateRepository>()
    private val refresher = RateRefresher(
        balanceRepository, accountRepository, instrumentRepository, exchangeRateRepository
    )

    private val eth = Instrument(code = "ETH", note = "", type = "cryptocurrency", decimalPlaces = 8, coingeckoId = "ethereum")

    private fun account(id: String, instrumentCode: String = "BTC") = Account(
        id = id, name = id, type = "asset",
        instrumentCode = instrumentCode, intermediaryInstrumentCode = "USDT"
    )

    private fun balance(accountId: String) = AccountBalance(
        accountId = accountId, balance = 300_000,
        instrumentBalance = 100_000_000, intermediaryBalance = 6_000_000,
        updatedAt = 0L, createdAt = 0L
    )

    @Test
    fun `refreshes each distinct instrument pair of the gain loss accounts once`() {
        whenever(balanceRepository.getAllSync()).thenReturn(listOf(balance("a"), balance("b")))
        whenever(accountRepository.getAllSync()).thenReturn(listOf(account("a"), account("b")))
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(btc, usdt))
        val expected = RefreshResult(listOf("BTC"), emptyList(), null)
        whenever(exchangeRateRepository.refresh(any())).thenReturn(expected)

        val result = refresher.refresh()

        assertEquals(expected, result)
        val captor = argumentCaptor<List<Pair<Instrument, Instrument>>>()
        verify(exchangeRateRepository).refresh(captor.capture())
        assertEquals(listOf(btc to usdt), captor.firstValue)
    }

    @Test
    fun `refreshing one account only fetches that account's pair`() {
        whenever(balanceRepository.getAllSync()).thenReturn(listOf(balance("a"), balance("b")))
        whenever(accountRepository.getAllSync()).thenReturn(listOf(account("a"), account("b", "ETH")))
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(btc, eth, usdt))
        whenever(exchangeRateRepository.refresh(any())).thenReturn(RefreshResult(emptyList(), emptyList(), null))

        refresher.refresh(accountId = "b")

        val captor = argumentCaptor<List<Pair<Instrument, Instrument>>>()
        verify(exchangeRateRepository).refresh(captor.capture())
        assertEquals(listOf(eth to usdt), captor.firstValue)
    }
}
