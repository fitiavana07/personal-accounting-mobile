# Accounting

Accounting app with features like accounts, balances, transactions, reports.

Build and test. Make sure to run it after every change. Remember to use TDD for
every change: RED → GREEN → REFACTOR.

```
./gradlew assembleDebug testDebugUnitTest
```

## Target Platform

This is an Android project targeting **API 19 (KitKat)**. Avoid using APIs
above API 19 without adding appropriate compatibility notes or checks.

## General

- **Language**: Kotlin.
- **Build system**: Gradle, with Kotlin DSL `build.gradle.kts`

Build variants: `debug` (applicationIdSuffix `.dev`, includes clear-data menu)
and `release`. Both can be installed side-by-side.

The package is dev.fitiavana.accounting.

## Architecture

- **UI:** Android Views (XML layouts) — Jetpack Compose requires API 21+ and is
  not compatible with the API 19 target.
- **Architecture pattern:** MVVM (ViewModel + LiveData) — current Android best
  practice for View-based apps.
- **Local storage:** Room (SQLite ORM).

No dependency injection framework. Each ViewModel has a manual ViewModelFactory
that receives Repository instances from the Activity/Fragment.

**Layering rule: Activity/Fragment → ViewModel → Repository → DAO.**
Activities and Fragments must talk to a ViewModel only — never call a
Repository or DAO directly. A ViewModel may depend on multiple Repositories, a
Repository may depend on multiple DAOs, but a DAO must never depend on a
Repository and a Repository must never be called directly from the UI layer. If
a ViewModel is missing a method you need, add it to the ViewModel (which
delegates to the Repository) rather than reaching past it from the UI layer.

RoomDatabase singleton in
app/src/main/java/dev/fitiavana/accounting/db/AppDatabase.kt

### Package structure (by feature)

```
features/
  accounts/       # Account (@Entity), AccountDao, AccountRepository, AccountTypes, LiquidityLevels
  balances/       # AccountBalance (@Entity), AccountBalanceDao, BalanceRepository, BalanceCalculator, GainLossCalculator, YieldCalculator
  transactions/   # Transaction/TransactionEntry (@Entity), TransactionDao, TransactionRepository, TransactionWithEntries
  instruments/    # Instrument (@Entity), InstrumentDao, InstrumentRepository
  exchangerates/  # ExchangeRateCache (@Entity), ExchangeRateCacheDao, ExchangeRateRepository
  reports/        # AccountLines, ReportRow, BalanceSheetBuilder, IncomeStatementBuilder, EquityStatementBuilder
  cexprices/      # CexPrice, CexPriceRepository
  p2pprices/      # P2pPrices, P2pPriceRepository (Binance P2P USDT/MGA top ads on home)
  alerts/         # GainLossAlert/AlertState (@Entity), AlertDao, AlertRepository, AlertEvaluator, AlertCheckManager, hourly alarm + notifier
  settings/       # AppSettings (@Entity), AppSettingsDao, AppSettingsRepository
  backup/         # BackupRepository, AutoBackupManager (daily backups)
  cloudbackup/    # CloudBackupManager/Repository, BackupEnvelope (.accbak), SafCloudFolder (Drive via SAF), CloudBackupStore
  crypto/         # AesGcm, pbkdf2Sha256 (shared by P2P sync and Cloud Backup)
  templates/      # TransactionTemplate/TemplateEntry (@Entity), TemplateDao, TemplateRepository
  sync/           # P2P sync over Wi-Fi: ChangeTracker (stamps), SyncRepository (merge), SyncCoordinator, discovery/session/crypto
  changelog/      # ChangelogParser, ChangelogRepository, AssetChangelogSource (reads the bundled CHANGELOG.md)
db/               # AppDatabase singleton (Room, migrations)
network/          # HTTP clients for exchange-rate providers (CoinGecko, Yahoo Finance)
  p2p/            # Binance P2P ad search (BinanceP2pApi, P2pPriceFetcher)
  cex/            # per-exchange ticker endpoints/parsers (Binance, Bybit, Bitget, OKX, KuCoin, MEXC, Kraken)
ui/
  accounts/      # AccountsFragment, AccountsViewModel, AccountsAdapter, EditAccountActivity
  balances/      # BalancesFragment, BalancesViewModel, BalancesAdapter
  transactions/  # TransactionsFragment, AddTransactionActivity, TransactionDetailActivity
  instruments/   # InstrumentsFragment, InstrumentsViewModel
  reports/       # ReportsFragment, ReportsViewModel, ReportPeriodSelector
  home/          # HomeFragment, HomeViewModel (dashboard/metrics)
  earn/          # EarnActivity, EarnViewModel, EarnAdapter (interest on accounts with an APR)
  alerts/        # AlertsActivity, AlertsViewModel, AlertsAdapter, EditAlertDialog (gain/loss % alerts)
  sync/          # SyncActivity, SyncViewModel, SyncPeersAdapter (passphrase, auto-sync, devices found)
  cloudbackup/   # CloudBackupActivity, CloudBackupViewModel (Drive folder, passphrase, daily backup, restore)
  changelog/     # ChangelogActivity, ChangelogViewModel, ChangelogAdapter (Release notes screen)
  common/        # shared presenters/adapters (UiUtils, ReportPresenter, TransactionDisplay)
```

Each feature under `features/<name>/` colocates its Room `@Entity`, `@Dao`, and
`Repository` together (rather than splitting them across `data/model/`,
`data/dao/`, `data/repository/`) — feature-cohesive, fewer cross-package jumps
to touch one feature. The `ui/<name>/` layering rule still applies:
UI code only talks to a `features/<name>/*Repository` through a ViewModel,
never to a `*Dao` directly.

Account ID is a UUID stored as `String`; generate with
`UUID.randomUUID().toString()`.

Features: Accounts, Instruments, Transactions, Balances, Reports

See `docs/guardrails.md` for the rules enforced by hooks, tests and compiler
settings (commit messages, version bumps, layering, build before finishing),
what blocks what, and how to get unblocked. On a fresh clone run
`git config core.hooksPath .githooks`.

See `docs/reports-feature.md` for how the Reports feature works (period
selection, ViewModel data flow, report builders) before exploring its code.

See `docs/instrument-transfer-base-amount.md` for how the base currency amount
is calculated in the Transfer transaction creation mode (which also covers
plain Ar transfers; the mode follows the From account).

See `docs/cex-prices-feature.md` for the CEX pair price comparison feature
(home shortcut, spinners, per-exchange fetchers).

See `docs/p2p-prices-feature.md` for the Binance P2P prices block on the home
screen.

See `docs/auto-backup-feature.md` for the automatic daily backup (AlarmManager,
receivers, where files go) and how to test it.

See `docs/cloud-backup-feature.md` for Cloud Backup (encrypted `.accbak` daily
backups to a Google Drive folder through the Storage Access Framework, own
passphrase, retention of 7, offline/failure handling, restore). Android 6.0+
only. It is isolated from the manual backup, the auto-backup and P2P sync, which
stay unencrypted.

See `docs/transaction-templates-feature.md` for transaction templates (saved
mode + accounts for Add Transaction): data model, slots, and how templates are
applied per mode.

See `docs/accounts-search.md` for the Accounts screen name search (scoped to
the selected type tab, combined with the hide-zero-balance toggle).

See `docs/earn-feature.md` for Earn (an APR on asset accounts, projected
interest, the Earn screen and the Balance Sheet APR row).

See `docs/gain-loss-alerts-feature.md` for gain/loss % alerts (per-account notifications
when the Home gain/loss crosses a threshold: crossing semantics with re-arming,
the hourly alarm, the check after the Home rate fetch, device-local state, sync and
backup).

See `docs/changelog-feature.md` for `CHANGELOG.md` (Keep a Changelog, one section
added per release by `scripts/release.sh`) and the Release notes screen that
shows it.

See `docs/p2p-sync.md` for peer-to-peer sync over the local Wi-Fi (what syncs,
stamps and tombstones, merge and repair, the protocol, and why debug and
release builds never see each other). **Every write to a synced table (
instruments, accounts, transactions + entries, templates + entries, the
settings row) must go through its repository inside
`ChangeTracker.inTransaction`, so it is stamped.**

Each CRUD feature: {Feature}Fragment + {Feature}ViewModel + {Feature}Adapter +
Edit{Feature}Activity + Edit{Feature}ViewModel

Transaction amounts stored as integers

## Testing

- **JUnit**: Unit testing framework
- **Mockito**: Mocking library for unit tests
- **Robolectric**: Runs Android framework code (layout inflation, resources,
  `Context`, Room via `androidx.room.testing`) on the JVM as unit tests, no
  emulator needed. Use `@RunWith(RobolectricTestRunner::class)` for tests that
  touch Android APIs (adapters binding views, DAOs, controllers depending on
  `Context`/resources); plain JUnit/Mockito is enough when the code under test
  has no Android framework dependency. `testOptions.unitTests` in
  `app/build.gradle.kts` sets `isIncludeAndroidResources = true` (so resources
  resolve under Robolectric) and `isReturnDefaultValues = true` (so
  non-Robolectric unit tests calling stray Android APIs don't crash).
- **TDD is mandatory**: for every change (new feature, bug fix, refactor),
  write a failing test first, verify it actually fails by running the test
  command, then write the minimum code to make it pass, then refactor. Never
  write production code before there is a test that requires it. When doing the
  RED check for the tests, only run the new/changed tests instead of
  everything, because some other agents may be adding new tests. Because
  running all the tests at once may be long, run the green CHECK in two steps:
  (1) running the new/changed tests only, (2) running all the tests. That way,
  we catch failing tests early.
- **Add a Robolectric test whenever it's relevant**, not just Mockito-mocked
  coverage: any new or changed `@Dao` interface with a non-trivial `@Query`
  (`WHERE`/`JOIN`/date-range filter/ordering/aggregation) needs its own
  `androidx.room.testing`-backed `@RunWith(RobolectricTestRunner::class)`
  test hitting a real in-memory Room database — a mocked-repository test in a
  ViewModel test cannot catch a broken `@Query`. The same applies to other
  Android-framework-dependent code (adapters binding views, anything reading
  resources/`Context`). See `features/accounts/AccountDaoTest.kt` or
  `features/transactions/TransactionDaoTest.kt` for the pattern.

## UI

- Minimalist UI design. Simple colors
- Focus on functionality, more than fancy visuals
- Respect basic UI/UX principles, such as colors on primary vs. non-primary
  actions, colors on destructive actions

## Code

- Avoid using `@Deprecated` methods when possible, use recommended replacement
  instead.
- Never include Co-Authored-By in commits
- DRY: Reuse code when possible, refactor if needed
- To display a base-currency amount (prefixed "Ar", thousands-separated, no
  parentheses), always use `UiUtils.formatAmountAr(context, amount)` — never
  duplicate
  `getString(R.string.amount_ar, TransactionDisplay.formatAmount(...))`
  inline. This requires a `Context`, so it's for UI-layer code (Activities,
  Fragments, Adapters) only. For report rows needing contra/parenthesized
  formatting (e.g. Balance Sheet, Income Statement), use `ReportPresenter`'s
  amount formatting instead, which has no `Context` dependency.

## Commits

### Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`,
`perf`. Use `!` after type/scope (e.g. `feat!:`) for breaking changes.

For a `fix` (and any non-trivial change), add a body after a blank line that
describes the bug (symptom, and the root cause when known) and the solution, so
the history explains why the change was made. Wrap the body at about 80 columns.

### Version bumps

Never edit `versionCode`/`versionName` in `app/build.gradle.kts` (a hook blocks
it). The user releases with `scripts/release.sh`, which bumps them, commits
`chore: bump version to <versionName> (<versionCode>)` and creates the
annotated tag `v<versionName>`.

## On new features

After every change, generate a testing checklist for the user to test manually

## When adding new libraries

When adding a new library (in build.gradle.kts / libs.versions.toml), always
verify the app can be built successfully before making changes to source code.