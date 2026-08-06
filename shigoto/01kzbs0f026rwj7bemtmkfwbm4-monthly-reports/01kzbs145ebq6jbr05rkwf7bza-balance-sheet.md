---
title: Balance Sheet
status: done
order: s
---

This is the first financial statement to implement.

Add a new menu "item" "Reports" in the bottom navigation bar, between 
"Home" and "Balances" items.

## Reports menu content

### Selection

Implement as described below in this new menu.

We want to implement month by month balance sheet.

In the top, show in normal font size: "Balance Sheet".
Below it, show a horizontal scrollable list of years showing years. E.g. 
"2026".
Below it, show a horizontal scrollable list of scrollable items showing 
months. E.g. "January".

Limit years and months to years and months as found in transactions. For 
that, I think you can take the oldest transaction by transactionDatetime, 
and the most recent transaction, and this way we can get all years and 
months between those two farthest transactions.


This list of years and months is used for choosing which balance sheet to 
show.

Balance sheets are on a date, not a period (like a month), so selecting a 
year and a month makes the app select the last day of the selected month 
(and year). For now, we only do end-of-month-balance sheet. No end-of-year 
balance sheet. Before the balance sheet content, show the date, e.g. "At 
March 31, 2026".

The last year of the list and the last month of the list are selected by 
default.


### Content

Take most of what is in Instant Balance Sheet (BalanceSheetAdapter.
kt), with these changes:

- no pie chart here, so don't add the colored circle before asset accounts
- Income, Expense, Gain, Loss become grouped into a group "Unclosed Income 
  Statement accounts". Each old group (e.g. "Income") become a single line 
  in this new "Unclosed Income Statement accounts" group, with its balance 
  equal to the total of this category as shown in Instant Balance Sheet (e.
  g. "Total Gain" for "Gain"). And the end, following the group structure, 
  add "Total Unclosed IS accounts" (IS is Income Statement)" to show the 
  total of the group
- Drawing remains a distinct group
- Exclude "Total Changes in Equity" line

For the amounts, we need to calculate the balance of these accounts at the 
end of the selected month. This should be doable using all transactions 
concerning the given accounts, up to the given date (end of the selected 
month/year). We'll optimize this calculation later. So for now, we would
always recalculate before display, when we don't yet have the balances. See
recalculateAll of BalanceRepository and reuse what can be reused (and
refactor where relevant)