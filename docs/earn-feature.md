# Earn (Simple Earn yield tracking)

Tracks the interest that an asset account such as a Binance Simple Earn or a savings account pays. You enter
the account's yearly rate (APR); the app projects the daily, monthly and yearly interest from its balances. No
exchange rate or market value is involved, and nothing is fetched from an exchange.

## Setting an APR

**Edit account** shows a *Yearly interest (APR, %)* field for **asset accounts only** (hidden for every other
type, like the liquidity level). Leave it empty for "no interest". A comma is accepted as the decimal separator.
`EditAccountViewModel.saveAccount` keeps the value only for asset accounts and treats zero or a negative rate as
none, so a rate typed and then switched to another type is not saved.

Data: `Account.aprPercent: Double?` (column `accounts.aprPercent`, added by `MIGRATION_17_18`, which also creates
the template tables). `BackupRepository` writes and reads it (omitted when null).

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
  Mo. Expenses; `ic_home_earn` is a coin with an up arrow). It lists every asset account with an APR, largest
  balance first, each with its balance and daily / monthly / yearly interest in base currency and, when it has them,
  in the instrument and intermediary instrument. A totals card on top sums the **base-currency** interest of all
  accounts. With no Earn account it shows a hint on how to set one.

## Code map

- `EarnItemBuilder` (pure) builds `EarnState(items, totals)` from accounts, balances and instruments.
- `EarnViewModel` merges the accounts, balances and instruments LiveData (like `AccountsViewModel`), so the screen
  stays current; `EarnAdapter` renders the totals card and one card per account; `EarnActivity` hosts them.
- `TransactionDisplay.formatApr` / `formatAprPercent` format a rate without trailing zeros ("5.5", "12", "5.5%").

## Tests

`YieldCalculatorTest`, `EarnItemBuilderTest`, `EarnViewModelTest`, `EarnAdapterTest`, `EarnActivityTest`,
`AccountDaoTest` (APR round trip), `Migration17To18Test` (the new column matches Room's schema),
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
