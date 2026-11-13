# Swap transaction mode

Fourth mode of *New transaction*: buy an amount of T using an amount of F, carrying the cost
basis into T's intermediary balance. There are two kinds, chosen by the From account:
an instrument swap (below), a [base-currency swap](#swap-from-a-base-currency-account) and a
[sell](#sell-an-asset-back-into-its-intermediary-instrument).

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
(`+ <I_T amount> · <I_F amount> · <Ar> Ar`). Toggling off clears the fee inputs. Templates store the fee's **expense account** (never the amount), see `docs/transaction-templates-feature.md`.

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

## Sell an asset back into its intermediary instrument

The reverse of a buy. F holds an instrument I_F **with** an intermediary instrument II_F (its cost
basis, e.g. BTC bought with USDT) and is sold into T, an asset whose instrument is II_F and that has
**no** intermediary (`SwapBuilder.isSellFrom`; T is never an account of I_F). From amount is in I_F,
To amount in I_T (= II_F).

F's position is released in proportion to the amount sold (`SwapBuilder.computeSell`, same rounding as
Transfer): base `B = fromAmount * F.balance / F.instrumentBalance` and cost basis
`C = fromAmount * F.intermediaryBalance / F.instrumentBalance` (II_F units). Receiving exactly `C` is a
sale at cost. The difference `D = toAmount - C` is a realized **gain** (> 0) or **loss** (< 0):

- `G = |D| * B / C` in base only (the cost basis' own rate), always positive.
- F: `creditAmount = B`, `instrumentCreditAmount = fromAmount`, `intermediaryCreditAmount = C`.
- T: `debitAmount = B ± G` (+ gain, - loss, so T's II_F is carried at F's cost rate),
  `instrumentDebitAmount = toAmount`, no intermediary amount.
- Gain: a **gain account** entry `creditAmount = G`; loss: a **loss account** entry `debitAmount = G`
  (base only, as these accounts are always in base currency).

When `D != 0` a section shows "Gain/Loss ≈ Ar G" and an account spinner offering gain accounts for a
gain or loss accounts for a loss (re-offered when the sign flips). An account is required to save
(`error_swap_gain_account_required` / `error_swap_loss_account_required`). The section is hidden when
`D == 0`. The account is not stored in templates.

Optional swap fee, deducted from the To amount like a buy: typed in I_T, in (0, toAmount), with an
expense account. `feeBase = fee * B / C` (`SwapBuilder.sellFeeBase`); T receives `toAmount - fee` with
`debitAmount = B ± G - feeBase`, plus an expense entry `debitAmount = feeBase`. The gain/loss is computed
on the gross `toAmount` (the fee is a separate expense). There is no secondary fee in a sell. The swap
fee switch is off by default. ALL is available. No rate (no I_F or II_F balance) blocks saving.

## No overdraw

Saving is blocked (toast `error_swap_insufficient_balance`) when the swap would take From below
zero: the base value spent plus the secondary fee exceeds From's base balance, or, for an
instrument From, the amount exceeds its instrument balance. Spending exactly the balance is allowed.
The From "new balance" preview uses the same rule (`overdrawsFrom`) and shows its main amount in
the loss red instead of the usual gold while the swap would overdraw From.

## Templates

`TemplateModes.SWAP` with `from`/`to` slots (a sell works the same way), same behaviour as Transfer templates. A template may
start from a base-currency From; applying it applies the base-currency defaults above.
