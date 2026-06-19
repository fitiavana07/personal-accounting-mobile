# Accounting

Accounting app with features like accounts, balances, journal, reports.

## Target Platform

This is an Android project targeting **API 19 (KitKat)**. Avoid using APIs above API 19 without adding appropriate compatibility notes or checks.

## General

- **Language**: Kotlin.
- **Build system**: Gradle, with Kotlin DSL `build.gradle.kts`

## Architecture

- **UI:** Android Views (XML layouts) — Jetpack Compose requires API 21+ and is not compatible with the API 19 target.
- **Architecture pattern:** MVVM (ViewModel + LiveData) — current Android best practice for View-based apps.
- **Local storage:** Room (SQLite ORM).

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

- Account ID is a UUID stored as `String`; generate with `UUID.randomUUID().toString()`.

## Testing

- **JUnit**: Unit testing framework
- **Mockito**: Mocking library for unit tests

## UI

- Minimalist UI design. Simple colors
- Focus on functionality, more than fancy visuals