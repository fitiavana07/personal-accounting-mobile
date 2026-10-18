# Home screen: improvement proposals

Date: 2026-10-18

## How this review was done

The review combines a reading of the code (`ui/home/`, `fragment_home.xml`,
`HomeViewModel`, the adapters) with a walkthrough of the running app:

- Device: the "Pixel 4a" virtual device (API 30), debug build 1.45-dev.
- Data: an empty database first, then seeded data (6 instruments, 11 accounts,
  7 months of transactions, a monthly-expense setting).
- Scenarios: first launch with no data, populated screen scrolled from top to
  bottom, and a launch with the network turned off.

The proposals are ordered from the strongest recommendation to the weakest.
Effort is a rough estimate: small (a few hours), medium (about a day), large
(several days). Every change should follow the project's TDD rule.

## Summary

The home screen works, and the metrics card is clear and well designed. The
main problems are about priorities and trust: a new user sees a blank screen,
the most useful information (the positions) is at the very bottom, and some
numbers are labelled or capped in a way that contradicts other numbers on the
same screen.

| #  | Proposal                                          | Effort |
|----|---------------------------------------------------|--------|
| 1  | Fix the empty state                               | Small  |
| 2  | Show more information in each position row        | Medium |
| 3  | Fix the Income to Expenses section                | Small  |
| 4  | Reorder the screen                                | Medium |
| 5  | Remove the duplicated metrics                     | Medium |
| 6  | Add offline, error and freshness feedback         | Medium |
| 7  | Make the hidden small positions visible           | Small  |
| 8  | Make the second pie chart discoverable            | Small  |
| 9  | Polish consistency and layout                     | Small  |
| 10 | Improve accessibility                             | Small  |
| 11 | Pay down technical debt                           | Large  |

---

## 1. Fix the empty state

**Problem.** With no data, the whole home screen is blank. The only content is
the text "No cryptocurrency positions with an intermediary instrument yet". The
metrics card, the shortcuts (CEX Prices, Earn) and the P2P block are all
hidden, so a new user cannot reach any feature from this screen.

**Evidence.** The empty view is shown when both the balance sheet and the
position list are empty (`HomeFragment.updateEmptyState`), and the recycler is
hidden entirely. The message also mentions only cryptocurrency, although
stocks are supported.

**Proposal.**
- Keep the recycler visible and show the empty state as one of its items, so
  the shortcuts and P2P prices stay available.
- Replace the message with an invitation to start, for example "Add your first
  account to see your balances here", with a primary button that opens the
  account or transaction screen.
- Use a separate, shorter message when accounts exist but no position
  qualifies (see proposal 7).

## 2. Show more information in each position row

**Problem.** A position row shows only the account name and its gain or loss
(percentage, Ar and the intermediary currency). The user cannot see how much
they hold, at what price, or how old that price is.

**Evidence.** `HomeItem` already carries `instrumentBalanceFormatted`,
`bookRate`, `currentRate` and `rateFetchedAt`, but `HomeAdapter` does not
display them. They are only visible after opening the detail screen.

**Proposal.**
- Add a second line under the name: quantity held and current price, for
  example "0.02 BTC · 68,000 USDT".
- Show the average cost or the age of the price in a small secondary text, and
  mark prices that are older than a threshold (see proposal 6).
- Keep the gain or loss on the right, with the percentage as the main figure.

## 3. Fix the Income to Expenses section

**Problem.** The section is confusing and contradicts the metrics card.

**Evidence.**
- Its caption reads "Monthly Spend" above a value that is the average
  income (Ar 1,713,333), next to a target of Ar 1,500,000. It reads as if the
  user spent more than planned.
- The metrics card says Income / Spend is 114%, while the section says "100%
  reached", because the percentage is capped.
- The progress bar is a solid dark bar, so it does not read as progress.

**Proposal.**
- Label the value "Average monthly income" and the target "Monthly spend".
- Show the real percentage (114%) as text and cap only the bar's fill.
- Use a clearly visible track with a lighter colour behind the filled part.

## 4. Reorder the screen

**Problem.** The positions, which are the most actionable content, are the
last section. The user must scroll about four screens to reach them. Before
them come the pie charts (about a third of a screen) and the full balance
sheet (more than one screen), which duplicates the Reports tab.

**Proposal.** Use this order:
1. Metrics card (equity first, with a larger headline number).
2. Positions, with the stock-estimate note attached to the list.
3. Goals (see proposal 5).
4. Shortcuts and P2P prices.
5. Breakdown: pie charts, then the balance sheet, possibly collapsed behind a
   "Breakdown" header or a link to the Reports tab.

## 5. Remove the duplicated metrics

**Problem.** The same facts appear twice, in the metrics card and again in a
dedicated section:
- Emergency Fund: "100%" in the card, then a full section with the same
  percentage.
- Income / Spend and Monthly Spend: in the card, then again in the Income to
  Expenses section.

**Evidence.** The two section layouts (`item_home_emergency_fund.xml` and
`item_home_income_to_expenses.xml`) are almost identical. This also goes
against the project's DRY rule.

**Proposal.**
- Keep the compact card for headline numbers (equity, cash, runway).
- Move the two progress bars into a single "Goals" section built from one
  shared layout and one shared adapter.
- Move the monthly-expense editing action into that section, instead of the
  "Mo. Expenses" shortcut.

## 6. Add offline, error and freshness feedback

**Problem.** The screen gives little feedback when data cannot be loaded.

**Evidence.**
- With the network off, the P2P block shows bare dashes, with no message and
  no "last updated" time.
- The "Failed to refresh some rates" toast appears only when every rate fails.
  A partial failure is silent, so some prices can stay stale.
- No screen element tells the user when prices were last updated.

**Proposal.**
- Show one line such as "Prices updated 12:31", turning to a warning colour
  when the data is old or the last refresh partly failed.
- In the P2P block, replace the dashes with "No connection. Pull to retry" or
  the time of the last successful fetch.
- Keep the toast for a complete failure only.

## 7. Make the hidden small positions visible

**Problem.** Positions below 10,000 Ar are silently left out of the position
list, with no setting and no hint.

**Evidence.** `HomeItemBuilder.MIN_BALANCE_AR` filters them. In the test data,
the 6,000 Ar "Dogecoin dust" account appeared in the balance sheet and the pie
chart, but not in the list.

**Proposal.**
- Add a line at the end of the list such as "1 small position hidden", which
  expands the list when tapped.
- Alternatively, make the threshold a setting.

## 8. Make the second pie chart discoverable

**Problem.** The two pie charts (by liquidity and by asset) sit in a
horizontal carousel. The second chart appears only as a sliver at the screen
edge, with no page indicator, so it is easy to miss. The charts are also large
compared with the information they carry.

**Proposal.**
- Add page dots or two small tabs ("By liquidity", "By asset").
- Reduce the chart height, or show them side by side at a smaller size.

## 9. Polish consistency and layout

Small issues that together make the screen feel less coherent:
- Section captions ("6-Month Emergency Fund", "Monthly Spend") are centred
  under left-aligned headers. Align them to the left.
- The metrics card uses compact amounts (Ar 22.2M), while the sections use
  full amounts (Ar 13,960,000). Choose one style per context and explain it, or
  show the full amount on tap.
- `HomeMetricsAdapter` builds amounts with `R.string.amount_ar` directly. The
  project guidance asks for `UiUtils.formatAmountAr` in UI code.
- The floating action button covers the end of each scroll position. Add
  enough bottom padding, or place the last card above it.
- The shortcut row is aligned to the left, with a large empty area on the
  right. Spread the items evenly or use the space.
- The "Mo. Expenses" label is abbreviated. Use the full text, or move the
  action (see proposal 5).

## 10. Improve accessibility

- The progress bars have no content description, so a screen reader cannot
  announce the value.
- Secondary text is 12 to 13 sp, which is small for a screen that is read at a
  glance. Consider 14 sp and checking the contrast on the metrics card.
- Positive and negative gains are distinguished only by colour (green and
  red). The sign (+ or -) is already present, which helps, but a test with
  colour-blind simulation is recommended.

## 11. Pay down technical debt

These points come from reading the code. They are hypotheses, not problems
seen on screen, so do them only when they matter or when touching the code
anyway.

- `HomeAdapter.submitList` calls `notifyDataSetChanged`. Moving to
  `ListAdapter` with `DiffUtil` would keep scroll position and animations when
  prices update.
- `HomeFragment` starts raw `Thread` objects for refreshing and for dialogs. If
  the view is destroyed during a refresh, the callback can touch a dead view.
  Prefer a lifecycle-aware executor owned by the ViewModel.
- `HomeViewModel` has seven `MediatorLiveData` blocks, and most of them
  subscribe to the same balances and accounts. A single combined state object
  would remove repeated work and make the code easier to test.

---

## Considered and dropped

- Moving "Mo. Expenses" out of the shortcuts is only a minor point on its own.
  It is kept as part of proposals 5 and 9.

## Suggested first steps

1. Proposals 1 and 3. They fix the most visible problems with little work.
2. Proposals 2 and 4. They change what the screen is for, so they should be
   designed together.
3. Proposals 5 and 6, then the polish items.
