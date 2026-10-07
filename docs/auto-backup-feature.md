# Automatic daily backup

Writes a JSON backup (same format as the manual **Backup** menu item, see `BackupRepository.export()`) once
a day, **even when the app is not running**. Works on API 19: it uses `AlarmManager` (WorkManager and
JobScheduler need newer APIs) and adds no dependency.

## How it works

| Piece | File (`features/backup/`) | Role |
|---|---|---|
| `AutoBackupManager` | `AutoBackupManager.kt` | All the logic: is a backup due, write the file, prune, arm the next alarm. Pure Kotlin with injected clock, directory, scheduler and prefs, so it is JVM-testable. |
| `AutoBackupScheduler` / `AlarmManagerBackupScheduler` | `AutoBackupManager.kt`, `AlarmManagerBackupScheduler.kt` | One fixed `PendingIntent`, so every `scheduleAt` replaces the previous alarm. `setAndAllowWhileIdle` on API 23+, `set` before. API 19 alarms are inexact, which is fine for a daily backup. |
| `AutoBackupReceiver` | `AutoBackupReceiver.kt` | Receives the alarm (`ACTION_AUTO_BACKUP`), runs `manager.onAlarm()` on a background thread under `goAsync()`. |
| `BootReceiver` | `BootReceiver.kt` | `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` clear alarms, so it calls `manager.ensureScheduled()`. Needs the `RECEIVE_BOOT_COMPLETED` permission. |
| `BackupPrefsStore` | `BackupPrefsStore.kt` | SharedPreferences (`auto_backup`): enabled flag (default **on**) and last successful backup time. |
| UI | `ui/backup/AutoBackupViewModel.kt`, `dialog_auto_backup.xml`, `MainActivity` | Overflow menu **Auto-backup**: switch, last backup time, folder path. |

Wiring: `AppContainer.autoBackupManager`.

### Flow

1. **Alarm fires** → `AutoBackupReceiver` → `onAlarm()` → if enabled, `runBackup()`: write file, store the time,
   prune, arm the alarm for **24 h later**.
2. **Failure** (export error, folder unavailable): nothing is written, the last-backup time is **not** updated,
   and the next attempt is armed **1 h later** (`RETRY_MILLIS`). Nothing throws.
3. **Reboot / app update** → `BootReceiver` → `ensureScheduled()`: arms the alarm at *last backup + 24 h*, or
   about 1 min from now if that is already overdue.
4. **App start** (`MainActivity.onCreate`, fresh launch only) → `runIfDue()`: backs up if the last one is 24 h old
   or missing, otherwise just re-arms the alarm. This is the safety net for a force-stopped app (Android does not
   deliver alarms to force-stopped apps until the next launch).
5. **Switch off** in the dialog → `setEnabled(false)` cancels the alarm; **on** re-arms it.

### Where the files go

`context.getExternalFilesDir("backups")` (falls back to `filesDir/backups` if external storage is unavailable).
On a device that is `Android/data/<applicationId>/files/backups/` (`dev.fitiavana.accounting.dev` for debug).
No storage permission is needed. Files are named `accounting_auto_backup_<yyyy-MM-dd_HHmmss>.json`, written to
a `.tmp` file and renamed so a crash never leaves a half-written backup. Only the newest
`MAX_BACKUPS` (**7**) are kept; manual backups and any other files in the folder are never deleted.

**Limitation:** this folder is deleted when the app is uninstalled, so it protects against data corruption and
bad edits, not against losing the phone. Use the manual **Backup** to keep a copy elsewhere.

Restoring an auto-backup: the files are ordinary backups, so copy one somewhere the document picker can reach
and use **Restore**. Schema rules are unchanged: a backup only restores on the same `AppDatabase.SCHEMA_VERSION`.

## Tests

Automated (`./gradlew testDebugUnitTest`):

- `AutoBackupManagerTest` – due / not due / disabled, file written with the exported JSON, last time and next alarm,
  pruning to 7 (and leaving non-auto files alone), export failure and missing folder (retry in 1 h), app-start
  catch-up, alarm, `ensureScheduled` (including overdue), enable/disable.
- `SharedPreferencesBackupPrefsStoreTest` – defaults and persistence.
- `AlarmManagerBackupSchedulerTest` (Robolectric `ShadowAlarmManager`) – alarm time and type, targets
  `AutoBackupReceiver`, rescheduling replaces rather than adds, cancel.
- `BackupReceiversTest` – the alarm broadcast writes a backup and re-arms; disabled does nothing; boot and package
  replaced only re-arm; unrelated actions are ignored.
- `AutoBackupViewModelTest`, `MainActivityAutoBackupTest` – menu entry, dialog switch, folder, last backup line.

### Manual test on a device (run these yourself)

Do not use adb on the phone in this project; everything below works from the device UI or a file manager.
Use the **debug** build (`dev.fitiavana.accounting.dev`).

1. **First backup on launch.** Install, open the app. Open **⋮ → Auto-backup**: the switch is on and *Last backup*
   shows a time. In a file manager open `Android/data/dev.fitiavana.accounting.dev/files/backups/` (on some
   Android versions this folder is hidden from file managers; use the system *Files* app or a connected PC).
   One `accounting_auto_backup_*.json` is there.
2. **No duplicate within 24 h.** Close and reopen the app. No new file appears.
3. **Daily alarm with the app closed.** Swipe the app away (do **not** use *Force stop*). Set the device date
   forward by one day (Settings → Date & time, automatic off). Within a few minutes the alarm fires (inexact on
   API 19; on newer Android it can wait for a maintenance window) and a second file appears without opening the
   app. Set the date back afterwards.
4. **After reboot.** Reboot, do not open the app, move the date forward one more day: a new file appears
   (the boot receiver re-armed the alarm).
5. **Force stop catch-up.** *Force stop* the app in system settings, move the date forward a day, open the app:
   a new file appears right after launch (app-start catch-up).
6. **Pruning.** Repeat step 3 until there are more than 7 files; only the newest 7 remain.
7. **Disable.** Turn the switch off, move the date forward a day, wait: no new file. Turn it back on: an alarm is
   armed again.
8. **Restore round trip.** Copy an auto-backup to Downloads, **⋮ → Restore**, pick it, confirm: data matches
   what was backed up.
