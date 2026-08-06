package dev.fitiavana.accounting.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.model.AccountBalance
import dev.fitiavana.accounting.data.model.ExchangeRateCache
import dev.fitiavana.accounting.data.model.Instrument
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.data.repository.ExchangeRateRepository
import dev.fitiavana.accounting.data.repository.InstrumentRepository
import dev.fitiavana.accounting.data.repository.RefreshResult

class HomeViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    private val balances: LiveData<List<AccountBalance>> = balanceRepository.getAll()
    private val accounts: LiveData<List<Account>> = accountRepository.getAll()
    private val instruments: LiveData<List<Instrument>> = instrumentRepository.getAll()
    private val rates: LiveData<List<ExchangeRateCache>> = exchangeRateRepository.getAllCached()

    val homeItems = MediatorLiveData<List<HomeItem>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()
        var latestInstruments: Map<String, Instrument> = emptyMap()
        var latestRates: Map<String, ExchangeRateCache> = emptyMap()

        fun update() {
            value = HomeItemBuilder.build(latestBalances, latestAccounts, latestInstruments, latestRates)
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
        addSource(instruments) { i ->
            latestInstruments = (i ?: emptyList()).associateBy { it.code }
            update()
        }
        addSource(rates) { r ->
            latestRates = (r ?: emptyList()).associateBy { it.pairKey }
            update()
        }
    }

    val balanceSheetRows = MediatorLiveData<List<BalanceSheetRow>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        fun update() {
            value = BalanceSheetBuilder.build(latestAccounts, latestBalances)
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
    }

    val assetSlices = MediatorLiveData<List<AssetSlice>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        fun update() {
            value = BalanceSheetBuilder.assetSlices(latestAccounts, latestBalances)
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
    }

    /** Synchronous — callers must invoke this off the main thread. */
    fun refreshRates(): RefreshResult {
        val items = HomeItemBuilder.build(
            balanceRepository.getAllSync(),
            accountRepository.getAllSync(),
            instrumentRepository.getAllSync().associateBy { it.code },
            emptyMap()
        )
        val pairs = items
            .map { it.instrument to it.intermediaryInstrument }
            .distinctBy { it.first.code to it.second.code }
        return exchangeRateRepository.refresh(pairs)
    }
}
