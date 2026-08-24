---
title: 'refactor: re-organize directories'
status: todo
order: k
---

`ui` sub-directories should only contain ui-directly-related files such as
*Adapter, *Fragment, *ViewModel, *ViewModelFactory.

Other domain-focused logic should be moved to feature-based directories
under a `features` directory.

Also move *Dao, model, *Repository files to this new `features` directory, to
appropriate feature-based directory.

Suggested directory structure:

```text
dev.fitiavana.accounting:
    features/            # per-feature, not related to ui: DAO, Entity, Repository, calculators
        accounts/
        backup/
        balances/        # includes AccountBalance, AccountBalanceDao
        exchangerates/
        instruments/
        reports/         # includes BalanceSheetBuilder and a separate, extracted
                          # IncomeStatementBuilder (currently a method inside
                          # ReportsViewModel.buildIncomeStatement)
        transactions/
    db/                  # singleton
    network/             # current data/network/* moves here unchanged
    ui/                  # per screen, related to ui: Adapter, Fragment, ViewModel, etc. (can be just the current ui package)
       home/
       common/           # common code across screens (currently just UiUtils.kt)
    MainActivity.kt
    AccountingApplication.kt
```

Notes on classification (where a file doesn't obviously fit `*Adapter`/`*Fragment`/`*ViewModel`/`*ViewModelFactory`/`*Dao`/`*Repository`):

- **Hard rule: `features/` must never import from `ui/`.** Dependencies flow one way — `ui/` depends on
  `features/`, never the reverse. A class cannot move into `features/` while it still references a
  `ui/*` type (Android color `Int`s from a UI palette, formatted display strings, etc.). If a class
  currently mixes calculation with UI-flavored output, it must be split before/while moving — see below.
- **Calculators go in `features/`, formatters go in `ui/`.** This is the operative split for every
  mixed-concern class in this refactor (see call-outs below): pure calculation (grouping, summing,
  computing gain/loss, etc.) belongs in `features/`; turning numbers into display strings, currency
  prefixes, parentheses for negatives, or picking colors belongs in `ui/`.
  - `BalanceCalculator`: already pure calculation, moves to `features/` as-is.
  - `GainLossCalculator` (currently `ui/home/`): split it. Keep `computeGainLoss`,
    `computeCurrentValue`, `computeGainLossPercent` (pure calculation) in a `features/`-based calculator
    (e.g. `features/balances/GainLossCalculator.kt`, since gain/loss is a balances concept). Move
    `formatSignedAmount`, `formatSignedAmountAr`, `formatSignedPercent` (display formatting, currently
    calling into `TransactionDisplay`) into a `ui/` formatter — either merged into `ui/common/` or kept
    as a small `ui/home/` formatter if only Home/HomeDetail ever call them.
  - `CompactNumberFormatter`: already a pure formatter, stays in `ui/` (e.g. `ui/home/`, since it's
    Home-specific).
- **"Builder" classes go in `ui/`** if their purpose is specifically to build display items for one
  screen (e.g. `BalanceItemBuilder`, `HomeItemBuilder`). `home` is a UI screen, not a feature — its
  builders that only assemble display items stay under `ui/home/`.
  - Exception: `BalanceSheetBuilder` is about the reports domain (Balance Sheet report), so it should be
    moved and refactored into `features/reports/`, and reused/refactored for reuse from the reports
    screen — **conditioned on satisfying the hard rule above.** As it stands today, `BalanceSheetBuilder`
    is itself a mixed-concern class and cannot move wholesale:
    - It imports `ui.home.AssetPalette` (returns Android color `Int`s) and `ui.transactions.TransactionDisplay`
      (currency-string formatting), and its private `formatAr`/`formatArParens`/`formatPlain`/
      `formatPlainParens` helpers bake in the "Ar" prefix and parenthesization — all `ui/` concerns.
    - Before/while moving: `BalanceSheetBuilder` in `features/reports/` should return rows with raw
      `Long` amounts (and no color), not formatted strings or `AssetPalette` colors. A `ui/reports/`
      (or `ui/home/`, for the screens that render it) presentation step formats those amounts into
      `AccountLine`/`TotalLine` display strings and assigns colors via `AssetPalette`. This may mean
      `BalanceSheetRow` itself needs to become a raw-amount domain model in `features/reports/`, with a
      separate UI-side row type (or a formatting pass over the same rows) producing the display strings
      — decide the exact shape during implementation, but the raw-vs-formatted boundary must exist.
    - `assetSlices()` / `AssetSlice` is only consumed by `HomeViewModel` for the pie chart — it's a
      Home-screen-only concern, not part of the Balance Sheet/Income Statement report domain. Split it
      out of `BalanceSheetBuilder` and keep it in `ui/home/` (or a small `features/balances/` calculator
      if the slice amounts themselves are considered domain calculation) rather than carrying it into
      `features/reports/`.
    - After the split, `ui/home/HomeViewModel` (which currently calls `BalanceSheetBuilder.build` and
      `.assetSlices`) will depend on `features/reports/` for the raw report data — that's the expected,
      correct direction (ui → features) and should be treated as part of this move, not a side effect
      discovered later.
- **Extract and clearly separate classes for income statement and balance sheet.** Currently
  `BalanceSheetBuilder` has a `buildIncomeStatement` method that should be extracted into its own class
  (e.g. `IncomeStatementBuilder`) and moved to `features/reports/` alongside `BalanceSheetBuilder`,
  subject to the same raw-amount/no-formatting constraint described above.
- **Shared formatters move to `ui/common/`.** `TransactionDisplay` (currently `ui/transactions/`) is
  used well beyond the transactions screen — `transactiondetail`, `addtransaction`, `home`, `homedetail`,
  `balances`, `transactions` all call into it. Leaving it under `ui/transactions/` would make every other
  `ui/<feature>` package depend on an arbitrary sibling screen's package. Move it to `ui/common/`
  alongside `UiUtils.kt` as part of this refactor, and update all call sites' imports.

Test sources (`app/src/test/java/dev/fitiavana/accounting/`) are currently all flat in one package and
do not mirror the main source layout at all. They should move alongside the classes under test into
matching `features/*` or `ui/*` test packages.

## Suggested sequencing

Given the number of files and import updates involved, do this incrementally rather than as one large
move, running `./gradlew assembleDebug testDebugUnitTest` after each step:

1. Move `TransactionDisplay` to `ui/common/` and update its call sites (mechanical, low risk, touches
   many files but no logic changes).
2. Split `GainLossCalculator` into a `features/balances/` calculator and a `ui/` formatter; update call
   sites.
3. Split `BalanceSheetBuilder`: extract `assetSlices`/`AssetSlice` into `ui/home/`; extract
   `buildIncomeStatement` into its own `IncomeStatementBuilder`; make the remaining
   `build`/`buildMonthly`/`buildIncomeStatement` return raw amounts with no `ui/` dependency; add the
   `ui/`-side formatting/coloring step in `ui/home/` and `ui/reports/`. Move the resulting pure builders
   to `features/reports/`.
4. Move the remaining `*Dao`/model/`*Repository`/calculator files into their `features/<name>/`
   directories one feature at a time (accounts, instruments, balances, exchangerates, backup,
   transactions), updating imports (including in `db/AppDatabase.kt`) as each feature moves.
5. Reorganize test sources to mirror the new `features/*`/`ui/*` layout, matching each move above.