# Reports feature

Package: `dev.fitiavana.accounting.ui.reports` (UI/ViewModel) +
`dev.fitiavana.accounting.features.reports` (report builders).

## Screen structure

`ReportsFragment` (`ui/reports/ReportsFragment.kt`) hosts four horizontal
`PeriodSelectorAdapter<T>` tab rows plus content:

- `recycler_reports_years` — years with transactions (`Int`)
- `recycler_reports_months` — months of the selected year (`Int?`, 0-11),
  with a trailing `null` entry rendered as a **"Year"** tab
- `recycler_reports_type` — `ReportType` values (Balance Sheet, Income
  Statement, Statement of Changes in Equity)
- `recycler_reports_content` (visible for Balance Sheet/Income Statement) or
  `scroll_reports_equity` (visible for Changes in Equity) — the report body

`PeriodSelectorAdapter<T>` (`ui/reports/PeriodSelectorAdapter.kt`) is a
single generic adapter reused for all three tab rows: `labelFor: (T) ->
String`, `onSelected: (T) -> Unit`, `submitList(items, selected)` highlights
the selected item (bold + gold + underline).

## `ReportsViewModel` (`ui/reports/ReportsViewModel.kt`)

State (LiveData): `hasTransactions`, `availableYears: List<Int>`,
`availableMonths: List<Int?>` (real months for the selected year, always
followed by a trailing `null` = "Year"), `selectedYear: Int`,
`selectedMonth: Int?` (`null` = "Year" mode selected), `selectedReportType`,
`asOfDateText`, `balanceSheetRows` (used by both Balance Sheet and Income
Statement), `equityStatement`.

Entry points: `start()` (idempotent, kicks off `loadInitialSync` on a
background `Thread`), `selectYear(year)`, `selectMonth(month: Int?)`,
`selectReportType(type)`. Each `xSync` method (e.g. `selectMonthSync`) is
the synchronous, directly-testable counterpart called by tests.

`monthsByYear: Map<Int, List<Int>>` is built once in `loadInitialSync` from
`balanceRepository.getTransactionDateRange()` via
`ReportPeriodSelector.monthsBetween`. `selectYearSync` always re-selects
that year's *last* month (never defaults into Year mode), then appends
`null` to the posted `availableMonths`.

### Period pipeline: `recomputeSync(year, month: Int?)`

Single funnel all selections go through. Branches on `month`:

| | Month mode | Year mode (`month == null`) |
|---|---|---|
| start | `ReportPeriodSelector.startOfMonthMillis(year, month)` | `startOfYearMillis(year)` |
| as-of / cutoff | `asOfMillis(year, month)` — "now" if selected month is the current month, else end of month | `asOfYearMillis(year)` — "now" if selected year is the current year, else end of year |
| previous-period end | `previousMonthEndMillis(year, month)` | `previousYearEndMillis(year)` |

Loads `cachedAccounts` (`accountRepository.getAllSync()`),
`cachedBalancesAsOf = balanceRepository.computeBalancesAsOf(asOfMs)`,
`cachedPeriodBalances = balanceRepository.computeBalancesBetween(startMs, asOfMs)`,
`cachedPreviousPeriodEndBalances = balanceRepository.computeBalancesAsOf(previousPeriodEndMs)`,
then calls `renderDisplay(type)`. `cachedReportMonth: Int?` and
`cachedReportYear` are stashed for `renderDisplay` to rebuild the period-end
boundary needed by `formatIncomeStatementPeriod`.

### `renderDisplay(type: ReportType)`

Reads only the cached fields above (no repository calls — this is what lets
`selectReportType` switch reports without re-querying).

- `asOfDateText`: Balance Sheet → `formatAsOfDate(cachedPeriodCutoffMillis)`;
  Income Statement/Changes in Equity →
  `formatIncomeStatementPeriod(start, cutoff, periodEndMs)` where
  `periodEndMs` is `endOfMonthMillis(year, month)` or, in Year mode,
  `endOfYearMillis(year)`.
- `balanceSheetRows`: Balance Sheet →
  `BalanceSheetBuilder.buildMonthly(accounts, cachedBalancesAsOf)`; Income
  Statement → `IncomeStatementBuilder.build(accounts, cachedPeriodBalances)`;
  Changes in Equity → empty (rendered via `equityStatement` instead).
- `equityStatement`: Changes in Equity only →
  `EquityStatementBuilder.build(accounts, cachedPreviousPeriodEndBalances,
  cachedPeriodBalances, previousBalanceLabel, currentBalanceLabel)`, with
  labels `"Balance at {date}"` built from `cachedPreviousPeriodEndMillis`/
  `cachedPeriodCutoffMillis`.

All three report builders (`features/reports/BalanceSheetBuilder.kt`,
`IncomeStatementBuilder.kt`, `EquityStatementBuilder.kt`) are **period-
agnostic** — they only take pre-computed `Map<String, Long>` balance
snapshots/deltas, never dates. All date-range logic lives in
`ReportPeriodSelector` + `ReportsViewModel.recomputeSync`, so adding a new
period granularity only means adding boundary functions there.

## `ReportPeriodSelector` (`ui/reports/ReportPeriodSelector.kt`)

Pure, stateless date-math object (`Calendar`-based, no clock injection —
"now" is always the real device clock, so tests avoid the current
month/year to stay deterministic).

- `monthsBetween(minMs, maxMs): List<YearMonth>` — every calendar month in
  an inclusive range (`YearMonth(year, month)`); used once to build
  `monthsByYear`.
- Month boundaries: `startOfMonthMillis`, `endOfMonthMillis`, `asOfMillis`
  (now vs. end-of-month), `previousMonthEndMillis` (rolls into prior year
  for January).
- Year boundaries (added for the "Year" tab): `startOfYearMillis`,
  `endOfYearMillis`, `asOfYearMillis` (now vs. end-of-year),
  `previousYearEndMillis`.
- Formatting: `formatDate`, `formatAsOfDate` (`"At {date}"`),
  `formatMonthEnded` (`"Month ended {date}"`), `formatIncomeStatementPeriod
  (startMs, asOfMs, periodEndMs)` — `"Month ended {end}"` once
  `asOfMs >= periodEndMs`, else `"{start} to {asOfMs}"`. This function is
  already period-granularity-agnostic — same code serves both month and
  year periods, just fed different boundaries.
- `monthName(month: Int): String` — 0-11 → full month name.

## `ReportType` (`ui/reports/ReportType.kt`)

Plain enum, not a string resource:
```kotlin
enum class ReportType(val label: String) {
    BALANCE_SHEET("Balance Sheet"),
    INCOME_STATEMENT("Income Statement"),
    CHANGES_IN_EQUITY("Statement of Changes in Equity")
}
```
Orthogonal to the year/month/Year-tab period selection.

## The "Year" tab (added 2026-09-11)

Selecting the "Year" tab (`selectMonth(null)`) switches all three reports to
a full-year period for the selected year:

- **Balance Sheet**: as of the last day of the last month of the year, or
  today if the year is the current year (`asOfYearMillis`).
- **Income Statement**: Jan 1 → Dec 31 of the year, or → today if current
  year (`startOfYearMillis` → `asOfYearMillis`).
- **Statement of Changes in Equity**: same period as Income Statement; the
  "previous balance" is the prior *year*-end balance
  (`previousYearEndMillis`) instead of prior month-end.

Implemented by making `selectedMonth`/`availableMonths`/`selectMonth`
nullable (`Int?`), with `null` meaning "whole year" and always appended as
the last entry after the real months — no changes needed to
`PeriodSelectorAdapter` (already generic) or the report builders (already
period-agnostic).

## Tests

- `ReportPeriodSelectorTest.kt` — pure date-math unit tests per function
  (including the year-level functions above).
- `ReportsViewModelTest.kt` — mocks `AccountRepository`/`BalanceRepository`
  with Mockito, calls `xSync` methods directly (never `Thread`-launching
  public methods) for determinism, uses a `millisFor(year, month, day)`
  helper to build fixture timestamps, and asserts exact
  `computeBalancesBetween`/`computeBalancesAsOf` call arguments via
  `ReportPeriodSelector` boundary functions. Year-mode tests use a past
  year in fixtures to avoid the "current period" `now`-branch being
  non-deterministic.
