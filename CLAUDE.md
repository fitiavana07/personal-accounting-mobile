# Accounting

Accounting app with features like accounts, balances, journal, reports.

Build and test. Make sure to run it after every change.

```
./gradlew assembleDebug testDebugUnitTest
```

## Target Platform

This is an Android project targeting **API 19 (KitKat)**. Avoid using APIs
above API 19 without adding appropriate compatibility notes or checks.

## General

- **Language**: Kotlin.
- **Build system**: Gradle, with Kotlin DSL `build.gradle.kts`

Build variants: `debug` (applicationIdSuffix `.debug`, includes clear-data
menu) and `release`. Both can be installed side-by-side.

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
Repository or DAO directly. A ViewModel may depend on multiple Repositories,
a Repository may depend on multiple DAOs, but a DAO must never depend on a
Repository and a Repository must never be called directly from the UI layer.
If a ViewModel is missing a method you need, add it to the ViewModel (which
delegates to the Repository) rather than reaching past it from the UI layer.

RoomDatabase singleton in
app/src/main/java/dev/fitiavana/accounting/db/AppDatabase.kt

### Package structure (by feature)

```
data/
  model/       # Room @Entity classes
  dao/         # Room @Dao interfaces
  repository/  # Repository classes (only ViewModels talk to these, never UI)
db/            # AppDatabase singleton
ui/
  accounts/    # AccountsFragment, AccountsViewModel, AccountsAdapter
  balances/    # (planned)
  journal/     # (planned)
```

Account ID is a UUID stored as `String`; generate with
`UUID.randomUUID().toString()`.

Features: Accounts, Instruments, Transactions, Balances (read-only),
Roadmap (static)

Each CRUD feature: {Feature}Fragment + {Feature}ViewModel + {Feature}Adapter +
Edit{Feature}Activity + Edit{Feature}ViewModel

Transaction amounts stored as integers

## Testing

- **JUnit**: Unit testing framework
- **Mockito**: Mocking library for unit tests

## UI

- Minimalist UI design. Simple colors
- Focus on functionality, more than fancy visuals
- Respect basic UI/UX principles, such as colors on primary vs. non-primary
  actions, colors on destructive actions

## Code

- Avoid using `@Deprecated` methods when possible, use recommended
  replacement instead.
- Never include Co-Authored-By in commits
- DRY: Reuse code when possible, refactor if needed
- To display a base-currency amount (prefixed "Ar", thousands-separated, no
  parentheses), always use `UiUtils.formatAmountAr(context, amount)` — never
  duplicate `getString(R.string.amount_ar, TransactionDisplay.formatAmount(...))`
  inline. This requires a `Context`, so it's for UI-layer code (Activities,
  Fragments, Adapters) only. For report rows needing contra/parenthesized
  formatting (e.g. Balance Sheet, Income Statement), use `ReportPresenter`'s
  amount formatting instead, which has no `Context` dependency.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`,
`perf`. Use `!` after type/scope (e.g. `feat!:`) for breaking changes.

## Version bumps

When bumping `versionCode`/`versionName` in `app/build.gradle.kts`, do it in
its own commit (`chore: bump version to <versionName> (<versionCode>)`), then
create an annotated git tag `v<versionName>` (e.g. `v1.16`) pointing at that
commit.

## On new features

After every change:

- generate a testing checklist
- write unit tests (new or update existing ones) conforming to this
  checklist, for everything that is worth to be unit-tested 

## When adding new libraries

When adding a new library (in build.gradle.kts / libs.versions.toml),
always verify the app can be built successfully before making changes to
source code.