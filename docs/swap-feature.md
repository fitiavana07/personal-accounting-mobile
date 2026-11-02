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

## Swap fee (optional)

A "Deduct a swap fee" switch (off by default; off = the plain two-entry swap above) reveals a fee
amount (in I_T, its prefix and `decimalPlaces`) and an expense account spinner. The fee is taken
from the amount received (`SwapBuilder.computeFee`):

- `feeFrom = fee * fromAmount / toAmount` (I_F units, the swap's own price, typed amounts).
- `feeBase = computeBaseAmount(feeFrom, F.balance, F.instrumentBalance)` (F's own prior rate).
- F entry unchanged. T: `debitAmount = B - feeBase`, `instrumentDebitAmount = toAmount - fee`,
  `intermediaryDebitAmount = fromAmount - feeFrom`. Expense: `debitAmount = feeBase` (base only).

The fee must be in (0, toAmount) and needs an expense account; a blank/zero fee means no fee.
With a valid fee a line shows what To really gets, in the usual order
(`+ <I_T amount> · <I_F amount> · <Ar> Ar`). Toggling off clears the fee inputs. Templates do not store the fee.

Transfer mode has the same optional fee, see `docs/instrument-transfer-base-amount.md`; both use
`FeeAccounts.selectable` for the expense spinner.

## Templates

`TemplateModes.SWAP` with `from`/`to` slots, same behaviour as Transfer templates.
