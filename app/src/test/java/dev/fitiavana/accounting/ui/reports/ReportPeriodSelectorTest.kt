package dev.fitiavana.accounting.ui.reports

import dev.fitiavana.accounting.ui.reports.ReportPeriodSelector
import dev.fitiavana.accounting.ui.reports.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReportPeriodSelectorTest {

    private fun millisFor(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // --- monthsBetween ---

    @Test
    fun `monthsBetween within the same month yields a single entry`() {
        val min = millisFor(2026, Calendar.MARCH, 5)
        val max = millisFor(2026, Calendar.MARCH, 20)

        val result = ReportPeriodSelector.monthsBetween(min, max)

        assertEquals(listOf(YearMonth(2026, Calendar.MARCH)), result)
    }

    @Test
    fun `monthsBetween spans an inclusive range across a year boundary`() {
        val min = millisFor(2025, Calendar.NOVEMBER, 15)
        val max = millisFor(2026, Calendar.FEBRUARY, 3)

        val result = ReportPeriodSelector.monthsBetween(min, max)

        assertEquals(
            listOf(
                YearMonth(2025, Calendar.NOVEMBER),
                YearMonth(2025, Calendar.DECEMBER),
                YearMonth(2026, Calendar.JANUARY),
                YearMonth(2026, Calendar.FEBRUARY)
            ),
            result
        )
    }

    // --- endOfMonthMillis ---

    @Test
    fun `endOfMonthMillis returns the last millisecond of a 31-day month`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.JANUARY)
        }
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `endOfMonthMillis returns the last millisecond of a 30-day month`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.APRIL)
        }
        assertEquals(30, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
    }

    @Test
    fun `endOfMonthMillis handles a leap February`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ReportPeriodSelector.endOfMonthMillis(2024, Calendar.FEBRUARY)
        }
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `endOfMonthMillis handles a non-leap February`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.FEBRUARY)
        }
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
    }

    // --- asOfMillis ---

    @Test
    fun `asOfMillis returns now for the current month when it hasn't ended yet`() {
        val now = Calendar.getInstance()

        val result = ReportPeriodSelector.asOfMillis(now.get(Calendar.YEAR), now.get(Calendar.MONTH))

        assertEquals(now.get(Calendar.YEAR), Calendar.getInstance().apply { timeInMillis = result }
            .get(Calendar.YEAR))
        assert(result <= System.currentTimeMillis())
        assert(result >= now.timeInMillis - 1000)
    }

    @Test
    fun `asOfMillis returns end of month for a past month`() {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        val result = ReportPeriodSelector.asOfMillis(year, month)

        assertEquals(ReportPeriodSelector.endOfMonthMillis(year, month), result)
    }

    @Test
    fun `asOfMillis returns end of month for a future month`() {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        val result = ReportPeriodSelector.asOfMillis(year, month)

        assertEquals(ReportPeriodSelector.endOfMonthMillis(year, month), result)
    }

    // --- formatIncomeStatementPeriod ---

    @Test
    fun `formatIncomeStatementPeriod shows Month ended once the month has fully elapsed`() {
        val startMs = ReportPeriodSelector.startOfMonthMillis(2026, Calendar.MARCH)
        val endMs = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.MARCH)

        val result = ReportPeriodSelector.formatIncomeStatementPeriod(startMs, endMs, endMs)

        assertEquals("Month ended March 31, 2026", result)
    }

    @Test
    fun `formatIncomeStatementPeriod shows a date range for a month still in progress`() {
        val startMs = ReportPeriodSelector.startOfMonthMillis(2026, Calendar.MARCH)
        val endMs = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.MARCH)
        val asOfMs = millisFor(2026, Calendar.MARCH, 15)

        val result = ReportPeriodSelector.formatIncomeStatementPeriod(startMs, asOfMs, endMs)

        assertEquals("March 1, 2026 to March 15, 2026", result)
    }

    // --- formatAsOfDate ---

    @Test
    fun `formatAsOfDate formats with full month name`() {
        val asOfMs = ReportPeriodSelector.endOfMonthMillis(2026, Calendar.MARCH)
        assertEquals("At March 31, 2026", ReportPeriodSelector.formatAsOfDate(asOfMs))
    }

    // --- monthName ---

    @Test
    fun `monthName returns full month names`() {
        assertEquals("January", ReportPeriodSelector.monthName(Calendar.JANUARY))
        assertEquals("December", ReportPeriodSelector.monthName(Calendar.DECEMBER))
    }
}
