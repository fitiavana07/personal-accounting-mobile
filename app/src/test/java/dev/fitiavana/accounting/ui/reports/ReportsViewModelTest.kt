package dev.fitiavana.accounting.ui.reports

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.BalanceRepository
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
        whenever(accountRepository.getAllSync()).thenReturn(
            listOf(Account(id = "acc1", name = "Cash", type = "asset"))
        )
        whenever(balanceRepository.computeBalancesAsOf(any())).thenReturn(mapOf("acc1" to 10_000L))
        whenever(balanceRepository.computeBalancesBetween(any(), any())).thenReturn(mapOf("acc1" to 10_000L))
        // Construction alone does no background work; tests call the *Sync methods directly
        // (start() is only invoked by the Fragment) to drive the ViewModel deterministically.
        viewModel = ReportsViewModel(accountRepository, balanceRepository)
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
        assertEquals(listOf(Calendar.JANUARY, Calendar.FEBRUARY), viewModel.availableMonths.value)
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
        assertEquals(listOf(Calendar.NOVEMBER, Calendar.DECEMBER), viewModel.availableMonths.value)
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
        verify(balanceRepository).computeBalancesBetween(expectedStart, expectedEnd)
    }

    @Test
    fun `selectReportType for Changes in Equity yields no rows`() {
        val min = millisFor(2026, Calendar.MARCH, 1)
        val max = millisFor(2026, Calendar.MARCH, 15)
        whenever(balanceRepository.getTransactionDateRange()).thenReturn(min to max)
        viewModel.loadInitialSync()

        viewModel.selectReportType(ReportType.CHANGES_IN_EQUITY)

        assertEquals(emptyList<ReportDisplayRow>(), viewModel.balanceSheetRows.value)
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
}
