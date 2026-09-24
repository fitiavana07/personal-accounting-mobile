# Project Review — 2026-09-24

Full-codebase review covering architecture, code structure, code style,
testing, and build/config. ~10,250 LOC across ~130 main-source files,
~45 test files, API 19 target.

**Status update (2026-09-24, later same day):** three of the recommendations
below have already been fixed:
- Medium #2 / Low #4 (`CLAUDE.md` package-structure doc) — fixed in
  `a2f1bb5` (`docs: fix package-structure section to match
  features/<name>/ layout`).
- Medium #2 (DAO-level Robolectric tests) — fixed in `59915a1`
  (`test(dao): add Robolectric integration tests for remaining DAOs`), 36
  new tests across `TransactionDaoTest`, `AccountBalanceDaoTest`,
  `InstrumentDaoTest`, `ExchangeRateCacheDaoTest`, `AppSettingsDaoTest`.
  All passed against the real implementations first try — no bugs found.
  `CLAUDE.md` was also updated (`828107b`) to require a Robolectric test
  for any new/changed DAO query with `WHERE`/`JOIN`/date-range/aggregation
  logic going forward.
- Low #5 (`applicationIdSuffix` doc/code mismatch) — `README.md` already
  documented the debug package as `dev.fitiavana.accounting.dev`, confirming
  `.dev` (the code) was the established convention, not `.debug`. Fixed by
  updating `CLAUDE.md`'s "Build variants" line to say `.dev`.

Remaining open items: Medium #1 (controller duplication), Medium #3
(`Thread`/`runOnUiThread` duplication in `AddTransactionActivity`), and
Low #6–7. See the updated recommendation list at the bottom for current
status per item.

## Overview

The codebase is in good shape overall. It genuinely follows the MVVM
layering rule documented in `CLAUDE.md` — no Activity/Fragment was found
calling a Repository or DAO method directly (they only pass repositories
into ViewModelFactory constructors, per the pattern in
`AppContainer.kt:14-15`). Feature areas are consistently organized, test
coverage is broad for pure logic (builders, presenters, calculators), and
Room migrations are handled carefully (17 versions, each additive/data-
preserving, with explicit handling of SQLite's lack of `RENAME COLUMN`
support pre-3.25). The main gaps are: **package structure has drifted from
what `CLAUDE.md` documents**, one god-Activity (`AddTransactionActivity`,
529 lines) that mixes threading/UI/mode-switching concerns, a repeated raw
`Thread`-based concurrency pattern instead of a shared executor, and real
duplication between the three transaction-mode controllers.

## Architecture

**Layering is respected.** Verified by grepping every `ui/**/*Fragment.kt`
and `ui/**/*Activity.kt` for repository usage — all matches are of the form
`container.xRepository` being threaded into a `XViewModelFactory`
constructor, never into a direct repository call (e.g.
`AccountsFragment.kt:52-54`, `AddTransactionActivity.kt:73-76`). DAOs are
never referenced outside `features/*/`.

**Package structure has drifted from CLAUDE.md.** The doc describes:
```
data/model/, data/dao/, data/repository/, db/, ui/<feature>/
```
but the actual layout groups entity + DAO + repository together per
feature under `features/<feature>/` (e.g.
`features/accounts/{Account,AccountDao,AccountRepository,AccountTypes,LiquidityLevels}.kt`,
`features/transactions/{Transaction,TransactionDao,TransactionRepository,TransactionEntry,TransactionWithEntries}.kt`).
This is arguably a *better* structure (feature-cohesive, fewer cross-package
jumps to touch one feature), but the docs are now actively misleading for
anyone (or any future Claude session) using `CLAUDE.md` as a map. **Update
`CLAUDE.md`'s package-structure section to describe `features/<name>/`
instead of `data/{model,dao,repository}/`.**

**✅ Fixed** in `a2f1bb5` — `CLAUDE.md` now documents the `features/<name>/`
layout.

**Repository layer is intentionally thin** — most repositories are 1:1
pass-throughs over a DAO (`TransactionRepository.kt:5-22`,
`AccountRepository.kt` at 12 lines, `InstrumentRepository.kt` at 20 lines).
This is fine given Room DAOs already return `LiveData`/entities directly,
but it means the "Repository" layer currently adds indirection without much
behavior in the simple cases — acceptable, not a defect, just worth
knowing when deciding whether a new repository method needs a repository
test or is adequately covered via the ViewModel test that exercises it.

**Concurrency pattern: raw `Thread`, not `Executor`/coroutines.** ViewModels
and controllers take `runInBackground: (() -> Unit) -> Unit` /
`runOnUiThread: (() -> Unit) -> Unit` lambdas, and every call site in
`AddTransactionActivity.kt` wires the same pair:
```kotlin
runInBackground = { Thread(it).start() },
runOnUiThread = { runOnUiThread(it) }
```
This appears **4 times** in `AddTransactionActivity.kt` alone (lines 101-102,
133-134, 151-152, 267-268). Each call spins up a brand-new `Thread` with no
pooling/cancellation — for a small local-only app this is low-risk, but it's
duplicated boilerplate that belongs in one place (e.g. a small
`BackgroundRunner` object or a constant lambda pair built once in `onCreate`
and reused). **Medium priority**: extract `private val runners = Pair(...)`
once per Activity, or a shared `UiUtils.newBackgroundRunner()` helper.

**Singletons use double-checked locking correctly** —
`AppDatabase.getInstance` (`db/AppDatabase.kt:265-292`) and
`AppContainer.getInstance` (`AppContainer.kt:47-53`) both use the standard
`@Volatile` + `synchronized` pattern. Consistent and correct.

## Code Structure

**God Activity**: `ui/transactions/AddTransactionActivity.kt` is 529 lines
and is by far the largest file in the project (next largest is
`EntryRowController.kt` at 393). It owns 4 data-entry modes (Classic,
Simple Transfer, Instrument Transfer, Instrument Income), tab-switching,
date/time pickers, and delegates to 4 different mode controllers. It's not
unreasonable for an Activity that hosts a tabbed multi-mode form, but at
529 lines it's a good candidate to extract the tab-switching/mode-lifecycle
logic into a smaller coordinator class, the way the per-mode logic already
is (`SimpleTransferController`, `InstrumentTransferController`,
`InstrumentIncomeController`, `EntryRowController`). **Low priority** —
readable today, but growth-prone since it's the file every new transaction
type touches first.

**Real duplication between `InstrumentIncomeController` and
`InstrumentTransferController`.** A side-by-side diff of the first 60 lines
shows near-identical structure: both define a private `Side` data holder
with `spinner/textBalance/textNewBalance/account/balance/instrumentBalance`,
both wire a `TextWatcher` that calls `updateNewBalances` twice then
`updateAmountBasePreview()`, both have a `loadBalance(side, account)` that
races on `side.account?.id != account.id`, both have `hideBalances`,
`populateSpinner`, `selectedAccount`. See
`InstrumentIncomeController.kt:57-153` vs.
`InstrumentTransferController.kt` (same shape, `From`/`To` instead of
`asset`/`revenue`). This is the kind of duplication the project's own DRY
rule (`CLAUDE.md` "Code" section) calls out. **Medium priority**: extract a
shared `AccountSideController`/`Side` abstraction (spinner wiring, balance
loading, populate/select) parameterized by debit/credit semantics and label
resources, with each mode controller supplying only its formula
(`InstrumentTransferBuilder.computeBaseAmount` vs.
`InstrumentValueCalculator.computeBaseAmount`) and validation messages. This
would cut both controllers by roughly a third and centralize the "a newer
selection may have won the race" guard (currently duplicated comment-and-
logic at `InstrumentIncomeController.kt:125` and the equivalent in
`InstrumentTransferController.kt`).

**`ReportPeriodSelector` is a good example of the pattern the rest of the
transaction controllers should follow**: pure, stateless, well-documented
(`docs/reports-feature.md`), fully covered by
`ReportPeriodSelectorTest.kt`, and reused across Balance Sheet / Income
Statement / Changes in Equity without those builders knowing about dates at
all. Worth pointing to as the target shape when refactoring the transaction
controllers above.

**`UiUtils.formatAmountAr` convention is followed correctly** — a repo-wide
grep for the banned inline pattern
(`getString(R.string.amount_ar, TransactionDisplay.formatAmount(...))`)
found zero matches outside `UiUtils.kt` itself. Good adherence to a rule
that's easy to silently violate.

## Code Style

**`!!` usage is rare and mostly justified** (7 occurrences total). Two
patterns:
- `ExchangeRateRepository.kt:70` — `it.first.coingeckoId!!` inside a
  `groupBy` whose receiver was already filtered for non-null
  `coingeckoId` upstream (`eligible`); safe, but a `filterNotNull`-style
  restructure or a comment noting the invariant would make it locally
  obvious without needing to trace `eligible`'s construction.
- `EditInstrumentActivity.kt:81,154,156,180` and
  `EditAccountActivity.kt:326,341` — `instrumentCode!!` / `accountId!!`
  reading an `Intent` extra stashed as a nullable field. These fire only
  after a null-check earlier in the same method in most cases, but because
  the check and the `!!` are in different methods/branches, a future edit
  could easily introduce a crash. **Low priority**: convert the nullable
  `var instrumentCode: String? = null` field pattern to a
  `lateinit var instrumentCode: String` set once in `onCreate` from the
  `Intent`, eliminating the need for `!!` at each use site.

**No `TODO`/`FIXME` markers anywhere** — either the project has no known
debt markers, or debt isn't being tracked inline. Given the "no
half-finished implementations" project rule, this is consistent, not a
red flag.

**Hardcoded currency-code strings in KDoc only** (`"USDT"`, `"USD"` in
`CoinGeckoResponseParser.kt:14`, `YahooFinanceResponseParser.kt:14`) — these
are inside `/** ... */` example JSON payloads, not live code, so not an
issue.

**Debug build suffix doesn't match `CLAUDE.md`.** `CLAUDE.md` says the debug
variant uses applicationIdSuffix `.debug`; `app/build.gradle.kts:60`
actually sets `applicationIdSuffix = ".dev"`. Minor doc/code drift — pick
one and fix the other. Low priority but a 30-second fix.

**KDoc quality is consistently good** on the non-trivial classes
(`InstrumentIncomeController.kt:21-32`, `ReportPeriodSelector`,
`AppContainer.kt:13-16`) — explains *why*, not *what*, in line with the
project's own commenting rule.

## Testing

**TDD claims are broadly credible.** Pure-logic classes (builders,
calculators, presenters, `ReportPeriodSelector`) all have a matching
`*Test.kt`, and `ReportsViewModelTest`/`HomeViewModelTest` mock repositories
with Mockito and call the `xSync` synchronous counterparts directly rather
than the `Thread`-launching public methods, exactly as documented in
`docs/reports-feature.md:145-155` — good evidence the "call the sync method
directly, avoid `Thread` in tests" pattern is a deliberate, followed
convention, not incidental.

**Coverage gaps**: 16 main-source files have no matching test file,
concentrated in the `features/*/` entity/DAO/repository files:
`Account.kt`, `AccountRepository.kt`, `AccountTypes.kt`,
`LiquidityLevels.kt`, `AccountBalance.kt`, `ExchangeRateCache.kt`,
`Instrument.kt`, `InstrumentRepository.kt`, `AccountLines.kt`,
`ReportRow.kt`, `AppSettings.kt`, `AppSettingsRepository.kt`,
`TransactionEntry.kt`, `Transaction.kt`, `TransactionRepository.kt`,
`TransactionWithEntries.kt`. Most of these are `@Entity` data classes (no
logic to test — fine to skip) or 1-2-method pass-through repositories
(`AccountRepository.kt` is 12 lines, `InstrumentRepository.kt` 20 lines) —
those are implicitly exercised through the ViewModel tests that mock them,
so this is a **low-priority** gap, not a real hole. Worth a conscious
decision, though: if any of these repositories grow real logic (e.g. a
future join or computed field), it should get its own test rather than
continuing to rely on indirect coverage.

**`AccountDaoTest.kt` is the only DAO-level Robolectric test** — the other
DAOs (`TransactionDao`, `InstrumentDao`, `AccountBalanceDao`,
`ExchangeRateCacheDao`, `AppSettingsDao`) have no direct Room-integration
test; they're exercised only indirectly through repository mocks in
ViewModel tests, meaning a broken `@Query` in, say, `TransactionDao`
wouldn't be caught by any test in the suite. **Medium priority**: at least
one Robolectric `androidx.room.testing`-backed test per DAO with a non-
trivial `@Query` (anything with a `WHERE`/join/date-range filter, e.g.
`TransactionDao.getFilteredWithEntries`, `AccountBalanceDao`'s balance
queries) would catch SQL regressions that a mocked-repository test
structurally cannot.

**✅ Fixed** in `59915a1` — added `TransactionDaoTest.kt` (15 tests, covers
`getFilteredWithEntries` date-range+join+LiveData, the debit/credit sum
queries, min/max datetime), `AccountBalanceDaoTest.kt` (6 tests),
`InstrumentDaoTest.kt` (7 tests), `ExchangeRateCacheDaoTest.kt` (4 tests),
and `AppSettingsDaoTest.kt` (4 tests). All 36 new tests passed against the
existing DAO implementations on the first run — no SQL bugs found. `CLAUDE.md`
was also updated (`828107b`) to require this going forward for any new/
changed non-trivial `@Query`.

## Build & Configuration

**`build.gradle.kts` is clean and small** (109 lines), single-module,
version catalog (`libs.versions.toml`) used throughout — no inline version
strings found in the dependency block.

**Signing credential resolution matches the documented pattern**: `local
.properties`-only via `signingProperty()` (`app/build.gradle.kts:16-17`),
no environment-variable fallback (confirms `CLAUDE.md`'s note and the
recent commit history removing that fallback). Consistent, no issues found
— did not attempt to read `local.properties` (blocked by project hook, and
out of scope for a source review anyway).

**Room migrations (`db/AppDatabase.kt`, 294 lines, `SCHEMA_VERSION = 17`)
are handled with unusual discipline** for a hobby-scale app: every ALTER
that SQLite can't do natively (column rename, FK constraint change) is
done via the create-new-table/copy/drop/rename dance
(`MIGRATION_9_10:159-176`, `MIGRATION_11_12:188-204`,
`MIGRATION_12_13:206-227`), and every migration recreates the indices it
drops. No `fallbackToDestructiveMigration()` escape hatch was found — good,
since that would silently wipe user data on a schema mismatch in
production.

**`compileSdk`/`targetSdk` are 36 while `minSdk` stays 19** — correctly
following the CLAUDE.md guidance to target API 19 compatibility while
compiling against a modern SDK; the API-level guard in `UiUtils.kt:26`
(`Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP`) is a good example
of the "compatibility notes/checks" CLAUDE.md asks for when using newer
APIs.

## Prioritized Recommendations

### High
- None found. No correctness bugs, no layering violations, no security
  issues in the reviewed source.

### Medium
1. **Extract shared logic between `InstrumentIncomeController` and
   `InstrumentTransferController`** (`ui/transactions/`) — real, sizeable
   duplication (Side holder, spinner wiring, balance-load race guard,
   populate/select helpers). Directly violates the project's own DRY rule.
   — **Open.**
2. ~~**Add DAO-level Robolectric tests** for `TransactionDao`,
   `InstrumentDao`, `AccountBalanceDao`, `ExchangeRateCacheDao`,
   `AppSettingsDao` — currently only `AccountDao` has one, and hand-written
   `@Query` filters (date ranges, joins) are exactly the kind of thing
   mocked-repository tests can't catch.~~ — **✅ Fixed in `59915a1`.**
3. **Centralize the repeated `Thread`/`runOnUiThread` lambda pair** in
   `AddTransactionActivity.kt` (4 duplicate call sites) into a single
   reusable helper. — **Open.**

### Low
4. ~~**Update `CLAUDE.md`'s package-structure section** — it still describes
   `data/model/`, `data/dao/`, `data/repository/`, but the real layout is
   `features/<name>/` (entity + DAO + repository colocated). Fix the doc,
   not the code — the actual structure is reasonable.~~ — **✅ Fixed in
   `a2f1bb5`.**
5. ~~**Fix the `applicationIdSuffix` doc/code mismatch** — `CLAUDE.md` says
   `.debug`, `app/build.gradle.kts:60` sets `.dev`.~~ — **✅ Fixed** — updated
   `CLAUDE.md` to say `.dev`, matching the code and `README.md`.
6. Consider extracting the tab/mode-lifecycle logic out of
   `AddTransactionActivity.kt` (529 lines) as the file continues to grow
   with new transaction modes. — **Open.**
7. Replace the handful of `Intent`-extra `!!` usages in
   `EditInstrumentActivity.kt` / `EditAccountActivity.kt` with `lateinit
   var` fields set once in `onCreate`, removing the need for `!!` at each
   read site. — **Open.**
