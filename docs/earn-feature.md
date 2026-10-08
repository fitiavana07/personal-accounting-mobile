# Earn (Simple Earn yield tracking)

Tracks the interest that an asset account such as a Binance Simple Earn or a savings account pays. You enter
the account's yearly rate (APR); the app projects the daily, monthly and yearly interest from its balances. No
exchange rate or market value is involved, and nothing is fetched from an exchange.

## Setting an APR

**Edit account** shows a *Yearly interest (APR, %)* field for **asset accounts only** (hidden for every other
type, like the liquidity level). Leave it empty for "no interest". A comma is accepted as the decimal separator.
`EditAccountViewModel.saveAccount` keeps the value only for asset accounts and treats zero or a negative rate as
none, so a rate typed and then switched to another type is not saved.

Data: `Account.aprPercent: Double?` (column `accounts.aprPercent`, added by `MIGRATION_18_19`, schema v19).
`BackupRepository` writes and reads it (omitted when null).

**Why 18 to 19 and not 17 to 18:** version 18 had already shipped with only the template tables, so a phone on
that build has a v18 database without the column. Adding the column to `MIGRATION_17_18` afterwards made Room
reject that database on launch ("Room cannot verify the data integrity ... identity hash"). Rule: once a schema
version has been installed anywhere, never edit its migration; add a new version. `MIGRATION_18_19` also skips the
`ALTER` if the column already exists. A backup made on v18 cannot be restored on v19 (the app only restores
a backup of the same schema version).

## The calculation (`features/balances/YieldCalculator`)

Simple interest, not compounded:

```
interest = balance x APR / 100 x periodFraction     (rounded to the nearest unit)
```

`periodFraction` is 1/365 for daily, 1/12 for monthly and 1 for yearly (`YieldPeriod`). The account's one APR
applies to each of its three balances separately, in that balance's own units:

- the base-currency balance (`balance`),
- the account instrument balance (`instrumentBalance`), when the account has an instrument,
- the intermediary instrument balance (`intermediaryBalance`), when it has an intermediary instrument.

So an account with 2.0 BTC (worth Ar 5,000,000 on the books) at 10% yearly shows `Ar 500,000 · 0.2 BTC`. Amounts
are stored as integers in minor units, so the daily interest of a small balance can round to zero.

`YieldCalculator.project` returns null when there is no positive APR. `YieldAmounts` holds base, instrument and
intermediary (the last two null when the account has none).

## Where it shows

- **Accounts list:** an `x% APR` line under the account type (`item_account.xml`, `AccountsAdapter`), hidden when
  the account has no APR.
- **Reports, Balance Sheet:** an asset account with an APR becomes tappable even without instruments. Expanding it
  (the same chevron / "Expand all" as the native amounts) shows the instrument amounts, then an `APR  5.5%` row.
  `BalanceSheetBuilder.buildMonthly` puts the rate on `ReportRow.AccountLine.aprPercent`; `ReportPresenter` adds a
  `ReportDisplayRow.AprLine`; `ReportAdapter` renders it like a native row.
- **Earn screen** (`ui/earn/`), opened from the **Earn** button on the Home shortcuts row (after CEX Prices and
  Mo. Expenses; `ic_home_earn` is a coin with an up arrow). It lists every asset account with an APR, **largest
  yearly interest first** (then by name). With no Earn account it shows a hint on how to set one.
  - **Totals card** (tinted): the total *monthly* base-currency interest as a large number, with
    `Ar x / day · Ar y / year` under it.
  - **Account card**: name and APR, a balance line (base, then instrument and intermediary amounts), then a
    Day / Month / Year row of **base-currency** interest only (so the columns line up between cards; Month is bold).
    A thin bar at the bottom shows the account's share of the total yearly interest (`EarnItem.yearlySharePercent`).
  - **Tap** an account that has an instrument to show its interest in the instrument's (and intermediary's) own
    units, one `Day:` / `Month:` / `Year:` line each; tap again to hide. Open cards stay open across data refreshes
    (the adapter keeps the set of open account ids). Accounts without an instrument are not tappable.

## Code map

- `EarnItemBuilder` (pure) builds `EarnState(items, totals)` from accounts, balances and instruments.
- `EarnViewModel` merges the accounts, balances and instruments LiveData (like `AccountsViewModel`), so the screen
  stays current; `EarnAdapter` renders the totals card and one card per account (and owns the open/closed state); `EarnActivity` hosts them.
- `TransactionDisplay.formatApr` / `formatAprPercent` format a rate without trailing zeros ("5.5", "12", "5.5%").

## Tests

`YieldCalculatorTest`, `EarnItemBuilderTest`, `EarnViewModelTest`, `EarnAdapterTest`, `EarnActivityTest`,
`AccountDaoTest` (APR round trip), `Migration18To19Test` (the new column matches Room's schema, also upgrading
from 17, and re-running is harmless), `Migration17To18Test` (leaves `accounts` untouched),
`BackupRepositoryTest`, `EditAccountViewModelTest`, `EditAccountActivityTest`, `AccountsAdapterAprTest`,
`BalanceSheetBuilderTest` / `ReportPresenterTest` / `ReportAdapterTest` / `ReportsViewModelTest` (APR row),
`TransactionDisplayTest`, `HomeShortcutsAdapterTest` and `HomeFragmentTest` (the Earn button).

### Manual checklist (on a device, run it yourself)

1. Edit an asset account, set APR `5.5`, save. The Accounts list shows `5.5% APR` under it.
2. Home: tap **Earn**. The account appears with its balance and daily / monthly / yearly interest, and the totals
   card adds them up. Compare one figure by hand: balance x 5.5% / 12 for the monthly interest.
3. For an account with an instrument (and an intermediary instrument), the instrument and intermediary interest
   appear after the base amount on each line.
4. Reports, Balance Sheet: the account shows a chevron; expanding it shows the `APR  5.5%` row under the native
   rows. **Expand all** includes it.
5. Clear the APR (or change the account to another type): it leaves the Earn screen and the Accounts list line.
6. Back up and restore: the APR is still there.
