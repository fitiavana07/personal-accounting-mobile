# Cloud Backup (Google Drive, encrypted)

Once a day, **even when the app is not running**, the whole database is backed up, **encrypted**, as one `.accbak`
file in a Google Drive folder the user picked. No server, no OAuth, no Google SDK: the folder is chosen with the
Storage Access Framework (SAF), so Drive is just one of the document providers the system picker offers.

It is **isolated** from the manual backup, the local auto-backup (`docs/auto-backup-feature.md`) and P2P sync
(`docs/p2p-sync.md`): own alarm, own preferences, own passphrase. Those keep producing plain, unencrypted files,
and the P2P sync passphrase is unrelated to the Cloud Backup one. It reuses `BackupRepository.export()` and
`restore()` unchanged.

Menu: overflow → **Cloud Backup** (`ui/cloudbackup/CloudBackupActivity`).

## Compatibility note: Android 6.0+ only

The app targets API 19, but picking a folder and creating/deleting files inside it is API 21+, and sealing the
passphrase with an AndroidKeyStore AES key is API 23+. So the feature **requires Android 6.0 (API 23)**. Below
it the menu item stays, the screen says "Cloud Backup needs Android 6.0 or newer", and everything is disabled.
`isCloudBackupSupported()` is the one place that knows; the classes that need it are annotated `@RequiresApi(23)`
or guarded by `SDK_INT >= 23`.

## The file format (`.accbak`, `BackupEnvelope`)

```
"ACCBK" | version u8 (1) | kdf u8 (1 = PBKDF2-HMAC-SHA256) | iterations u32 | salt 16 | nonce 12 | AES-256-GCM(gzip(json)) + tag
```

- The 27 header bytes are authenticated as GCM associated data: changing the iteration count (or anything) fails
  like a wrong passphrase. Fresh random salt and nonce for every file.
- The iteration count (100 000 today) lives in the header, so it can be raised later without breaking old files.
  A count above 2 000 000 is refused (a hostile header must not burn the CPU).
- GCM cannot tell a wrong passphrase from damage; both are `WRONG_PASSPHRASE_OR_DAMAGED`. A file without the
  magic is `NOT_AN_ENVELOPE`; a newer version or key derivation is `UNSUPPORTED_VERSION`.
- The crypto primitives are shared with P2P sync: `features/crypto/AesGcm.kt` (Bouncy Castle GCM, used directly
  because the platform provider rejects `GCMParameterSpec` before API 21) and `pbkdf2Sha256`. `SyncCrypto`
  delegates to them with an unchanged wire format.

## Pieces (`features/cloudbackup/`)

| Piece | Role |
|---|---|
| `CloudBackupManager` | The logic, pure Kotlin with injected export, repository, store, scheduler, network, notifier, clock. Run, schedule, retry, notify, restore. |
| `CloudBackupRepository` | The connected folder: connect/disconnect, upload, read, prune, list. |
| `CloudFolder` / `SafCloudFolder` | The folder as a small interface; the SAF implementation uses `DocumentsContract` (no `androidx.documentfile`). Every failure is an `IOException`. |
| `CloudBackupStore` / `SharedPreferencesCloudBackupStore` | Prefs file `cloud_backup`: folder uri and name, enabled, passphrase, last success, last error, failure count. |
| `SecretCipher` / `KeystoreSecretCipher` | Seals the passphrase with an AES-256-GCM key in the AndroidKeyStore. If the key is lost the passphrase reads as missing and must be typed again. |
| `AlarmManagerCloudBackupScheduler`, `CloudBackupReceiver` | Its own alarm (request code 1, action `...CLOUD_BACKUP`), `goAsync()` + a background thread. `BootReceiver` also re-arms it. |
| `ConnectivityNetworkChecker` | `activeNetwork` has the internet capability. Only a cheap pre-check. |
| `AndroidCloudBackupNotifier` | Failure notification on its own channel; tap opens the screen. |
| `CloudBackupPolicy` | Constants shared with the screen: `.accbak`, keep 7, passphrase minimum 8 characters. |

Wiring: `AppContainer.cloudBackupManager`. UI talks to `CloudBackupViewModel` only.

## Flow of a run (`runBackup`)

1. No folder or no passphrase → `NOT_CONFIGURED`; nothing is retried (the user has to fix the setup).
2. **Offline** → skipped, **not counted as a failure**, retried in 1 h.
3. Export → `BackupEnvelope.encode` → one new file `accounting_cloud_backup_<yyyy-MM-dd_HHmmss>.accbak`
   (debug builds: `accounting_dev_cloud_backup_...`). A file that fails halfway is deleted again.
4. **Success** → last-success time stored, error and failure count cleared, the notification cleared, **pruned to
   the newest 7** (names sort chronologically; only files of this build's prefix and `.accbak` are ever deleted;
   other files and the other build's files are untouched), next alarm in 24 h. A pruning error does not fail the run.
5. **Failure** (folder unreachable or revoked → `FOLDER`, anything else → `UNEXPECTED`) → counted, retry in 1 h.
6. **Notification**, once per streak: after **3 failures in a row**, or when no backup succeeded for **more than 3
   days** (the long-offline case, counted from the last success or from when it was switched on). Needs the
   `POST_NOTIFICATIONS` permission on Android 13+, asked when the switch is turned on; without it the screen still
   shows the status.

Same cadence as the local auto-backup: 24 h interval, 1 h retry, catch-up at app start (`MainActivity.onCreate`
→ `runIfDue()`), re-armed on boot and on app update. Switching it off, or disconnecting the folder, cancels the alarm.

## Debug and release

They are separate installs (`.dev` suffix) with separate preferences, so each remembers its own folder and
passphrase. Pick **different folders** (the debug screen says so). The distinct file prefix is a second safety
net: even in the same folder, pruning never touches the other build's files.

## Restore

**Restore from cloud** lists the `.accbak` files in the folder, newest first (any prefix). After a red confirmation:

- the **stored passphrase** is used without asking;
- if none is stored, or it fails, the user is asked for the passphrase of that backup (used for that restore
  only, never saved);
- then `BackupRepository.restore(json)` runs unchanged, and the result is shown by `ui/common/RestoreResultPresenter`
  (shared with the manual restore: older-schema warning, newer-schema refusal).
- The database is only touched once the file has been decrypted. Wrong passphrase, not a backup file, newer format
  and unreachable folder each have their own message.

Changing the passphrase does **not** re-encrypt the files already in the folder; they stay readable with the old one
(typed at restore).

## Not synced, not backed up

`cloud_backup.xml` is excluded from Android's own Auto Backup and device transfer
(`backup_rules.xml`, `data_extraction_rules.xml`): the sealed passphrase is useless without this phone's key.

## Tests

JVM and Robolectric (no emulator): `BackupEnvelopeTest`, `AesGcmTest`, `CloudBackupManagerTest` (fakes in
`CloudBackupFakes.kt`), `SharedPreferencesCloudBackupStoreTest`, `SafCloudFolderTest` (a fake documents provider),
scheduler/receiver/notifier/network tests, `CloudBackupViewModelTest`, `CloudBackupActivityTest`. Robolectric has no
AndroidKeyStore, so tests seal secrets with `InMemorySecretCipher`; `KeystoreSecretCipher` and the real Drive provider
are covered by the manual checklist below.

## Manual checklist (device, Android 6.0+)

1. Debug build: Cloud Backup → Choose Drive folder (open the picker's ☰ menu → Drive) → pick/create `Accounting-dev`.
2. Set a passphrase (8+ characters), turn on **Back up to the cloud every day**, tap **Back up now** → a `.accbak`
   appears in Drive, a few KB, not readable as text. Status shows the time.
3. Back up 8+ times → only the newest 7 remain; any other file in the folder is untouched.
4. Release build: its own folder and passphrase; files use the other prefix.
5. Airplane mode + **Back up now** → "Offline: it will try again automatically"; no failure counted; back online,
   the next retry uploads.
6. Delete the Drive folder, or remove the app's access in Drive, then Back up now → folder error on the screen;
   after 3 failures (or 3 days) one notification, tapping it opens the screen; a later success removes it.
7. Restore from cloud: with the stored passphrase → no prompt; change the passphrase then restore an old file →
   prompted; wrong passphrase → clear message and the data is untouched.
8. Restore a file made by an older schema → the existing older-version warning.
9. Manual backup, local auto-backup and P2P sync behave as before (no passphrase anywhere).
10. API 19-22 device: the screen shows "Cloud Backup needs Android 6.0 or newer".
