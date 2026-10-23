# Reports feature

Package: `dev.fitiavana.accounting.ui.reports` (UI/ViewModel) +
`dev.fitiavana.accounting.features.reports` (report builders).

## Screen structure

`fragment_reports.xml` has a pinned header (outside the scroll area), a
`NestedScrollView` with the trends and the report body, and an empty state.

Pinned header (`layout_reports_header`):

- `text_reports_year` — a chip showing the selected year; tapping it opens a
  `ListPopupWindow` with the years that have transactions (`showYearDropdown`)
- `recycler_reports_months` — months of the selected year (`Int?`, 0-11),
  with a trailing `null` entry rendered as a **"Year"** tab
  (`PeriodSelectorAdapter`, `item_period_compact.xml`)
- `recycler_reports_type` — `ReportType` tabs with an icon and `shortLabel`
  (Balance Sheet, Income, Equity; `item_period_segment.xml`)
- `text_reports_as_of_date` — the as-of / period line

Scroll area (`scroll_reports`): the trends block (see below), the
"Expand all" control, then the report body: `recycler_reports_content`
(visible for Balance Sheet/Income Statement) or `scroll_reports_equity`
(visible for Changes in Equity), and a bottom spacer clearing the add button.

`PeriodSelectorAdapter<T>` (`ui/reports/PeriodSelectorAdapter.kt`) is a
single generic adapter reused for the month and type rows: `labelFor: (T) ->
String`, `onSelected: (T) -> Unit`, `submitList(items, selected)` highlights
the selected item (bold + gold + underline).

## Report body style (section cards)

On this screen the report bodies are drawn as rounded cream **cards**; Home's
"Instant Balance Sheet" reuses the same adapter but keeps the flat look.

- **`ReportAdapter(cardStyle = true)`** (only `ReportsFragment` passes it).
  `submitList` computes a `CardRole` per row with `ReportAdapter.cardRolesFor`:
  a card runs from a `SectionHeader` (`TOP`) through its rows (`MIDDLE`) to the
  next *emphasized* `TotalLine` (`BOTTOM`, a slightly darker footer). An
  emphasized total outside any section (the Income Statement's Net Income) is
  its own `SINGLE` card; non-emphasized subtotals stay inside the card;
  `Title`/`DateLine` rows are never in a card.
- `RowViewHolder.applyCard` runs after each `bind*`: it sets the role's
  background (`bg_report_card_top/middle/bottom/single.xml`, colours
  `report_card_bg` / `report_card_footer_bg`, light and night), 16dp side
  margins and a 12dp gap above `TOP`/`SINGLE` rows. Margins are reset on every
  bind because views are recycled.
- **Net result tint:** `ReportDisplayRow.TotalLine.netResultSign` (-1/0/1,
  set by `ReportPresenter` for `parenthesizeNegative` totals, i.e. Net Income /
  Net Loss and "Total Unclosed IS accounts"; `null` otherwise). Only an
  *emphasized* total with a non-zero sign is tinted, green (`report_net_gain`)
  or red (`report_net_loss`). Income/Expense/Gain/Loss lines and totals stay
  neutral (parentheses already show contra amounts).
- **Changes in Equity** (`EquityStatementAdapter`): one card made of the rows,
  the column-titles row on top (muted), the "Balance at ..." rows tinted
  (`bg_report_card_footer`), "Changes in ..." rows plain, the last row closing
  the card. Numbers use the proportional font with tabular figures (`tnum`, API
  21+), not monospace; `"-"` cells are muted. The table still scrolls
  horizontally inside `scroll_reports_equity` (16dp start/end padding).
- Do not edit the shared colours `report_title`, `report_header_text`,
  `report_divider`, `report_section_header_bg`, `report_grand_total_bg`: Home,
  Transaction detail, Sync and Cloud Backup use them. Add `report_card_*`
  names instead.

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

Plain enum, not a string resource; `shortLabel` and `iconRes` are used by the
type tabs:
```kotlin
enum class ReportType(val label: String, val shortLabel: String, val iconRes: Int) {
    BALANCE_SHEET("Balance Sheet", "Balance Sheet", R.drawable.ic_report_balance_sheet),
    INCOME_STATEMENT("Income Statement", "Income", R.drawable.ic_report_income_statement),
    CHANGES_IN_EQUITY("Statement of Changes in Equity", "Equity", R.drawable.ic_report_equity_statement)
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

## Balance Sheet: tap-to-expand native balances

On the Balance Sheet only, an individually listed asset/liability/equity account that has an instrument
can be tapped to reveal sub-rows with its balance in its instrument and, if set, its intermediary
instrument (label = instrument code, amount right-aligned in the same column). An "Expand all /
Collapse all" control (`text_reports_expand_all`) sits under the as-of date.

- Totals, subtotals, the lumped "Other" line and the Income/Expense/Gain/Loss/Drawing lines stay base-only and
  are not expandable. Revenue/expense/drawing/gain/loss accounts cannot have an instrument
  (`AccountTypes.supportsInstrument`, enforced in `EditAccountViewModel`/`EditAccountActivity`).
- Native balances are computed as of the period cutoff like base balances, from the entries'
  `instrument*`/`intermediary*` amounts (`BalanceRepository.computeInstrumentBalancesAsOf` /
  `computeIntermediaryBalancesAsOf`); no market value.
- `ReportsViewModel` caches those maps and keeps `expandedAccountIds` across period/report switches;
  `toggleAccount`/`toggleExpandAll` re-render from caches (no DB access). `ReportRow.AccountLine` carries
  `accountId` + `nativeAmounts`; `ReportPresenter` turns them into `expandable`/`expanded` lines followed by
  `ReportDisplayRow.NativeLine` sub-rows; `ReportAdapter(onAccountClick)` renders the chevron and click.
- **APR sub-row:** an asset account with an APR (see `docs/earn-feature.md`) is also expandable, even with no
  instrument. `BalanceSheetBuilder.buildMonthly` copies `Account.aprPercent` to `ReportRow.AccountLine.aprPercent`;
  `ReportPresenter` appends a `ReportDisplayRow.AprLine` (e.g. `5.5% `) after the native sub-rows, and
  `ReportAdapter` renders it as an "APR" row in the same column. The rate is underlined and tappable
  (`ReportAdapter(onAprClick)`): it opens `EditAprDialog` (`ui/common/`), and Save calls
  `ReportsViewModel.updateApr` -> `AccountRepository.updateApr` (stamped for sync), then reloads the sheet. Only
  positive rates on asset accounts can be saved (there is no "remove" here; clear the APR in Edit account).

## Trend charts (top of the scroll area)

- A "Trends" block (`text_trend_toggle` + `layout_trend`) opens the scroll area, below the pinned header. Tapping
  the toggle (chevron ▼/►) collapses or expands it. It displays two charts at once: net worth
  (`LineChart`) and net income (`BarChart`, coloured by sign), for the last `TrendSeriesBuilder.MAX_MONTHS` (12)
  months that have transactions. The scroll area is a `NestedScrollView`, so the charts scroll away and the
  report list does not scroll by itself.
- `TrendSeriesBuilder` (pure) reuses the report builders: net worth = `BalanceSheetBuilder.totalEquity` over
  `computeBalancesAsOf(asOfMillis)`, net income = `IncomeStatementBuilder.netIncome` (the Income Statement's
  Net Income row) over `computeBalancesBetween(startOfMonth, asOfMillis)`; the current month is "as of now".
- `ReportsViewModel.trendPoints` is loaded once by `loadInitialSync` (after the first `recomputeSync`, which fills
  the cached accounts) and does not change when the period changes. `trendPoints` is `null` until loaded: the
  block is then `INVISIBLE` (keeps its height, so the report below does not jump when the charts arrive), and
  becomes `GONE` only if the loaded trend has fewer than 2 points (`TrendChartRenderer.blockVisibility`).
- Rendering lives in `TrendChartRenderer` (MPAndroidChart, same approach as the Home `PieChartRenderer`).
- Look: net worth is a low-tension (`CURVE_INTENSITY`, so it does not overshoot the data) filled curve without point
  dots; net income bars are 0.6 wide with a zero baseline; both charts have no axis lines, a faint dashed grid, small
  axis text, extra offsets (so the x labels are not clipped) and a short entry animation.

## Tests

- `TrendSeriesBuilderTest`, `TrendChartRendererTest`, plus the trend cases in `ReportsViewModelTest` /
  `ReportsFragmentTest`.
- `ReportAdapterTest` (Robolectric) — row binding, chevron/APR clicks, and the card style: `cardRolesFor`
  grouping, card margins/backgrounds, recycling reset, flat default style, net-result tint.
- `EquityStatementAdapterTest` (Robolectric) — card backgrounds, tabular proportional numbers, muted titles and
  dashes. (Bold weight is not asserted: Robolectric's `Typeface` does not report it.)
- `ReportPresenterTest` — formatting and `netResultSign`.
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
