package dev.fitiavana.accounting.ui.reports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dev.fitiavana.accounting.data.model.Account
import dev.fitiavana.accounting.data.repository.AccountRepository
import dev.fitiavana.accounting.data.repository.BalanceRepository
import dev.fitiavana.accounting.ui.home.BalanceSheetBuilder
import dev.fitiavana.accounting.ui.home.BalanceSheetRow

class ReportsViewModel(
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository
) : ViewModel() {

    private var monthsByYear: Map<Int, List<Int>> = emptyMap()
    private var started = false
    private var lastAccounts: List<Account> = emptyList()
    private var lastBalances: Map<String, Long> = emptyMap()
    private var lastPeriodBalances: Map<String, Long> = emptyMap()
    private var lastAsOfMs = 0L

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

    private val _balanceSheetRows = MutableLiveData<List<BalanceSheetRow>>(emptyList())
    val balanceSheetRows: LiveData<List<BalanceSheetRow>> = _balanceSheetRows

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

        val months = ReportPeriodSelector.monthsBetween(range.first, range.second)
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
        val asOfMs = ReportPeriodSelector.endOfMonthMillis(year, month)
        lastAsOfMs = asOfMs
        lastAccounts = accountRepository.getAllSync()
        lastBalances = balanceRepository.computeBalancesAsOf(asOfMs)
        lastPeriodBalances = balanceRepository.computeBalancesBetween(startMs, asOfMs)
        renderDisplay(_selectedReportType.value ?: ReportType.BALANCE_SHEET)
    }

    private fun renderDisplay(type: ReportType) {
        _asOfDateText.postValue(
            when (type) {
                ReportType.BALANCE_SHEET -> ReportPeriodSelector.formatAsOfDate(lastAsOfMs)
                ReportType.INCOME_STATEMENT -> ReportPeriodSelector.formatMonthEnded(lastAsOfMs)
                ReportType.CHANGES_IN_EQUITY -> ""
            }
        )
        _balanceSheetRows.postValue(
            when (type) {
                ReportType.BALANCE_SHEET -> BalanceSheetBuilder.buildMonthly(lastAccounts, lastBalances)
                ReportType.INCOME_STATEMENT -> BalanceSheetBuilder.buildIncomeStatement(lastAccounts, lastPeriodBalances)
                ReportType.CHANGES_IN_EQUITY -> emptyList()
            }
        )
    }
}
