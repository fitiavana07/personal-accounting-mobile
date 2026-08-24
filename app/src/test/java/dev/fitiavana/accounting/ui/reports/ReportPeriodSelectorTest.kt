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
