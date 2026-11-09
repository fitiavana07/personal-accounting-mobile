# Swap transaction mode

Fourth mode of *New transaction*: buy an amount of T using an amount of F, carrying the cost
basis into T's intermediary balance. There are two kinds, chosen by the From account:
an instrument swap (below) and a [base-currency swap](#swap-from-a-base-currency-account).

## Eligible accounts (`SwapBuilder`)

- **From (F):** an asset with non-zero base balance and at least one eligible To, that is either
  - an instrument asset: an instrument I_F and **no** intermediary instrument, or
  - a base-currency asset (`SwapBuilder.isBaseFrom`): no instrument and no intermediary.
- **To (T), instrument swap:** asset with an instrument I_T != I_F and intermediary instrument
  == I_F. The To spinner depends on the selected From.
- **To (T), base-currency swap:** asset with an instrument and **no** intermediary instrument.

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
`FeeAccounts.selectable` for the expense spinner (and the fee section UI, `FeeSection`).

## Swap from a base-currency account

F is a base-currency asset, so the swap buys an asset with base currency and T's cost basis is
the base amount itself (T needs no intermediary instrument).

Inputs: From amount `A` (whole base currency, prefix "Ar"), To amount `U` (I_T, its
`decimalPlaces`). There is no rate lookup, no zero-balance error and no "≈ Ar" line (the amount is
already in Ar). The **ALL** button is hidden for a base From (the secondary fee is charged on top,
so ALL would overdraw); it comes back for an instrument From.

Entries (`SwapBuilder.buildBaseEntries`):

- F: `creditAmount = A + S` (S = secondary fee, 0 without one), no instrument fields.
- T: `debitAmount = A - feeBase`, `instrumentDebitAmount = U - fee`, **no** intermediary amount.
- Swap fee expense (if any): `debitAmount = feeBase`. Secondary fee expense (if any):
  `debitAmount = S`.

Swap fee: as above, in I_T and deducted from what T receives, but F has rate 1 so
`feeBase = fee * A / U` (`SwapBuilder.computeBaseSwapFee`). It must be in (0, U).

Secondary fee: a second switch (shown only for a base From) with an amount typed in **base
currency** and its own expense account. It is not deducted from T: it is *added* to the amount F
pays (F credit = `A + S`, T unchanged). A blank/zero amount means no fee; an amount > 0 needs an
account.

Defaults: when From becomes a base-currency account, **both** switches turn on; when it becomes an
instrument account, both turn off (the secondary fee is hidden). Moving between accounts of the
same kind keeps what the user set. The secondary fee's account spinner never offers the swap fee's
account (and picking the swap fee's account resets a clashing secondary choice), so one expense
account is never debited twice in one transaction.

UI plumbing: each fee is a `FeeSection` (toggle + amount + expense account), shared with the
Transfer mode.

## No overdraw

Saving is blocked (toast `error_swap_insufficient_balance`) when the swap would take From below
zero: the base value spent plus the secondary fee exceeds From's base balance, or, for an
instrument From, the amount exceeds its instrument balance. Spending exactly the balance is allowed.
The From "new balance" preview uses the same rule (`overdrawsFrom`) and shows its main amount in
the loss red instead of the usual gold while the swap would overdraw From.

## Templates

`TemplateModes.SWAP` with `from`/`to` slots, same behaviour as Transfer templates. A template may
start from a base-currency From; applying it applies the base-currency defaults above.
