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

    /**
     * The as-of instant for [year]/[month]: "now" for the current calendar month (since it hasn't
     * ended yet), otherwise the last millisecond of that month.
     */
    fun asOfMillis(year: Int, month: Int): Long {
        val now = Calendar.getInstance()
        return if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)) {
            now.timeInMillis
        } else {
            endOfMonthMillis(year, month)
        }
    }

    /**
     * The last millisecond of the month preceding [month] (0-11) in [year],
     * rolling back into December of the prior year when [month] is January.
     */
    fun previousMonthEndMillis(year: Int, month: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MILLISECOND, -1)
        }
        return cal.timeInMillis
    }

    /**
     * The first millisecond of [year].
     */
    fun startOfYearMillis(year: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * The last millisecond of [year].
     */
    fun endOfYearMillis(year: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.YEAR, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return cal.timeInMillis
    }

    /**
     * The as-of instant for [year]: "now" for the current calendar year (since it hasn't ended
     * yet), otherwise the last millisecond of that year.
     */
    fun asOfYearMillis(year: Int): Long {
        val now = Calendar.getInstance()
        return if (year == now.get(Calendar.YEAR)) {
            now.timeInMillis
        } else {
            endOfYearMillis(year)
        }
    }

    /**
     * The last millisecond of the year preceding [year].
     */
    fun previousYearEndMillis(year: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MILLISECOND, -1)
        }
        return cal.timeInMillis
    }

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val monthNameFormat = SimpleDateFormat("MMMM", Locale.getDefault())

    fun formatDate(ms: Long): String = dateFormat.format(Date(ms))

    fun formatAsOfDate(asOfMs: Long): String = "At ${dateFormat.format(Date(asOfMs))}"

    fun formatMonthEnded(asOfMs: Long): String = "Month ended ${dateFormat.format(Date(asOfMs))}"

    /**
     * Income statement period text: "Month ended {end}" once the month has fully elapsed
     * ([asOfMs] reached [endOfMonthMs]), otherwise "{start} to {asOfMs}" for a month still in
     * progress (see [ReportPeriodSelector.asOfMillis]).
     */
    fun formatIncomeStatementPeriod(startMs: Long, asOfMs: Long, endOfMonthMs: Long): String =
        if (asOfMs >= endOfMonthMs) {
            formatMonthEnded(asOfMs)
        } else {
            "${dateFormat.format(Date(startMs))} to ${dateFormat.format(Date(asOfMs))}"
        }

    fun monthName(month: Int): String {
        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, month) }
        return monthNameFormat.format(cal.time)
    }

    /**
     * Whether [dateMs] falls on the last calendar day of its month.
     */
    fun isLastDayOfMonth(dateMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        return cal.get(Calendar.DAY_OF_MONTH) ==
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /**
     * Up to [n] consecutive full calendar months ending at the last full month before/at
     * [nowMs] (the current month counts as full only when [nowMs] is its last day; otherwise
     * the window ends the month before), in chronological order (oldest first), never starting
     * before the calendar month containing [earliestTransactionMs]. Returns an empty list when
     * [earliestTransactionMs] is null or falls entirely after the window.
     */
    fun lastNFullMonths(nowMs: Long, n: Int, earliestTransactionMs: Long?): List<YearMonth> {
        if (earliestTransactionMs == null) return emptyList()

        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val lastFullMonth = Calendar.getInstance().apply {
            set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            if (!isLastDayOfMonth(nowMs)) add(Calendar.MONTH, -1)
        }

        val earliest = Calendar.getInstance().apply { timeInMillis = earliestTransactionMs }
        val earliestYearMonth = earliest.get(Calendar.YEAR) * 12 + earliest.get(Calendar.MONTH)
        val lastFullYearMonth =
            lastFullMonth.get(Calendar.YEAR) * 12 + lastFullMonth.get(Calendar.MONTH)
        if (lastFullYearMonth < earliestYearMonth) return emptyList()

        val startYearMonth = maxOf(earliestYearMonth, lastFullYearMonth - (n - 1))

        val months = mutableListOf<YearMonth>()
        for (ym in startYearMonth..lastFullYearMonth) {
            months += YearMonth(ym / 12, ym % 12)
        }
        return months
    }
}
