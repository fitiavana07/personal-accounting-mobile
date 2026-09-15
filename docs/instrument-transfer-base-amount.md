# Instrument Transfer: base currency amount calculation

Package: `dev.fitiavana.accounting.ui.transactions`

## Formula

`InstrumentTransferBuilder.computeBaseAmount()` derives an implicit exchange
rate from the **From account's current base balance ÷ its current
instrument balance**, then multiplies it by the instrument amount entered:

```
baseAmount = instrumentAmount × (fromBalance / fromInstrumentBalance)
```

If the From account's instrument balance is zero or negative, no rate can
be derived and the function returns `null`.

Source:
`app/src/main/java/dev/fitiavana/accounting/ui/transactions/InstrumentTransferBuilder.kt:47-55`

```kotlin
fun computeBaseAmount(
    instrumentAmount: Long,
    fromBalance: Long,
    fromInstrumentBalance: Long
): Long? {
    if (fromInstrumentBalance <= 0L) return null
    return (instrumentAmount.toDouble() * fromBalance.toDouble() / fromInstrumentBalance.toDouble())
        .roundToLong()
}
```

## Callers

`InstrumentTransferController` (`ui/transactions/InstrumentTransferController.kt`)
calls this in two places, always passing the **From** side's `balance` and
`instrumentBalance`:

- `computedBaseAmount()` (lines 163-168) — used live, while typing, to
  drive both the "new balance" preview (`updateNewBalances`) and the
  base-amount preview line (`updateAmountBasePreview`):

  ```kotlin
  private fun computedBaseAmount(instrument: Instrument): Long? =
      InstrumentTransferBuilder.computeBaseAmount(
          instrumentAmount = parsedInstrumentAmount(instrument),
          fromBalance = from.balance,
          fromInstrumentBalance = from.instrumentBalance
      )
  ```

- `collectEntries()` (lines 277-281) — called again at save time to compute
  the final `baseAmount` used to build the transaction's two entries via
  `InstrumentTransferBuilder.buildEntries`:

  ```kotlin
  val baseAmount = InstrumentTransferBuilder.computeBaseAmount(
      instrumentAmount = instrumentAmount,
      fromBalance = from.balance,
      fromInstrumentBalance = from.instrumentBalance
  )
  ```

## Notes

- The rate is always taken from the **From** account, never the To account
  — this matters for instruments where different accounts might otherwise
  imply different rates (e.g. rounding drift across accounts holding the
  same instrument).
- `fromBalance` and `fromInstrumentBalance` are the account's balances
  *before* the transfer is applied (loaded by
  `InstrumentTransferController.loadBalance`), not the projected new
  balances.
- A `null` result (no rate available) surfaces to the user as
  `error_instrument_transfer_no_rate` in `collectEntries()`, and simply
  hides the base-amount preview line in `updateAmountBasePreview()`.
