# Accounting

Accounting app with features like accounts, balances, journal, reports.

Build and test

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

RoomDatabase singleton in
app/src/main/java/dev/fitiavana/accounting/db/AppDatabase.kt

### Package structure (by feature)

```
data/
  model/       # Room @Entity classes
  dao/         # Room @Dao interfaces
  repository/  # Repository classes (ViewModel talks to these, not DAOs directly)
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

## On new features

After every new feature added:

- generate a testing checklist
- write unit tests (new or update existing ones) conforming to this
  checklist, for everything that is worth to be unit-tested 