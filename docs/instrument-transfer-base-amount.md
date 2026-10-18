# Transfer mode: instrument transfers and base currency amount calculation

Package: `dev.fitiavana.accounting.ui.transactions`

## One Transfer mode, driven by the From account

Simple Transfer and Instrument Transfer used to be two modes. They are now one
**Transfer** mode (`TransferController`, `TransferBuilder`), and what it does
depends on the selected **From** account:

| From account | To accounts offered | Amount field | Entries |
|---|---|---|---|
| no instrument | other instrument-free assets | whole Ar, label "Ar", no decimals | base only (`buildSimpleEntries`) |
| has an instrument | other assets with the same instrument **and** intermediary instrument | instrument units with decimals, label = instrument code, "≈ … Ar" preview | base + instrument (+ intermediary) (`buildInstrumentEntries`) |

- From lists every asset account with a non-zero base balance; To is empty until
  From is picked and is repopulated whenever From changes (the To selection is
  kept if still offered). To never offers the From account.
- Switching From to an account of another kind (instrument ↔ instrument-free, or
  another instrument) clears the typed amount, since Ar and instrument units
  aren't comparable.
- ALL fills the From base balance (instrument-free) or instrument balance
  (instrument).
- A zero balance shows the error under From (base balance, or instrument balance
  for an instrument account) and blocks saving.
- The rest of this document describes the instrument side of the mode.

## Formula

`InstrumentValueCalculator.computeBaseAmount()` derives an implicit exchange
rate from the **From account's current base balance ÷ its current
instrument balance**, then multiplies it by the instrument amount entered:

```
baseAmount = instrumentAmount × (balance / instrumentBalance)
```

If the From account's instrument balance is zero or negative, no rate can
be derived and the function returns `null`.

Source:
`app/src/main/java/dev/fitiavana/accounting/ui/transactions/InstrumentValueCalculator.kt:19-27`

```kotlin
fun computeBaseAmount(
    instrumentAmount: Long,
    balance: Long,
    instrumentBalance: Long
): Long? {
    if (instrumentBalance <= 0L) return null
    return (instrumentAmount.toDouble() * balance.toDouble() / instrumentBalance.toDouble())
        .roundToLong()
}
```

This function is shared by both `TransferController` and
`InstrumentIncomeController` — each just passes in its own side's
`balance`/`instrumentBalance` (From, for Transfer; asset, for Income).

## Callers

`TransferController` (`ui/transactions/TransferController.kt`)
calls this, for an instrument From account, in two places, always passing the **From** side's `balance` and
`instrumentBalance`:

- `computedBaseAmount()` — used live, while typing, to drive both the "new
  balance" preview (`updateNewBalances`) and the base-amount preview line
  (`updateAmountBasePreview`):

  ```kotlin
  private fun computedBaseAmount(instrument: Instrument?): Long? =
      InstrumentValueCalculator.computeBaseAmount(
          instrumentAmount = parsedAmount(instrument),
          balance = from.balance,
          instrumentBalance = from.instrumentBalance
      )
  ```

- `collectEntries()` — called again at save time to compute the final
  `baseAmount` used to build the transaction's two entries via
  `TransferBuilder.buildInstrumentEntries`:

  ```kotlin
  val baseAmount = InstrumentValueCalculator.computeBaseAmount(
      instrumentAmount = instrumentAmount,
      balance = from.balance,
      instrumentBalance = from.instrumentBalance
  )
  ```

`InstrumentIncomeController` calls the same function analogously, passing
the **asset** side's `balance`/`instrumentBalance` and the inferred
delta-from-typed-new-balance as `instrumentAmount` (see
`InstrumentIncomeController.transactionInstrumentAmount`).

### Income with an intermediary instrument

Income also accepts asset accounts that have an intermediary instrument
(e.g. BTC via USDT). The intermediary amount is never typed: it is derived
with the same function and the same instrument delta, using the asset's
`intermediaryBalance` as the numerator:

```kotlin
val intermediaryAmount = InstrumentValueCalculator.computeBaseAmount(
    instrumentAmount = delta,
    balance = asset.intermediaryBalance,
    instrumentBalance = asset.instrumentBalance
)
```

It is saved as `intermediaryDebitAmount` on the asset entry (omitted when
the account has no intermediary or the result is not positive). The
intermediary balance is added as a second part to the asset's "Balance:"
line (`AccountSideController`), its "New balance:" line, and the "+ …"
delta line below the new balance field.

#### Accounts whose income is on the intermediary instrument

An asset account with an intermediary can set `Account.incomeOnIntermediary`
(checkbox on the Edit Account screen, shown only for an asset account with an
intermediary; saved as false otherwise). For such an account the account
instrument balance never changes, so Income is typed on the **intermediary**
instrument instead: the new-balance field and its code label use the
intermediary instrument, the amount is the typed new balance minus
`intermediaryBalance`, and the base amount is scaled from the intermediary
side:

```kotlin
val baseAmount = InstrumentValueCalculator.computeBaseAmount(
    instrumentAmount = intermediaryDelta,
    balance = asset.balance,
    instrumentBalance = asset.intermediaryBalance
)
```

The asset entry then carries only `debitAmount` and `intermediaryDebitAmount`
(`instrumentDebitAmount` stays null). The "New balance:" line keeps the
unchanged instrument balance and shows the new intermediary balance; the "+ …"
delta line shows `intermediary · Ar`. The flag is part of the account's
backup/sync JSON (`incomeOnIntermediary`, schema version 21).

All these lines (in Income and Transfer) show amounts in the order
**account instrument · intermediary instrument · base (Ar)**, e.g.
`Balance: 1.5 BTC · 90,000 USDT · 400,000 Ar`, since the instrument is what
the user types; the order lives in the string templates only.

### Transfer with an intermediary instrument

An instrument transfer accepts accounts with an intermediary instrument too, but
the To account must have the **same instrument and the same intermediary
instrument** as From (`TransferBuilder.selectableToAccounts`). The
intermediary amount is derived exactly as in Income, from the **From**
account's `intermediaryBalance`, and saved as `intermediaryCreditAmount` on
the From entry and `intermediaryDebitAmount` on the To entry. The balance,
new balance and "≈ … · … Ar" preview lines gain the intermediary part.

### Zero-balance accounts hidden from From

The From spinner hides accounts whose base balance is zero (or has no balance
row). The To spinner is unaffected by balances. Balances come from
`AddTransactionViewModel.loadAccountsAndInstruments()` (`balancesByAccountId`).
A template whose FROM account is empty is therefore reported as unavailable.

### "ALL" button

Next to the Transfer amount field, **ALL** fills the amount with the From
account's whole `instrumentBalance` (formatted with the instrument's decimals;
for an instrument-free From it is the base balance).
The base amount then equals the From account's full base balance. It does
nothing without a From account or with a zero instrument balance.

## Notes

- The rate is always taken from the **From** account (Transfer) or the
  **asset** account (Income), never the other side — this matters for
  instruments where different accounts might otherwise imply different
  rates (e.g. rounding drift across accounts holding the same instrument).
- `balance` and `instrumentBalance` are the account's balances *before* the
  transaction is applied (loaded by `AccountSideController`, the shared
  account-selection/balance-loading helper both controllers compose two
  instances of), not the projected new balances.
- A `null` result (no rate available) surfaces to the user as
  `error_instrument_transfer_no_rate` / `error_instrument_income_no_rate` in
  each controller's `collectEntries()`, and simply hides the base-amount
  preview line in `updateAmountBasePreview()`.
- `TransferController` and `InstrumentIncomeController` share
  their account-side state, spinner wiring, and balance-loading plumbing
  (including the "a newer selection may have won the race" guard) via
  `AccountSideController` (`ui/transactions/AccountSideController.kt`).
  Each controller keeps its own amount-parsing formula, new-balance
  preview formatting, and validation messages, which differ between the
  two modes.
