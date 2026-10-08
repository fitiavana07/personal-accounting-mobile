package dev.fitiavana.accounting.ui.reports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.accounts.AccountTypes
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.EquityStatementBuilder
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.features.reports.IncomeStatementBuilder
import dev.fitiavana.accounting.features.reports.NativeAmount
import dev.fitiavana.accounting.ui.common.EquityStatementDisplay
import dev.fitiavana.accounting.ui.common.EquityStatementPresenter
import dev.fitiavana.accounting.ui.common.ReportDisplayRow
import dev.fitiavana.accounting.ui.common.ReportPresenter

class ReportsViewModel(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val instrumentRepository: InstrumentRepository
) : ViewModel() {

    /** All months that have transactions, grouped by year, for populating the year/month pickers. */
    private var monthsByYear: Map<Int, List<Int>> = emptyMap()

    /** Whether [start] has already kicked off the initial load, so repeat calls are no-ops. */
    private var started = false

    /** Accounts as of the last recompute, reused by [renderDisplay] when only the report type changes. */
    private var cachedAccounts: List<Account> = emptyList()

    /** Cumulative per-account balances as of [cachedPeriodCutoffMillis], used for the balance sheet. */
    private var cachedBalancesAsOf: Map<String, Long> = emptyMap()

    /** Per-account balances accrued between [cachedPeriodStartMillis] and [cachedPeriodCutoffMillis], used for the income statement. */
    private var cachedPeriodBalances: Map<String, Long> = emptyMap()

    /** Per-account balances in each account's instrument, as of [cachedPeriodCutoffMillis]. */
    private var cachedInstrumentBalancesAsOf: Map<String, Long> = emptyMap()

    /** Per-account balances in each account's intermediary instrument, as of [cachedPeriodCutoffMillis]. */
    private var cachedIntermediaryBalancesAsOf: Map<String, Long> = emptyMap()

    /** Instruments by code, for formatting native amounts with the right decimal places. */
    private var cachedInstruments: Map<String, Instrument> = emptyMap()

    /** Accounts whose native sub-rows are shown; kept across period and report switches. */
    private var expandedAccountIds: Set<String> = emptySet()

    /** Accounts with a tappable line in the currently rendered Balance Sheet (excludes the lumped "Other" accounts). */
    private var cachedExpandableAccountIds: Set<String> = emptySet()

    /** End boundary of the selected period: "now" if the month is still in progress, otherwise its last millisecond. */
    private var cachedPeriodCutoffMillis = 0L

    /** Start boundary of the selected period: the first millisecond of the selected month. */
    private var cachedPeriodStartMillis = 0L

    /** End of the period preceding the selected one (prior month, or prior year in Year mode), used by the Changes in Equity report. */
    private var cachedPreviousPeriodEndMillis = 0L

    /** Cumulative per-account balances as of [cachedPreviousPeriodEndMillis], used by the Changes in Equity report. */
    private var cachedPreviousPeriodEndBalances: Map<String, Long> = emptyMap()

    /** Year of the currently selected report period, kept alongside the balances it produced. */
    private var cachedReportYear = 0

    /** Month (0-11) of the currently selected report period, or null when the whole year is selected. */
    private var cachedReportMonth: Int? = 0

    val reportTypes: List<ReportType> = ReportType.values().toList()


    private val _hasTransactions = MutableLiveData<Boolean>()
    val hasTransactions: LiveData<Boolean> = _hasTransactions

    private val _availableYears = MutableLiveData<List<Int>>(emptyList())
    val availableYears: LiveData<List<Int>> = _availableYears

    private val _availableMonths = MutableLiveData<List<Int?>>(emptyList())
    val availableMonths: LiveData<List<Int?>> = _availableMonths

    private val _selectedYear = MutableLiveData<Int>()
    val selectedYear: LiveData<Int> = _selectedYear

    /** The selected month (0-11), or null when the "Year" tab is selected. */
    private val _selectedMonth = MutableLiveData<Int?>()
    val selectedMonth: LiveData<Int?> = _selectedMonth

    private val _selectedReportType = MutableLiveData(ReportType.BALANCE_SHEET)
    val selectedReportType: LiveData<ReportType> = _selectedReportType

    /** Visibility/label state of the "Expand all / Collapse all" control. */
    private val _expandToggle = MutableLiveData(ExpandToggleState(visible = false, allExpanded = false))
    val expandToggle: LiveData<ExpandToggleState> = _expandToggle

    private val _asOfDateText = MutableLiveData<String>()
    val asOfDateText: LiveData<String> = _asOfDateText

    private val _balanceSheetRows =
        MutableLiveData<List<ReportDisplayRow>>(emptyList())
    val balanceSheetRows: LiveData<List<ReportDisplayRow>> = _balanceSheetRows

    private val _equityStatement =
        MutableLiveData(EquityStatementDisplay(emptyList(), emptyList()))
    val equityStatement: LiveData<EquityStatementDisplay> = _equityStatement

    /**
     * Net worth and net income of the most recent months, for the trend charts above the period selector.
     * `null` until the trend has been loaded, so the UI can reserve the charts' space meanwhile.
     */
    private val _trendPoints = MutableLiveData<List<TrendPoint>?>(null)
    val trendPoints: LiveData<List<TrendPoint>?> = _trendPoints

    /** Kicks off the initial background load. Safe to call from every onViewCreated — a no-op after the first call. */
    fun start() {
        if (started) return
        started = true
        Thread { loadInitialSync() }.start()
    }

    fun selectYear(year: Int) {
        Thread { selectYearSync(year) }.start()
    }

    fun selectMonth(month: Int?) {
        Thread { selectMonthSync(month) }.start()
    }

    /** Switches the displayed report; re-renders from the already-loaded balances, no DB access needed. */
    fun selectReportType(type: ReportType) {
        _selectedReportType.value = type
        renderDisplay(type)
    }

    /** Expands or collapses one Balance Sheet account's native sub-rows; ignored if it isn't expandable. */
    fun toggleAccount(accountId: String) {
        if (accountId !in cachedExpandableAccountIds) return
        expandedAccountIds =
            if (accountId in expandedAccountIds) expandedAccountIds - accountId
            else expandedAccountIds + accountId
        renderDisplay(_selectedReportType.value ?: ReportType.BALANCE_SHEET)
    }

    /** Expands every expandable account, or collapses them all when they already are all expanded. */
    fun toggleExpandAll() {
        expandedAccountIds =
            if (cachedExpandableAccountIds.all { it in expandedAccountIds }) expandedAccountIds - cachedExpandableAccountIds
            else expandedAccountIds + cachedExpandableAccountIds
        renderDisplay(_selectedReportType.value ?: ReportType.BALANCE_SHEET)
    }

    /** Synchronous version of the initial load, for use on a background thread (or directly in tests). */
    internal fun loadInitialSync() {
        val range = balanceRepository.getTransactionDateRange()
        if (range == null) {
            _hasTransactions.postValue(false)
            return
        }

        val months =
            ReportPeriodSelector.monthsBetween(range.first, range.second)
        monthsByYear = months.groupBy({ it.year }, { it.month })
        val years = monthsByYear.keys.sorted()
        val lastYear = years.last()
        val lastMonth = monthsByYear.getValue(lastYear).max()

        _hasTransactions.postValue(true)
        _availableYears.postValue(years)
        _availableMonths.postValue(monthsByYear.getValue(lastYear) + null)
        _selectedYear.postValue(lastYear)
        _selectedMonth.postValue(lastMonth)
        recomputeSync(lastYear, lastMonth)
        loadTrendSync(months.takeLast(TrendSeriesBuilder.MAX_MONTHS))
    }

    /** Net worth and net income for [months]; independent of the selected report period. */
    private fun loadTrendSync(months: List<YearMonth>) {
        _trendPoints.postValue(
            TrendSeriesBuilder.build(
                months = months,
                accounts = cachedAccounts,
                balancesAsOf = {
                    balanceRepository.computeBalancesAsOf(
                        ReportPeriodSelector.asOfMillis(it.year, it.month)
                    )
                },
                balancesBetween = {
                    balanceRepository.computeBalancesBetween(
                        ReportPeriodSelector.startOfMonthMillis(it.year, it.month),
                        ReportPeriodSelector.asOfMillis(it.year, it.month)
                    )
                }
            )
        )
    }

    /** Synchronous version of [selectYear], for use on a background thread (or directly in tests). */
    internal fun selectYearSync(year: Int) {
        val months = monthsByYear[year] ?: return
        val month = months.max()
        _selectedYear.postValue(year)
        _availableMonths.postValue(months + null)
        _selectedMonth.postValue(month)
        recomputeSync(year, month)
    }

    /** Synchronous version of [selectMonth], for use on a background thread (or directly in tests). */
    internal fun selectMonthSync(month: Int?) {
        val year = _selectedYear.value ?: return
        _selectedMonth.postValue(month)
        recomputeSync(year, month)
    }

    private fun recomputeSync(year: Int, month: Int?) {
        val startMs: Long
        val asOfMs: Long
        val previousPeriodEndMs: Long
        if (month == null) {
            startMs = ReportPeriodSelector.startOfYearMillis(year)
            asOfMs = ReportPeriodSelector.asOfYearMillis(year)
            previousPeriodEndMs = ReportPeriodSelector.previousYearEndMillis(year)
        } else {
            startMs = ReportPeriodSelector.startOfMonthMillis(year, month)
            asOfMs = ReportPeriodSelector.asOfMillis(year, month)
            previousPeriodEndMs = ReportPeriodSelector.previousMonthEndMillis(year, month)
        }
        cachedPeriodCutoffMillis = asOfMs
        cachedPeriodStartMillis = startMs
        cachedPreviousPeriodEndMillis = previousPeriodEndMs
        cachedReportYear = year
        cachedReportMonth = month
        cachedAccounts = accountRepository.getAllSync()
        cachedBalancesAsOf = balanceRepository.computeBalancesAsOf(asOfMs)
        cachedPeriodBalances =
            balanceRepository.computeBalancesBetween(startMs, asOfMs)
        cachedPreviousPeriodEndBalances =
            balanceRepository.computeBalancesAsOf(previousPeriodEndMs)
        cachedInstrumentBalancesAsOf = balanceRepository.computeInstrumentBalancesAsOf(asOfMs)
        cachedIntermediaryBalancesAsOf = balanceRepository.computeIntermediaryBalancesAsOf(asOfMs)
        cachedInstruments = instrumentRepository.getAllSync().associateBy { it.code }
        renderDisplay(_selectedReportType.value ?: ReportType.BALANCE_SHEET)
    }

    private fun renderDisplay(type: ReportType) {
        _asOfDateText.postValue(
            when (type) {
                ReportType.BALANCE_SHEET -> ReportPeriodSelector.formatAsOfDate(
                    cachedPeriodCutoffMillis
                )

                ReportType.INCOME_STATEMENT, ReportType.CHANGES_IN_EQUITY ->
                    ReportPeriodSelector.formatIncomeStatementPeriod(
                        cachedPeriodStartMillis,
                        cachedPeriodCutoffMillis,
                        cachedReportMonth?.let {
                            ReportPeriodSelector.endOfMonthMillis(cachedReportYear, it)
                        } ?: ReportPeriodSelector.endOfYearMillis(cachedReportYear)
                    )
            }
        )
        val balanceSheetRows = when (type) {
            ReportType.BALANCE_SHEET ->
                ReportPresenter.present(
                    BalanceSheetBuilder.buildMonthly(
                        cachedAccounts,
                        cachedBalancesAsOf,
                        nativeAmountsByAccountId()
                    ),
                    cachedInstruments,
                    expandedAccountIds
                )

            ReportType.INCOME_STATEMENT ->
                ReportPresenter.present(
                    IncomeStatementBuilder.build(
                        cachedAccounts,
                        cachedPeriodBalances
                    )
                )

            ReportType.CHANGES_IN_EQUITY -> emptyList()
        }
        if (type == ReportType.BALANCE_SHEET) {
            cachedExpandableAccountIds = balanceSheetRows
                .filterIsInstance<ReportDisplayRow.AccountLine>()
                .filter { it.expandable }
                .mapNotNull { it.accountId }
                .toSet()
        }
        _expandToggle.postValue(
            ExpandToggleState(
                visible = type == ReportType.BALANCE_SHEET && cachedExpandableAccountIds.isNotEmpty(),
                allExpanded = cachedExpandableAccountIds.isNotEmpty() &&
                    cachedExpandableAccountIds.all { it in expandedAccountIds }
            )
        )
        _balanceSheetRows.postValue(balanceSheetRows)
        _equityStatement.postValue(
            when (type) {
                ReportType.CHANGES_IN_EQUITY ->
                    EquityStatementPresenter.present(
                        EquityStatementBuilder.build(
                            accounts = cachedAccounts,
                            previousMonthEndBalances = cachedPreviousPeriodEndBalances,
                            periodChangeBalances = cachedPeriodBalances,
                            previousBalanceLabel =
                                "Balance at ${ReportPeriodSelector.formatDate(cachedPreviousPeriodEndMillis)}",
                            currentBalanceLabel =
                                "Balance at ${ReportPeriodSelector.formatDate(cachedPeriodCutoffMillis)}"
                        )
                    )

                ReportType.BALANCE_SHEET, ReportType.INCOME_STATEMENT ->
                    EquityStatementDisplay(emptyList(), emptyList())
            }
        )
    }

    /**
     * Native balances per account, instrument first then intermediary: only for account types that can
     * hold an instrument, and only for the instruments the account actually has set.
     */
    private fun nativeAmountsByAccountId(): Map<String, List<NativeAmount>> =
        cachedAccounts
            .filter { AccountTypes.supportsInstrument(it.type) && it.instrumentCode != null }
            .associate { account ->
                account.id to listOfNotNull(
                    account.instrumentCode?.let {
                        NativeAmount(cachedInstrumentBalancesAsOf[account.id] ?: 0L, it)
                    },
                    account.intermediaryInstrumentCode?.let {
                        NativeAmount(cachedIntermediaryBalancesAsOf[account.id] ?: 0L, it)
                    }
                )
            }
}

/** State of the Balance Sheet's "Expand all / Collapse all" control. */
data class ExpandToggleState(val visible: Boolean, val allExpanded: Boolean)
