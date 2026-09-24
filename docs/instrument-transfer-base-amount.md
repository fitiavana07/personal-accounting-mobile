# Instrument Transfer: base currency amount calculation

Package: `dev.fitiavana.accounting.ui.transactions`

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

This function is shared by both `InstrumentTransferController` and
`InstrumentIncomeController` — each just passes in its own side's
`balance`/`instrumentBalance` (From, for Transfer; asset, for Income).

## Callers

`InstrumentTransferController` (`ui/transactions/InstrumentTransferController.kt`)
calls this in two places, always passing the **From** side's `balance` and
`instrumentBalance`:

- `computedBaseAmount()` — used live, while typing, to drive both the "new
  balance" preview (`updateNewBalances`) and the base-amount preview line
  (`updateAmountBasePreview`):

  ```kotlin
  private fun computedBaseAmount(instrument: Instrument): Long? =
      InstrumentValueCalculator.computeBaseAmount(
          instrumentAmount = parsedInstrumentAmount(instrument),
          balance = from.balance,
          instrumentBalance = from.instrumentBalance
      )
  ```

- `collectEntries()` — called again at save time to compute the final
  `baseAmount` used to build the transaction's two entries via
  `InstrumentTransferBuilder.buildEntries`:

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
- `InstrumentTransferController` and `InstrumentIncomeController` share
  their account-side state, spinner wiring, and balance-loading plumbing
  (including the "a newer selection may have won the race" guard) via
  `AccountSideController` (`ui/transactions/AccountSideController.kt`).
  Each controller keeps its own amount-parsing formula, new-balance
  preview formatting, and validation messages, which differ between the
  two modes.
