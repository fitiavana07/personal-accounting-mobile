---
title: Statement of Changes in Equity
status: done
order: k
---

See fragment_reports.xml.

## Content

When selecting "Statement of Changes in Equity" (ReportType.CHANGES_IN_EQUITY),
the report view (currently displaying "Coming soon") should display the
Statement of Changes in Equity report for the selected year for the
selected month.

Unlike the other reports (income statement, balance sheet), this is a
**matrix** (multiple value columns per row), not a single label + amount per
row. `ReportRow`/`ReportAdapter` only support one label + one amount per row,
so they cannot be reused as-is for this report. Build a new table row type
and a new adapter/view capable of rendering a label column plus N value
columns. Since the number of columns is data-dependent (depends on how many
equity accounts exist), the table should scroll horizontally when it doesn't
fit the screen width.

The period label is the same as for "Income Statement" ("Month ended ...")

The columns are:

- First column with no title (this will contain labels)
- Equity Account 1
- Equity Account 2
- Equity Account 3
- "Unclosed IS Accounts"
- "Drawing"
- "Total"

Of course, "Equity Account 1" and 2 and 3 should be replaced by the list of
accounts of type equity. Unlike in other reports, always show the account
even if its balance is 0.

The first column of the rows are:

- The header row containing the column titles listed above
- "Balance at `last day of the previous month here`"
- "Changes in Equity Account 1"
- "Changes in Equity Account 2"
- "Changes in Equity Account 3"
- "Changes in Unclosed IS Accounts"
- "Changes in Drawing"
- Balance at `last day of the selected month here`

"previous month" is the month previous to the selected month.

Like in other reports, the last day of the selected month should be
replaced with the current day if the selected month is the current month

"Changes in Equity Account 1", 2 and 3 should place "Equity Account (number)
" with the account name like in the column titles

Use two separate strings, both new:
- `"Unclosed IS Accounts"` (short form) for this report's column title.
- `"Unclosed Income Statement Accounts"` (full form) for future use.

Do not touch or rename the existing hardcoded string in
`BalanceSheetBuilder` ("Unclosed Income Statement accounts" subsection
header, "Total Unclosed IS accounts" total label) — leave those as they are.
The new strings are for this report and future reuse only.

## Content of the table

These are the content of table. The title of the following sections
correspond to the row names (first column) (one section is one row).

Reuse existing calculation functions (Balance Sheet's as-of balance
functions, Income Statement's period-sum functions) where they fit as-is.
Don't force reuse where the shapes are incompatible — duplicate the small
amount of logic instead of contorting a shared function to serve two
different callers.

### Sign convention

Drawing amounts in this report (both the previous/end-of-month balance and
the "Changes in Drawing" row) are **negative** (contra) — used as negative in
every sum/total involving them — consistent with how Drawing is already
contra in the Balance Sheet, and with the existing `totalEquityOf` formula
subtracting total Drawing. This way the Total column can be computed with a
plain sum, without a special-cased subtraction for Drawing.

Any negative value in this table (Drawing amounts, a net loss in "Unclosed
IS Accounts", a negative Total, etc.) is displayed in **parenthesized
format**, e.g. `(1 000)`, not with a leading `-`. This matches the
`contra`/`parenthesizeNegative` display convention already used by
`ReportRow` for the other reports. This is unrelated to the "-" placeholder
used for "not relevant here" cells (see below) — that "-" is a dash literal,
not a formatted amount, and stays as-is regardless of sign.

### Total column

The Total column of every row is the sum of that row's cells, treating any
"-" (not relevant) cell as 0:
- For "Balance at ..." rows (previous month and last day of selected month),
  this sum equals the Total Equity as of that date.
- For a "Changes in ..." row, since only one cell in that row is a real
  number and the rest are "-" (0), the Total column simply equals that one
  value. Compute it the same way as the other rows (sum of cells) rather
  than special-casing "just copy the one value" — the two are equivalent
  given "-" cells are 0, and staying consistent avoids a special case in the
  table-rendering code.

### "Balance at `last day of the last month here`"

It shows for each column the balance of the account on the column at the end
of the previous month, meaning last day last second. Total column for that
row is the total of all these balances.

For Equity Accounts and Drawing, this should be the same balance we show for
that month (previous to the selected month) for this account in the balance
sheet — but note the Balance Sheet's displayed rows filter out zero-balance
accounts, while this report must always show the account (see above). So
reuse the underlying as-of balance calculation (e.g.
`BalanceRepository.computeBalancesAsOf`), not the already-filtered rows the
Balance Sheet renders.

The same for Unclosed IS Accounts, we should reuse the balance of "Unclosed
IS Accounts" in the balance sheet. Maybe reuse the same function for
calculating that balance but instead of the selected month, calculate for
the month preceding the selected month.

If the selected month is the first month with any transaction (no previous
month), all columns for this row are 0 (including Total).

### "Changes in Equity Account 1", 2, 3...

For each Equity Account, it shows the changes in the balance of this equity
account. It means, the sum of all transactions between the first day of the
month (00:00) and the last day of the month (or the current day if the
selected month is the current month). The Income Statement report should
already have a similar calculation and already use functions that we can
reuse here for calculation transaction sums for a specific period

This change in an account should show in the column of that same account.
For other columns, it should just show "-" or better mean to say "not
relevant here"

### Changes in Unclosed IS Accounts

This should be the Net Income / Net Loss of the selected month as shown in
the Income Statement report. The amount should be positioned to the column
"Unclosed IS Accounts" because this amount changes this sum account
"Unclosed IS Accounts".

### Changes in Drawing

Shows the sum of transactions on drawing in this period, shown as negative
and parenthesized (see Sign convention above). Should show in "Drawing"
column.

### Balance at last day of the month

It should show for each column the balance at the start of the month summed
to the changes in this column in this account.