package dev.fitiavana.accounting.ui.reports

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository
import dev.fitiavana.accounting.ui.common.ReportDisplayRow
import dev.fitiavana.accounting.ui.reports.ReportPeriodSelector
import dev.fitiavana.accounting.ui.reports.ReportType
import dev.fitiavana.accounting.ui.reports.ReportsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar

class ReportsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var accountRepository: AccountRepository
    private lateinit var balanceRepository: BalanceRepository
    private lateinit var instrumentRepository: InstrumentRepository
    private lateinit var viewModel: ReportsViewModel

    private fun millisFor(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Before
    fun setUp() {
        accountRepository = mock()
        balanceRepository = mock()
        instrumentRepository = mock()
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "acc1", name = "Cash", type = "asset"))
        )
        whenever(balanceRepository.computeBalancesAsOf(any())).thenReturn(mapOf("acc1" to 10_000L))
        whenever(balanceRepository.computeBalancesBetween(any(), any())).thenReturn(mapOf("acc1" to 10_000L))
        // Construction alone does no background work; tests call the *Sync methods directly
        // (start() is only invoked by the Fragment) to drive the ViewModel deterministically.
        whenever(instrumentRepository.getAllSync()).thenReturn(emptyList())
        whenever(balanceRepository.computeInstrumentBalancesAsOf(any())).thenReturn(emptyMap())
        whenever(balanceRepository.computeIntermediaryBalancesAsOf(any())).thenReturn(emptyMap())
        viewModel = ReportsViewModel(accountRepository, balanceRepository, instrumentRepository)
    }

    @Test
    fun `loadInitialSync with no transactions marks hasTransactions false`() {
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(null)

        viewModel.loadInitialSync()

        assertEquals(false, viewModel.hasTransactions.value)
    }

    @Test
    fun `loadInitialSync defaults to the last available year and month`() {
        val min = millisFor(2025, Calendar.NOVEMBER, 15)
        val max = millisFor(2026, Calendar.FEBRUARY, 3)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)

        viewModel.loadInitialSync()

        assertEquals(true, viewModel.hasTransactions.value)
        assertEquals(listOf(2025, 2026), viewModel.availableYears.value)
        assertEquals(2026, viewModel.selectedYear.value)
        assertEquals(Calendar.FEBRUARY, viewModel.selectedMonth.value)
        assertEquals(listOf(Calendar.JANUARY, Calendar.FEBRUARY, null), viewModel.availableMonths.value)
    }

    @Test
    fun `loadInitialSync computes balance sheet rows for the default period`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)

        viewModel.loadInitialSync()

        assertEquals("At March 31, 2026", viewModel.asOfDateText.value)
        val rows = viewModel.balanceSheetRows.value ?: emptyList()
        assertTrue(rows.any { it is ReportDisplayRow.AccountLine && it.name == "Cash" })
    }

    @Test
    fun `selectYearSync clamps month selection to the last available month for that year`() {
        val min = millisFor(2025, Calendar.NOVEMBER, 15)
        val max = millisFor(2026, Calendar.FEBRUARY, 3)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectYearSync(2025)

        assertEquals(2025, viewModel.selectedYear.value)
        assertEquals(Calendar.DECEMBER, viewModel.selectedMonth.value)
        assertEquals(listOf(Calendar.NOVEMBER, Calendar.DECEMBER, null), viewModel.availableMonths.value)
    }

    @Test
    fun `selectMonthSync recomputes the balance sheet for the newly selected month`() {
        val min = millisFor(2026, Calendar.JANUARY, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(Calendar.JANUARY)

        assertEquals(Calendar.JANUARY, viewModel.selectedMonth.value)
        assertEquals("At January 31, 2026", viewModel.asOfDateText.value)
    }

    @Test
    fun `start triggers the initial load exactly once even when called repeatedly`() {
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(null)

        viewModel.start()
        viewModel.start()
        viewModel.start()

        verify(balanceRepository, timeout(1000)).getTransactionDateRange()
        verify(balanceRepository, times(1)).getTransactionDateRange()
    }

    @Test
    fun `defaults to the Balance Sheet report type`() {
        assertEquals(ReportType.BALANCE_SHEET, viewModel.selectedReportType.value)
    }

    @Test
    fun `selectReportType switches to income statement rows without touching the repositories again`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "acc1", name = "Salary", type = "revenue"))
        )
        whenever(balanceRepository.computeBalancesBetween(any(), any())).thenReturn(mapOf("acc1" to 10_000L))
        viewModel.loadInitialSync()

        viewModel.selectReportType(ReportType.INCOME_STATEMENT)

        assertEquals(ReportType.INCOME_STATEMENT, viewModel.selectedReportType.value)
        assertEquals("Month ended March 31, 2026", viewModel.asOfDateText.value)
        val rows = viewModel.balanceSheetRows.value ?: emptyList()
        assertTrue(rows.any { it is ReportDisplayRow.TotalLine && it.label == "Net Income" })
        verify(accountRepository, times(1)).getAllSync()
    }

    @Test
    fun `recomputeSync queries income statement balances scoped to the selected month only`() {
        val min = millisFor(2026, Calendar.JANUARY, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)

        viewModel.loadInitialSync()

        val expectedStart = ReportPeriodSelector.startOfMonthMillis(2026, Calendar.MARCH)
        val expectedEnd = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.MARCH)
        // The trend chart also queries this month's range, so it is called more than once.
        verify(balanceRepository, atLeastOnce()).computeBalancesBetween(expectedStart, expectedEnd)
    }

    @Test
    fun `selectReportType for Changes in Equity yields no balance sheet rows`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectReportType(ReportType.CHANGES_IN_EQUITY)

        assertEquals(emptyList<ReportDisplayRow>(), viewModel.balanceSheetRows.value)
    }

    @Test
    fun `selectReportType for Changes in Equity shows the Month ended period label`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectReportType(ReportType.CHANGES_IN_EQUITY)

        assertEquals("Month ended March 31, 2026", viewModel.asOfDateText.value)
    }

    @Test
    fun `selectReportType for Changes in Equity populates the equity statement`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "e", name = "Owner Capital", type = "equity"))
        )
        whenever(balanceRepository.computeBalancesAsOf(any())).thenReturn(mapOf("e" to 500L))
        whenever(balanceRepository.computeBalancesBetween(any(), any())).thenReturn(mapOf("e" to 50L))
        viewModel.loadInitialSync()

        viewModel.selectReportType(ReportType.CHANGES_IN_EQUITY)

        val statement = viewModel.equityStatement.value
        assertEquals(
            listOf("Owner Capital", "Unclosed IS Accounts", "Drawing", "Total"),
            statement?.columnTitles
        )
        assertEquals(
            listOf(
                "Balance at February 28, 2026",
                "Changes in Owner Capital",
                "Changes in Unclosed IS Accounts",
                "Changes in Drawing",
                "Balance at March 31, 2026"
            ),
            statement?.rows?.map { it.label }
        )
        val previousRow = statement?.rows?.first { it.label == "Balance at February 28, 2026" }
        assertEquals(listOf("500", "0", "0", "500"), previousRow?.cellTexts)
    }

    @Test
    fun `selectReportType for other report types yields an empty equity statement`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        assertEquals(emptyList<String>(), viewModel.equityStatement.value?.columnTitles)
        assertEquals(emptyList<Any>(), viewModel.equityStatement.value?.rows)
    }

    @Test
    fun `selectYearSync for an unknown year is a no-op`() {
        val min = millisFor(2026, Calendar.JANUARY, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()
        val yearBefore = viewModel.selectedYear.value

        viewModel.selectYearSync(1999)

        assertEquals(yearBefore, viewModel.selectedYear.value)
    }

    @Test
    fun `selectMonthSync with null selects Year mode and updates selectedMonth`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(null)

        assertEquals(null, viewModel.selectedMonth.value)
    }

    @Test
    fun `Year mode queries balances scoped to the full selected year`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(null)

        val expectedStart = ReportPeriodSelector.startOfYearMillis(2025)
        val expectedEnd = ReportPeriodSelector.endOfYearMillis(2025)
        verify(balanceRepository).computeBalancesBetween(expectedStart, expectedEnd)
        verify(balanceRepository).computeBalancesAsOf(expectedEnd)
    }

    @Test
    fun `Year mode shows the balance sheet as-of the last day of the year`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(null)

        assertEquals("At December 31, 2025", viewModel.asOfDateText.value)
    }

    @Test
    fun `Year mode shows the income statement period ended for the full year`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(null)
        viewModel.selectReportType(ReportType.INCOME_STATEMENT)

        assertEquals("Month ended December 31, 2025", viewModel.asOfDateText.value)
    }

    @Test
    fun `Year mode Changes in Equity uses the prior year end balance as the previous balance`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "e", name = "Owner Capital", type = "equity"))
        )
        whenever(balanceRepository.computeBalancesAsOf(any())).thenReturn(mapOf("e" to 500L))
        whenever(balanceRepository.computeBalancesBetween(any(), any())).thenReturn(mapOf("e" to 50L))
        viewModel.loadInitialSync()

        viewModel.selectMonthSync(null)
        viewModel.selectReportType(ReportType.CHANGES_IN_EQUITY)

        val expectedPreviousYearEnd = ReportPeriodSelector.previousYearEndMillis(2025)
        verify(balanceRepository).computeBalancesAsOf(expectedPreviousYearEnd)
        val statement = viewModel.equityStatement.value
        assertEquals(
            listOf(
                "Balance at December 31, 2024",
                "Changes in Owner Capital",
                "Changes in Unclosed IS Accounts",
                "Changes in Drawing",
                "Balance at December 31, 2025"
            ),
            statement?.rows?.map { it.label }
        )
    }

    @Test
    fun `switching from Year mode back to a month recomputes month-scoped balances`() {
        val min = millisFor(2025, Calendar.JANUARY, 1)
        val max = millisFor(2025, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()
        viewModel.selectMonthSync(null)

        viewModel.selectMonthSync(Calendar.JANUARY)

        assertEquals(Calendar.JANUARY, viewModel.selectedMonth.value)
        assertEquals("At January 31, 2025", viewModel.asOfDateText.value)
    }

    // --- Balance Sheet mode (Base / Instrument / Intermediary) ---

    private fun loadWithNativeAccounts() {
        val usdt = Instrument(code = "USDT", note = "", type = "crypto", decimalPlaces = 2)
        val usdc = Instrument(code = "USDC", note = "", type = "crypto", decimalPlaces = 2)
        whenever(instrumentRepository.getAllSync()).thenReturn(listOf(usdt, usdc))
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(
                Account(id = "acc1", name = "Cash", type = "asset"),
                Account(id = "acc2", name = "Bybit", type = "asset", instrumentCode = "USDT", intermediaryInstrumentCode = "USDC"),
                Account(id = "acc3", name = "Binance", type = "asset", instrumentCode = "USDT")
            )
        )
        whenever(balanceRepository.computeBalancesAsOf(any()))
            .thenReturn(mapOf("acc1" to 20_000L, "acc2" to 50_000L, "acc3" to 30_000L))
        whenever(balanceRepository.computeInstrumentBalancesAsOf(any()))
            .thenReturn(mapOf("acc1" to 0L, "acc2" to 1_100L, "acc3" to 700L))
        whenever(balanceRepository.computeIntermediaryBalancesAsOf(any()))
            .thenReturn(mapOf("acc1" to 0L, "acc2" to 1_090L, "acc3" to 0L))
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2026, Calendar.MARCH, 1) to millisFor(2026, Calendar.MARCH, 15))
        viewModel.loadInitialSync()
    }

    private fun rows(): List<ReportDisplayRow> = viewModel.balanceSheetRows.value.orEmpty()

    private fun nativeLines(): List<ReportDisplayRow.NativeLine> =
        rows().filterIsInstance<ReportDisplayRow.NativeLine>()

    private fun accountLine(name: String): ReportDisplayRow.AccountLine =
        rows().filterIsInstance<ReportDisplayRow.AccountLine>().first { it.name == name }

    @Test
    fun `balance sheet is collapsed by default and shows base amounts only`() {
        loadWithNativeAccounts()

        assertTrue(nativeLines().isEmpty())
        assertEquals("50,000 ", accountLine("Bybit").amountText)
        assertTrue(accountLine("Bybit").expandable)
        assertTrue(!accountLine("Bybit").expanded)
    }

    @Test
    fun `accounts without an instrument are not expandable`() {
        loadWithNativeAccounts()

        assertTrue(!accountLine("Cash").expandable)
    }

    @Test
    fun `toggleAccount expands to instrument and intermediary sub-rows then collapses`() {
        loadWithNativeAccounts()

        viewModel.toggleAccount("acc2")

        assertEquals(
            listOf(
                ReportDisplayRow.NativeLine("USDT", "11.0 "),
                ReportDisplayRow.NativeLine("USDC", "10.9 ")
            ),
            nativeLines()
        )
        assertTrue(accountLine("Bybit").expanded)

        viewModel.toggleAccount("acc2")

        assertTrue(nativeLines().isEmpty())
    }

    @Test
    fun `account with an instrument but no intermediary expands to a single sub-row`() {
        loadWithNativeAccounts()

        viewModel.toggleAccount("acc3")

        assertEquals(listOf(ReportDisplayRow.NativeLine("USDT", "7.0 ")), nativeLines())
    }

    @Test
    fun `toggling an account without an instrument changes nothing`() {
        loadWithNativeAccounts()

        viewModel.toggleAccount("acc1")

        assertTrue(nativeLines().isEmpty())
    }

    @Test
    fun `totals stay in base when accounts are expanded`() {
        loadWithNativeAccounts()

        viewModel.toggleExpandAll()

        val total = rows().filterIsInstance<ReportDisplayRow.TotalLine>().first { it.label == "Total Assets" }
        assertEquals("Ar 100,000 ", total.amountText)
    }

    @Test
    fun `expansion is kept when the period changes`() {
        loadWithNativeAccounts()
        viewModel.toggleAccount("acc2")

        viewModel.selectMonthSync(null)

        assertEquals(2, nativeLines().size)
    }

    @Test
    fun `toggleExpandAll expands every expandable account then collapses all`() {
        loadWithNativeAccounts()
        assertEquals(ExpandToggleState(visible = true, allExpanded = false), viewModel.expandToggle.value)

        viewModel.toggleExpandAll()

        assertEquals(3, nativeLines().size)
        assertEquals(ExpandToggleState(visible = true, allExpanded = true), viewModel.expandToggle.value)

        viewModel.toggleExpandAll()

        assertTrue(nativeLines().isEmpty())
        assertEquals(ExpandToggleState(visible = true, allExpanded = false), viewModel.expandToggle.value)
    }

    @Test
    fun `toggleExpandAll expands the rest when only some accounts are expanded`() {
        loadWithNativeAccounts()
        viewModel.toggleAccount("acc3")

        viewModel.toggleExpandAll()

        assertEquals(3, nativeLines().size)
    }

    @Test
    fun `expand toggle is hidden when no account is expandable or the report is not the balance sheet`() {
        loadWithNativeAccounts()
        viewModel.selectReportType(ReportType.INCOME_STATEMENT)

        assertEquals(false, viewModel.expandToggle.value?.visible)

        viewModel.selectReportType(ReportType.BALANCE_SHEET)

        assertEquals(true, viewModel.expandToggle.value?.visible)
    }

    @Test
    fun `expanding and collapsing does not query repositories again`() {
        loadWithNativeAccounts()

        viewModel.toggleAccount("acc2")
        viewModel.toggleExpandAll()
        viewModel.toggleExpandAll()

        verify(accountRepository, times(1)).getAllSync()
        verify(balanceRepository, times(1)).computeInstrumentBalancesAsOf(any())
        verify(balanceRepository, times(1)).computeIntermediaryBalancesAsOf(any())
    }

    @Test
    fun `native balances are computed as of the selected period cutoff`() {
        loadWithNativeAccounts()
        val cutoff = ReportPeriodSelector.asOfMillis(2026, Calendar.MARCH)

        verify(balanceRepository).computeInstrumentBalancesAsOf(cutoff)
        verify(balanceRepository).computeIntermediaryBalancesAsOf(cutoff)
    }

    @Test
    fun `an asset with an APR but no instrument can be expanded to show its rate`() {
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2025, Calendar.JANUARY, 1) to millisFor(2025, Calendar.JANUARY, 20))
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "earn", name = "Earn", type = "asset", aprPercent = 5.5))
        )
        whenever(balanceRepository.computeBalancesAsOf(any())).thenReturn(mapOf("earn" to 500_000L))
        viewModel.loadInitialSync()

        viewModel.toggleAccount("earn")

        assertTrue(viewModel.balanceSheetRows.value.orEmpty().contains(ReportDisplayRow.AprLine("5.5% ")))
        assertEquals(true, viewModel.expandToggle.value?.visible)
    }

    @Test
    fun `loadInitialSync builds one trend point per month in chronological order`() {
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2025, Calendar.JANUARY, 1) to millisFor(2025, Calendar.MARCH, 15))

        viewModel.loadInitialSync()

        assertEquals(
            listOf(YearMonth(2025, 0), YearMonth(2025, 1), YearMonth(2025, 2)),
            viewModel.trendPoints.value?.map { it.yearMonth }
        )
    }

    @Test
    fun `trend points use total equity as of the month and the month's net income`() {
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2025, Calendar.JANUARY, 1) to millisFor(2025, Calendar.JANUARY, 20))
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(
                Account(id = "cap", name = "Capital", type = "equity"),
                Account(id = "sal", name = "Salary", type = "revenue")
            )
        )
        val asOf = ReportPeriodSelector.asOfMillis(2025, Calendar.JANUARY)
        val start = ReportPeriodSelector.startOfMonthMillis(2025, Calendar.JANUARY)
        whenever(balanceRepository.computeBalancesAsOf(asOf))
            .thenReturn(mapOf("cap" to 1_000L, "sal" to 250L))
        whenever(balanceRepository.computeBalancesBetween(start, asOf))
            .thenReturn(mapOf("sal" to 250L))

        viewModel.loadInitialSync()

        val point = viewModel.trendPoints.value?.single()
        assertEquals(1_250L, point?.netWorth)
        assertEquals(250L, point?.netIncome)
    }

    @Test
    fun `trend points are limited to the most recent months`() {
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2023, Calendar.JANUARY, 1) to millisFor(2025, Calendar.MARCH, 15))

        viewModel.loadInitialSync()

        val points = viewModel.trendPoints.value.orEmpty()
        assertEquals(TrendSeriesBuilder.MAX_MONTHS, points.size)
        assertEquals(YearMonth(2025, 2), points.last().yearMonth)
        assertEquals(YearMonth(2024, 3), points.first().yearMonth)
    }

    @Test
    fun `trend points stay the same when another period is selected`() {
        whenever(balanceRepository.getTransactionDateRange())
            .thenReturn(millisFor(2025, Calendar.JANUARY, 1) to millisFor(2025, Calendar.MARCH, 15))
        viewModel.loadInitialSync()
        val before = viewModel.trendPoints.value

        viewModel.selectMonthSync(Calendar.JANUARY)

        assertEquals(before, viewModel.trendPoints.value)
    }
}
