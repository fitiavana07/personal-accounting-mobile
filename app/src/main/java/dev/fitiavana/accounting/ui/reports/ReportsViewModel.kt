package dev.fitiavana.accounting.ui.reports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.reports.BalanceSheetBuilder
import dev.fitiavana.accounting.features.reports.EquityStatementBuilder
import dev.fitiavana.accounting.features.reports.IncomeStatementBuilder
import dev.fitiavana.accounting.ui.common.EquityStatementDisplay
import dev.fitiavana.accounting.ui.common.EquityStatementPresenter
import dev.fitiavana.accounting.ui.common.ReportDisplayRow
import dev.fitiavana.accounting.ui.common.ReportPresenter

class ReportsViewModel(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository
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

    /** End boundary of the selected period: "now" if the month is still in progress, otherwise its last millisecond. */
    private var cachedPeriodCutoffMillis = 0L

    /** Start boundary of the selected period: the first millisecond of the selected month. */
    private var cachedPeriodStartMillis = 0L

    /** Last millisecond of the month preceding the selected period, used by the Changes in Equity report. */
    private var cachedPreviousMonthEndMillis = 0L

    /** Cumulative per-account balances as of [cachedPreviousMonthEndMillis], used by the Changes in Equity report. */
    private var cachedPreviousMonthEndBalances: Map<String, Long> = emptyMap()

    /** Year of the currently selected report period, kept alongside the balances it produced. */
    private var cachedReportYear = 0

    /** Month (0-11) of the currently selected report period, kept alongside the balances it produced. */
    private var cachedReportMonth = 0

    val reportTypes: List<ReportType> = ReportType.values().toList()

    private val _hasTransactions = MutableLiveData<Boolean>()
    val hasTransactions: LiveData<Boolean> = _hasTransactions

    private val _availableYears = MutableLiveData<List<Int>>(emptyList())
    val availableYears: LiveData<List<Int>> = _availableYears

    private val _availableMonths = MutableLiveData<List<Int>>(emptyList())
    val availableMonths: LiveData<List<Int>> = _availableMonths

    private val _selectedYear = MutableLiveData<Int>()
    val selectedYear: LiveData<Int> = _selectedYear

    private val _selectedMonth = MutableLiveData<Int>()
    val selectedMonth: LiveData<Int> = _selectedMonth

    private val _selectedReportType = MutableLiveData(ReportType.BALANCE_SHEET)
    val selectedReportType: LiveData<ReportType> = _selectedReportType

    private val _asOfDateText = MutableLiveData<String>()
    val asOfDateText: LiveData<String> = _asOfDateText

    private val _balanceSheetRows =
        MutableLiveData<List<ReportDisplayRow>>(emptyList())
    val balanceSheetRows: LiveData<List<ReportDisplayRow>> = _balanceSheetRows

    private val _equityStatement =
        MutableLiveData(EquityStatementDisplay(emptyList(), emptyList()))
    val equityStatement: LiveData<EquityStatementDisplay> = _equityStatement

    /** Kicks off the initial background load. Safe to call from every onViewCreated — a no-op after the first call. */
    fun start() {
        if (started) return
        started = true
        Thread { loadInitialSync() }.start()
    }

    fun selectYear(year: Int) {
        Thread { selectYearSync(year) }.start()
    }

    fun selectMonth(month: Int) {
        Thread { selectMonthSync(month) }.start()
    }

    /** Switches the displayed report; re-renders from the already-loaded balances, no DB access needed. */
    fun selectReportType(type: ReportType) {
        _selectedReportType.value = type
        renderDisplay(type)
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
        _availableMonths.postValue(monthsByYear.getValue(lastYear))
        _selectedYear.postValue(lastYear)
        _selectedMonth.postValue(lastMonth)
        recomputeSync(lastYear, lastMonth)
    }

    /** Synchronous version of [selectYear], for use on a background thread (or directly in tests). */
    internal fun selectYearSync(year: Int) {
        val months = monthsByYear[year] ?: return
        val month = months.max()
        _selectedYear.postValue(year)
        _availableMonths.postValue(months)
        _selectedMonth.postValue(month)
        recomputeSync(year, month)
    }

    /** Synchronous version of [selectMonth], for use on a background thread (or directly in tests). */
    internal fun selectMonthSync(month: Int) {
        val year = _selectedYear.value ?: return
        _selectedMonth.postValue(month)
        recomputeSync(year, month)
    }

    private fun recomputeSync(year: Int, month: Int) {
        val startMs = ReportPeriodSelector.startOfMonthMillis(year, month)
        val asOfMs = ReportPeriodSelector.asOfMillis(year, month)
        val previousMonthEndMs = ReportPeriodSelector.previousMonthEndMillis(year, month)
        cachedPeriodCutoffMillis = asOfMs
        cachedPeriodStartMillis = startMs
        cachedPreviousMonthEndMillis = previousMonthEndMs
        cachedReportYear = year
        cachedReportMonth = month
        cachedAccounts = accountRepository.getAllSync()
        cachedBalancesAsOf = balanceRepository.computeBalancesAsOf(asOfMs)
        cachedPeriodBalances =
            balanceRepository.computeBalancesBetween(startMs, asOfMs)
        cachedPreviousMonthEndBalances =
            balanceRepository.computeBalancesAsOf(previousMonthEndMs)
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
                        ReportPeriodSelector.endOfMonthMillis(
                            cachedReportYear,
                            cachedReportMonth
                        )
                    )
            }
        )
        _balanceSheetRows.postValue(
            when (type) {
                ReportType.BALANCE_SHEET ->
                    ReportPresenter.present(
                        BalanceSheetBuilder.buildMonthly(
                            cachedAccounts,
                            cachedBalancesAsOf
                        )
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
        )
        _equityStatement.postValue(
            when (type) {
                ReportType.CHANGES_IN_EQUITY ->
                    EquityStatementPresenter.present(
                        EquityStatementBuilder.build(
                            accounts = cachedAccounts,
                            previousMonthEndBalances = cachedPreviousMonthEndBalances,
                            periodChangeBalances = cachedPeriodBalances,
                            previousBalanceLabel =
                                "Balance at ${ReportPeriodSelector.formatDate(cachedPreviousMonthEndMillis)}",
                            currentBalanceLabel =
                                "Balance at ${ReportPeriodSelector.formatDate(cachedPeriodCutoffMillis)}"
                        )
                    )

                ReportType.BALANCE_SHEET, ReportType.INCOME_STATEMENT ->
                    EquityStatementDisplay(emptyList(), emptyList())
            }
        )
    }
}
