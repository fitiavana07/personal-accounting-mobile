# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions are numbered by `scripts/release.sh`.

## [Unreleased]

## [1.55] - 2026-10-23

### Added

- alerts: notify when an account's gain/loss % crosses a threshold

## [1.54] - 2026-10-22

### Added

- apr: edit an account's APR from the Balance Sheet and Earn screens

## [1.53] - 2026-10-21

### Added

- home: redesign the gain/loss block with a summary and clearer rows

### Changed

- home: move gain/loss list above the P2P prices

### Fixed

- home: hide crypto gain/loss rows without a coingeckoId

## [1.52] - 2026-10-19

### Added

- backup: encrypted daily Cloud Backup to a Google Drive folder

### Changed

- backup: share the restore confirmation dialog

## [1.51] - 2026-10-18

### Added

- transactions: emphasize income amounts in Instrument Income

## [1.50] - 2026-10-18

### Added

- accounts: pre-associate a revenue account with an asset account
- backup: restore backups made by an older schema

## [1.49] - 2026-10-18

### Added

- transactions: hide zero-balance assets in Instrument Income
- release: add make changelog to sync and commit Unreleased entries

### Changed

- sync: show "Sync" in the overflow menu with one shared string

## [1.48] - 2026-10-18

### Fixed

- build: run copyChangelogAsset before lint tasks

## [1.47] - 2026-10-18

### Added

- release: print a numbered message for each release step

## [1.46] - 2026-10-18

### Added

- transactions: merge Simple and Instrument Transfer into one Transfer mode
- ui: move add-transaction button to the center of the bottom navigation bar
- accounts: record instrument income on the intermediary instrument per account
- release: generate CHANGELOG.md sections from commits on release, add one-off backfill script
- changelog: add release notes screen from the bundled CHANGELOG.md

### Changed

- templates: drop fallbacks for the old transfer template modes

## [1.45] - 2026-10-18

### Added

- transactions: support intermediary instrument accounts in Instrument Transfer
- transactions: filter From/To spinners in Simple and Instrument Transfer
- transactions: add ALL button to Instrument Transfer amount
- transactions: show instrument amount before base in instrument balance lines

### Fixed

- transactions: keep balance amounts and Ar together with a non-breaking space

## [1.44] - 2026-10-14

### Fixed

- network: trust DigiCert Global Root G2 on API 19-20 to fix Yahoo quotes
- network: use Conscrypt on API 19-20 so Binance P2P TLS (AES-GCM) works

## [1.43] - 2026-10-14

### Fixed

- sync: use Bouncy Castle GCM to fix encryption crash on API 19
- sync: avoid Map.merge to fix crash recording sync result on API 19

## [1.42] - 2026-10-14

### Fixed

- sync: avoid ConcurrentHashMap.newKeySet to fix launch crash on API 19

## [1.41] - 2026-10-14

### Added

- sync: add pure sync core (stamps, merge, discovery packet, peer registry)
- sync: add passphrase-derived AES-GCM encryption with per-flavor namespace
- sync: stamp every ledger write and add sync_meta storage (DB v20)
- sync: add snapshot, JSON, consistency rules and repair
- sync: merge peer snapshots with repair and balance recalculation
- sync: add UDP discovery, encrypted TCP sessions and Wi-Fi permissions
- sync: run sync while the app is on screen (coordinator, foreground tracking)
- sync: add Sync screen (passphrase, auto-sync, devices) with menu entry
- sync: add show/hide eye toggle to the passphrase input

### Changed

- backup: share row JSON converters so sync can reuse them

### Fixed

- ui: pad screen content above the navigation bar so Save is not hidden

## [1.40] - 2026-10-14

### Added

- ui: add live thousands separators to amount inputs

## [1.39] - 2026-10-11

### Added

- accounts: add name search to accounts screen

### Fixed

- layouts: use app:tint and app:srcCompat so icons inflate on API 19
- reports: use ImageViewCompat for icon tint so Reports opens on API 19
- reports: use chevron glyphs available in API 19 system font

## [1.38] - 2026-10-11

### Added

- transactions: support intermediary instrument accounts in Instrument Income
- earn: hide accounts with a zero base balance from the Earn screen

### Fixed

- reports: refresh reports when returning from Add Transaction
- transactions: keep instrument amounts and codes on one line in balance previews

## [1.37] - 2026-10-08

### Changed

- Maintenance and internal changes.

## [1.36] - 2026-10-08

### Changed

- earn: declutter the Earn screen with a hero total, period columns and a share bar

### Fixed

- reports: reserve trend charts space and show a loading spinner

## [1.35] - 2026-10-08

### Added

- reports: add new-transaction FAB and reuse its icon on transactions screen
- home: show progress targets on the percent line in emergency fund and income blocks
- home: move monthly expenses editing to a Mo. Expenses shortcut
- reports: add net worth and net income trend charts
- backup: add automatic daily backups via AlarmManager
- transactions: add transaction templates for mode and accounts
- earn: add APR on asset accounts, projected interest and an Earn screen

### Fixed

- db: add the account APR in migration 18 to 19 instead of editing 17 to 18

## [1.34] - 2026-10-06

### Added

- cex-prices: replace refresh button with pull-to-refresh
- reports: scroll selected period tab fully into view

## [1.33] - 2026-10-01

### Added

- home: combine metrics into compact side-by-side rows
- home: show Binance P2P USDT/MGA top buy and sell ads with payment method filter
- home: regroup metrics by theme, add avg income and shorten labels

## [1.32] - 2026-10-01

### Added

- transactions: add ALL button to fill simple transfer amount with From balance

## [1.31] - 2026-10-01

### Added

- accounts: disallow instruments on income statement and drawing accounts
- reports: tap-to-expand native balances on the Balance Sheet
- network: add per-exchange CEX ticker fetchers
- cexprices: add CexPriceRepository and crypto instrument query
- home: add CEX Prices screen and home shortcut button
- cexprices: remember last pair, darker text and price comparison card

## [1.30] - 2026-09-24

### Changed

- transactions: centralize background/UI-thread lambda pair in AddTransactionActivity
- eliminate Intent-extra !! usages in EditInstrumentActivity/EditAccountActivity

## [1.29] - 2026-09-24

### Added

- transactions: animate mode-card press and wizard step transition
- home: add FAB for direct transaction creation
- transactions: save-success toast and zero-balance transfer guard

## [1.28] - 2026-09-24

### Added

- transactions: replace mode tabs with a two-step selection wizard
- transactions: add per-mode icons to the mode-selection cards
- reports: add icons for each report type

### Changed

- transactions: extract AccountSideController from Instrument mode controllers

## [1.27] - 2026-09-24

### Changed

- Maintenance and internal changes.

## [1.26] - 2026-09-22

### Added

- home: add Income to Expenses metric and progress section

### Changed

- release: read signing credentials from local.properties only

## [1.25] - 2026-09-18

### Added

- transactions: infer Instrument Income amount from a new-balance input

## [1.24] - 2026-09-18

### Added

- transactions: add Instrument Income transaction creation mode

### Changed

- accounts: centralize account type constants in AccountTypes
- transactions: extract InstrumentValueCalculator shared by transfer and income modes

### Fixed

- transactions: guard collectEntries against unset instrumentTransferController

## [1.23] - 2026-09-15

### Added

- ui: add generated PNG icons for bottom navigation

## [1.22] - 2026-09-11

### Added

- reports: add Year tab for full-year reports

## [1.21] - 2026-09-10

### Added

- transactions: add Instrument Transfer transaction mode

### Changed

- transactions: extract EntryRowController and SimpleTransferController from AddTransactionActivity

### Fixed

- backup: include Monthly Expenses setting in export/import

## [1.20] - 2026-09-10

### Added

- transactions: add Simple Transfer entry mode
- transactions: redesign transaction detail screen

## [1.19] - 2026-09-07

### Added

- reports: style Equity statement header and balance rows like other reports

## [1.18] - 2026-09-04

### Added

- reports: add Statement of Changes in Equity report

## [1.17] - 2026-09-02

### Added

- transactions: add live debit/credit balance indicator
- accounts: show balance summary and filters on Accounts screen
- accounts: add liquidity level to asset accounts
- home: add Metrics block with equity, cash, emergency fund and cash-to-equity
- home: add Monthly Expense and Cash Runway metrics to Metrics block
- release: add release script and signing config

### Changed

- reports: clarify ReportsViewModel cached-state field names
- home: make liquidity and assets pie charts scrollable in one row
- home: extract Metrics card row into reusable layout template

### Fixed

- home: sync asset color dots with Assets pie chart slice colors

## [1.16] - 2026-08-27

### Added

- home: add COLORFUL_COLORS palette to asset pie chart
- instruments: move Instruments to AppBar menu as full-screen activity
- reports: add monthly Balance Sheet report
- reports: add monthly Income Statement report
- balances: move Balances access from bottom nav to AppBar menu
- home: show assets-only balance sheet on Home tab
- reports: use current date for the in-progress current month
- home: add emergency fund tracking with editable monthly expenses

### Changed

- ui: move TransactionDisplay to ui/common
- balances,home: split GainLossCalculator into calculator and formatter
- reports,balances: split BalanceSheetBuilder into features/reports and ui/home
- features: move Dao/model/Repository/calculator files into features/<name>/
- test: reorganize test sources to mirror features/ui layout
- network: move data/network to top-level network package
- ui: merge edit/detail subpackages into their parent feature ui packages
- transactions: route balance lookup through BalanceRepository
- introduce AppContainer to centralize repository construction
- reports: rename NamedBalance to NamedAmount
- reports: rename BalanceSheetRow to ReportRow, move display types to ui/common
- reports: rename balance_sheet layouts/colors to report
- reports: sort gain/loss lines, document assetIndex and emphasized
- reports: sort liability/equity/drawing lines descending
- ui: move UiUtils to ui/common
- accounts: extract EditAccountActivity.onCreate into focused methods
- transactions,home: introduce ViewModels for the remaining direct-repository activities
- accounts: split AccountsFragment.onViewCreated and dedupe account type list
- ui: use string resource for Ar currency prefix

### Fixed

- home: use pure white/black for pie chart amount text by theme
- balances: sort balance list alphabetically by account name
- reports: correct Statement of Changes in Equity label wording
- transactions: use AccountRepository instead of direct AccountDao access
- transactions: route instrument lookups through InstrumentRepository
- reports: remove Ar prefix from Income/Expense/Gain/Loss lines in Balance Sheet
- reports: align amount digits between parenthesized and plain amounts
- ui: make add icon visible in dark mode

## [1.13] - 2026-08-06

### Added

- balance-sheet: group other assets and changes in equity
- home: add Total Changes in Equity line to balance sheet
- transactions: validate consistent debit/credit direction across currencies
- add-transaction: confirm discard on exit with unsaved changes
- home: use monospace amounts and drop Ar prefix on balance sheet line items
- home: add asset distribution pie chart with balance sheet color sync
- home: order assets by balance amount decreasing
- home: make asset pie chart fill screen width and label its center
- home: show total assets in the pie chart center and refine value labels
- home: hide gain/loss accounts with balance under 10,000 Ar
- home: track gain/loss for stock accounts via Yahoo Finance
- home: show disclaimer above stock gain/loss entries

### Fixed

- home: render Total Equity as a signed amount instead of parens
- home: pie chart percent labels adapt color to slice brightness

## [1.5] - 2026-07-07

### Changed

- Maintenance and internal changes.

## [1.4] - 2026-07-07

### Changed

- Maintenance and internal changes.

## [1.3] - 2026-07-06

### Changed

- Maintenance and internal changes.

## [0.1.0] - 2026-06-21

### Changed

- Maintenance and internal changes.

[Unreleased]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.55...HEAD
[1.55]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.54...v1.55
[1.54]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.53...v1.54
[1.53]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.52...v1.53
[1.52]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.51...v1.52
[1.51]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.50...v1.51
[1.50]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.49...v1.50
[1.49]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.48...v1.49
[1.48]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.47...v1.48
[1.47]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.46...v1.47
[1.46]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.45...v1.46
[1.45]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.44...v1.45
[1.44]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.43...v1.44
[1.43]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.42...v1.43
[1.42]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.41...v1.42
[1.41]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.40...v1.41
[1.40]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.39...v1.40
[1.39]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.38...v1.39
[1.38]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.37...v1.38
[1.37]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.36...v1.37
[1.36]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.35...v1.36
[1.35]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.34...v1.35
[1.34]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.33...v1.34
[1.33]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.32...v1.33
[1.32]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.31...v1.32
[1.31]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.30...v1.31
[1.30]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.29...v1.30
[1.29]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.28...v1.29
[1.28]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.27...v1.28
[1.27]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.26...v1.27
[1.26]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.25...v1.26
[1.25]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.24...v1.25
[1.24]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.23...v1.24
[1.23]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.22...v1.23
[1.22]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.21...v1.22
[1.21]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.20...v1.21
[1.20]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.19...v1.20
[1.19]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.18...v1.19
[1.18]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.17...v1.18
[1.17]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.16...v1.17
[1.16]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.13...v1.16
[1.13]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.5...v1.13
[1.5]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.4...v1.5
[1.4]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v1.3...v1.4
[1.3]: https://github.com/fitiavana07/personal-accounting-mobile/compare/v0.1.0...v1.3
[0.1.0]: https://github.com/fitiavana07/personal-accounting-mobile/releases/tag/v0.1.0
