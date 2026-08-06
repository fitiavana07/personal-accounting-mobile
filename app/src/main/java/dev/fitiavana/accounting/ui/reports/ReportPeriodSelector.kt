package dev.fitiavana.accounting.ui.reports

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class YearMonth(val year: Int, val month: Int)

object ReportPeriodSelector {

    /**
     * Every calendar month from [minMs]'s month/year to [maxMs]'s month/year, inclusive.
     */
    fun monthsBetween(minMs: Long, maxMs: Long): List<YearMonth> {
        val start = Calendar.getInstance().apply { timeInMillis = minMs }
        val end = Calendar.getInstance().apply { timeInMillis = maxMs }

        val months = mutableListOf<YearMonth>()
        val cursor = Calendar.getInstance().apply {
            set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endYearMonth = end.get(Calendar.YEAR) * 12 + end.get(Calendar.MONTH)

        while (cursor.get(Calendar.YEAR) * 12 + cursor.get(Calendar.MONTH) <= endYearMonth) {
            months += YearMonth(cursor.get(Calendar.YEAR), cursor.get(Calendar.MONTH))
            cursor.add(Calendar.MONTH, 1)
        }
        return months
    }

    /**
     * The first millisecond of [month] (0-11) in [year].
     */
    fun startOfMonthMillis(year: Int, month: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * The last millisecond of [month] (0-11) in [year].
     */
    fun endOfMonthMillis(year: Int, month: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return cal.timeInMillis
    }

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val monthNameFormat = SimpleDateFormat("MMMM", Locale.getDefault())

    fun formatAsOfDate(asOfMs: Long): String = "At ${dateFormat.format(Date(asOfMs))}"

    fun formatMonthEnded(asOfMs: Long): String = "Month ended ${dateFormat.format(Date(asOfMs))}"

    fun monthName(month: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, month) }
        return monthNameFormat.format(cal.time)
    }
}
