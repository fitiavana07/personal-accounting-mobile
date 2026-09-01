package dev.fitiavana.accounting.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateCache
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.exchangerates.RefreshResult
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.settings.AppSettingsRepository
import dev.fitiavana.accounting.ui.common.ReportDisplayRow
import dev.fitiavana.accounting.ui.common.ReportPresenter

class HomeViewModel(
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val settingsRepository: AppSettingsRepository
) : ViewModel() {

    private val balances: LiveData<List<AccountBalance>> =
        balanceRepository.getAll()
    private val accounts: LiveData<List<Account>> = accountRepository.getAll()
    private val instruments: LiveData<List<Instrument>> =
        instrumentRepository.getAll()
    private val rates: LiveData<List<ExchangeRateCache>> =
        exchangeRateRepository.getAllCached()

    val homeItems = MediatorLiveData<List<HomeItem>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()
        var latestInstruments: Map<String, Instrument> = emptyMap()
        var latestRates: Map<String, ExchangeRateCache> = emptyMap()

        fun update() {
            value = HomeItemBuilder.build(
                latestBalances,
                latestAccounts,
                latestInstruments,
                latestRates
            )
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

    val balanceSheetRows = MediatorLiveData<List<ReportDisplayRow>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        fun update() {
            value = ReportPresenter.present(
                BalanceSheetBuilder.build(
                    latestAccounts,
                    latestBalances
                )
            )
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
            value =
                AssetSliceBuilder.assetSlices(latestAccounts, latestBalances)
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

    val liquiditySlices = MediatorLiveData<List<AssetSlice>>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()

        fun update() {
            value =
                LiquiditySliceBuilder.liquiditySlices(latestAccounts, latestBalances)
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

    val emergencyFund = MediatorLiveData<EmergencyFundInfo>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()
        var latestMonthlyExpenses = 0L

        fun update() {
            value = EmergencyFundBuilder.build(
                LiquidAssetsBuilder.totalLiquidAssets(latestAccounts, latestBalances),
                latestMonthlyExpenses
            )
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
        addSource(settingsRepository.observe()) { settings ->
            latestMonthlyExpenses = settings?.monthlyLivingExpenses ?: 0L
            update()
        }
    }

    val metrics = MediatorLiveData<HomeMetrics>().apply {
        var latestBalances: List<AccountBalance> = emptyList()
        var latestAccounts: List<Account> = emptyList()
        var latestEmergencyFundPercent = 100
        var latestMonthlyExpenses = 0L

        fun update() {
            value = HomeMetricsBuilder.build(
                latestAccounts,
                latestBalances,
                latestEmergencyFundPercent,
                latestMonthlyExpenses
            )
        }

        addSource(balances) { b ->
            latestBalances = b ?: emptyList()
            update()
        }
        addSource(accounts) { a ->
            latestAccounts = a ?: emptyList()
            update()
        }
        addSource(emergencyFund) { info ->
            latestEmergencyFundPercent = info?.sixMonthPercent ?: 100
            latestMonthlyExpenses = info?.monthlyExpenses ?: 0L
            update()
        }
    }

    /** Synchronous — callers must invoke this off the main thread. */
    fun setMonthlyLivingExpenses(amount: Long) =
        settingsRepository.setMonthlyLivingExpenses(amount)

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
