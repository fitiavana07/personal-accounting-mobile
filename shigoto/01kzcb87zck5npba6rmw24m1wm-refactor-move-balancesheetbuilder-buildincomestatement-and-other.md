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

- **Calculation logic goes in `features/`** (e.g. `BalanceCalculator`, `GainLossCalculator`).
- **Formatters go in `ui/`** (e.g. `CompactNumberFormatter`), since they produce display strings for a
  specific screen.
- **"Builder" classes go in `ui/`** if their purpose is specifically to build display items for one
  screen (e.g. `BalanceItemBuilder`, `HomeItemBuilder`). `home` is a UI screen, not a feature — its
  builders that only assemble display items stay under `ui/home/`.
  - Exception: `BalanceSheetBuilder` is about the reports domain (Balance Sheet report), so it should
    be moved and refactored into `features/reports/`, and reused/refactored for reuse from the reports
    screen.
- **Extract and clearly separate classes for income statement and balance sheet.** Currently
  `BalanceSheetBuilder` has a `buildIncomeStatement` method that should be extracted into its own class
  (e.g. `IncomeStatementBuilder`) and moved to `features/reports/` alongside `BalanceSheetBuilder`.

Test sources (`app/src/test/java/dev/fitiavana/accounting/`) are currently all flat in one package and
do not mirror the main source layout at all. They should move alongside the classes under test into
matching `features/*` or `ui/*` test packages.