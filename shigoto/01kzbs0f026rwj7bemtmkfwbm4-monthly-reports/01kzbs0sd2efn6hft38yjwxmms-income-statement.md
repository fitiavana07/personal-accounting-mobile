---
title: Income Statement
status: done
order: V
---

This is the logical follow-up of the task "monthly balance sheet".

See fragment_reports.xml.

## Report selection

The simple display "Balance Sheet", change it to a scrollable horizontal
list (just like the list of years and the list of months) containing
"Balance Sheet", "Income Statement", "Statements of Changes in Equity"; and
put it below the month selector.

This new selector allows us to select the report to display below. So
display the current balance sheet only when we select "Balance Sheet" in
the selector. "Income Statement" will be described below. Put "Coming soon"
for "Statements of Changes in Equity".

## Income Statement content

Reuse the structure of the Instant Balance Sheet of (BalanceSheetAdapter.kt),
with the following changes:

- The sections "Income", "Expense", "Gain", "Loss" become the main section
  (in place "ASSETS", "LIABILITIES", "EQUITY", which means these sections
  are gone)
- Keep the accounts of the new main sections detailed like in Instant
  Balance Sheet, and the total at each end of section (e.g. "Total Income")
- Show "Net Income" as the last line, with value equal to `total_income - 
  total_expense + total_gain - total_loss`