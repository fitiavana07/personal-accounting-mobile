# Swap transaction mode

Fourth mode of *New transaction*: buy an amount of T using an amount of F, carrying the cost
basis into T's intermediary balance.

## Eligible accounts (`SwapBuilder`)

- **From (F):** asset with an instrument I_F, **no** intermediary instrument, non-zero base
  balance, and at least one eligible To.
- **To (T):** asset with an instrument I_T != I_F and intermediary instrument == I_F. The To
  spinner depends on the selected From.

## Inputs

From account, To account, From amount (I_F, its `decimalPlaces`), To amount (I_T, its
`decimalPlaces`). Amounts are parsed with `EditText.plainAmount(instrument)`.

## Entries

Base amount `B = InstrumentValueCalculator.computeBaseAmount(fromAmount, F.balance,
F.instrumentBalance)`: F's own prior cost rate, as in Transfer. No rate (zero balance) blocks saving.

- F: `creditAmount = B`, `instrumentCreditAmount = fromAmount`.
- T: `debitAmount = B`, `instrumentDebitAmount = toAmount`, `intermediaryDebitAmount = fromAmount`.

Saved through the normal `AddTransactionViewModel.saveTransaction` path (stamped by
`ChangeTracker`, balances recalculated). No schema change.

## Templates

`TemplateModes.SWAP` with `from`/`to` slots, same behaviour as Transfer templates.
