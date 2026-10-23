# Pinned asset on Home

One asset of the Home gains/losses block can be pinned: it is always listed first.

- **Gesture**: long-press a row. Long-pressing another row moves the pin; long-pressing the pinned row
  unpins it. The pinned row shows a small pin icon before its name. The summary header is not pinnable.
- **Storage**: `AppSettings.pinnedAccountId` (nullable) on the single settings row, so there is at most one
  pin. It is a synced, backed-up column (Room schema 24, `MIGRATION_23_24`).
- **Writes**: `AppSettingsRepository.togglePin` runs inside `ChangeTracker.inTransaction` and stamps the
  settings record, like `setMonthlyLivingExpenses`. Both read the current row and `copy` it, so neither
  overwrites the other's field. Synchronous: call it off the main thread (`HomeFragment` uses a `Thread`).
- **Sync/backup**: the settings record is merged whole, last writer wins (see `p2p-sync.md`). The JSON key
  `pinnedAccountId` is optional (`BackupRowJson.kt`), so data from older versions reads as "no pin".
- **Ordering**: `HomeItemBuilder.build(..., pinnedAccountId)` moves the pinned item first (stable, the rest
  keep their order) and sets `HomeItem.isPinned`. A pin on an account that is deleted or not listed
  (below the 10,000 Ar threshold, no intermediary…) is ignored; the pin is not cleared.
- **Tests**: `HomeItemBuilderTest`, `AppSettingsRepositoryTest`, `SyncJsonTest`, `HomeViewModelTest`,
  `HomeAdapterTest`, `Migration23To24Test`.
