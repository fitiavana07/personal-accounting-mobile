# Peer-to-peer sync

The user's devices keep the same ledger in step over the local Wi-Fi, with no server and no account. Devices find
each other automatically, each one behaves as if it synced to a server (its changes are pushed, the others' are
pulled), and conflicts are settled by a fixed merge rule. The design is ported from the learning-mgmt app's
`docs/p2p-sync.md`; this page describes what is specific to the accounting app.

## What syncs

| Synced (one record each) | Not synced |
|---|---|
| Instrument (key: its code) | `account_balances` (derived, rebuilt after every merge) |
| Account | `exchange_rate_cache`, CEX and P2P prices |
| **Transaction together with its entries** | Backup preferences, CEX pair choices |
| **Template together with its entries** | The device id, the passphrase, the auto-sync switch |
| The settings row (monthly living expenses; key `"0"`) | |

A transaction and its entries are one record, and so are a template and its entries: they are stamped, merged
and deleted together, so two versions can never be mixed. Transactions are never edited in the app (only created,
or bulk-deleted by the debug *Clear all*), so in practice a transaction is created once and then deleted or not.

## Concepts

- **Device id**: random UUID made on first use (`SyncSettingsStore.deviceId`), kept in the device-local `sync`
  preferences, which are excluded from cloud backup and device transfer (`backup_rules.xml`,
  `data_extraction_rules.xml`): a restored id would be shared by two phones, and the passphrase must not leave.
- **Device name**: `Adjective Noun` derived from the id (`DeviceName`); a peer with the same name shows `Name · XXXX`.
- **Passphrase**: typed on every device (at least 8 characters after trimming). It defines the group and the key.
- **Stamp** `(time, deviceId)`: the version of a record, ordered by time then device id. `StampClock` never goes
  backwards and is pushed past every stamp received, so a phone with a slow clock still orders its writes after
  what it synced. **Tombstone**: a deleted record keeps its stamp (`deleted = true`) so the deletion propagates;
  tombstones are never purged.
- A row that has no stamp (data from before sync existed) counts as stamp `(0, "")`, older than any real write.

## Debug and release never see each other

Both builds install side by side (applicationIdSuffix `.dev`), even on one phone, so they are separated at three
independent layers, all taken from `SyncIdentity.forBuild()` (`BuildConfig.DEBUG`):

| | Release | Debug |
|---|---|---|
| UDP discovery port | 41889 | 41890 |
| `app` field of discovery packets | `accounting-sync` | `accounting-sync-dev` |
| Crypto namespace (PBKDF2 salt, group-tag label) | `accounting-sync-v1` | `accounting-sync-dev-v1` |

Packets of the other flavor are ignored, and even the same passphrase gives another key and another group tag, so a
cross-flavor session could not authenticate anyway.

## Protocol

- **Discovery**: UDP broadcast on the flavor's port to the broadcast address of every IPv4 network and to
  255.255.255.255. Packet (JSON, at most 1024 bytes): `app`, `protocol` (1), `type` (`announce` or `query`),
  `deviceId`, `name`, `port` (TCP port of the session server), `group` (group tag). Announce on start and every 5 s;
  a query (sent by *Refresh*) makes every group member answer directly. A peer not heard for 15 s expires.
  `PeerRegistry` ignores our own packets, other groups and other protocol versions.
- **Security**: PBKDF2-HMAC-SHA256 (100 000 iterations) gives a 256-bit key; messages are AES-256-GCM with a random
  12-byte nonce (`nonce || ciphertext || tag`); the group tag is the first 8 bytes of
  `HMAC-SHA256(key, "<namespace>-group")` in hex. A message that fails authentication is never used. The platform has
  no PBKDF2-SHA256 before API 26, so `SyncCrypto` uses **Bouncy Castle** directly (`PKCS5S2ParametersGenerator`, not
  registered as a provider). The threat model is a curious person on the same Wi-Fi.
- **Session**: one round trip over TCP (3 s connect, 15 s read timeout, at most 4 connections served at once, frames
  of at most 16 MiB: 4-byte big-endian length then payload). The initiator sends `request(snapshot)`; the responder
  merges it, then answers `response(its snapshot after the merge)` (or `failure`); the initiator merges that. A
  responder that cannot decrypt hangs up without answering ("No answer" for the initiator).
- **When**: while the app is on screen and a passphrase is set. A new peer is synced with when this device's id is
  smaller, or when this device has local changes that peer has not been synced up to; a local change is pushed to
  every peer about 3 s after the last one (auto-sync); the user can tap a peer or *Sync all*; *Refresh* forgets and
  rediscovers. Applying a peer's rows is not a local change and triggers nothing.

## Merge and repair

Per record, last write wins: the higher stamp wins, a tombstone competes like any edit, equal stamps change nothing
(`SyncMerge`, commutative, associative and idempotent). Rows are then repaired (`SyncRepair`, deterministic and
idempotent) because merging row by row can break rules across rows:

1. an account whose instrument is gone loses that reference (its data and transactions are kept: a missing
   reference is better than deleting the account with its transactions);
   likewise, an account whose pre-associated revenue account (`linkedRevenueAccountId`, a plain id, not a
   foreign key) is gone loses that link;
2. a transaction with an entry on an account that is gone is dropped whole (and tombstoned);
3. a template entry on an account that is gone is removed (the template counts as changed, as the foreign key does locally).

Everything the repair modified or removed is stamped as a fresh local write, so peers take it. `SyncRules` then
checks unique ids and every foreign key; if it fails, the whole merge is rolled back and the snapshot rejected.

## Applying a snapshot (`SyncRepository.apply`)

One database transaction: reject another schema version or an inconsistent snapshot (a row without a live stamp, a
stamp without a row, duplicate ids, an entry without its transaction), read local rows and stamps, merge, observe the
highest stamp received, repair, verify, store the stamps taken, stamp what the repair changed, write only the rows
that differ (children before parents on delete, parents first on insert; accounts are updated before instruments are
deleted; `@Upsert` so no foreign key cascade fires; deletes in chunks of 500), then **recalculate every balance**
(`BalanceRepository.recalculateAll`) when rows changed. Any failure rolls everything back, stamps included.

**Known limitation**: deleting an account on one device while another records a transaction on it, both offline,
loses that transaction (rule 2). A device left offline needs nothing special.

## Write path

`ChangeTracker` is the only place that stamps writes. Each repository calls it **inside its own transaction**
(`tracker.inTransaction { ... }`): `InstrumentRepository`, `AccountRepository` (`deletedAccount` also restamps the
templates that pick the account, since the cascade removes their entry), `TransactionRepository` (`save` stores a
transaction with its entries atomically; `clearAll` tombstones every transaction), `TemplateRepository`,
`AppSettingsRepository`, and `BackupRepository.restore` (`replaceAll`: restamp what the backup brought, tombstone
what it replaced, otherwise the next sync would bring the old data back). The debug *Clear all transactions* also
recalculates balances.

Schema version 20 adds the `sync_meta` table (`kind`, `id`, `stampTime`, `stampDevice`, `deleted`, primary key
`(kind, id)`) with `MIGRATION_19_20`; the backup file format is unchanged except that a backup of another schema
version is refused, as before. The row JSON shapes are shared between backup and sync (`BackupRowJson.kt`).

## Where each part lives (`features/sync/`, UI in `ui/sync/`)

| Part | Code |
|---|---|
| Identity per build flavor | `SyncIdentity` |
| Stamps, clock, merge | `Stamp`, `StampClock`, `RecordKey`, `Meta`, `SyncKind`, `SyncMerge` |
| Names, peers | `DeviceName`, `PeerRegistry`, `DiscoveryPacket` |
| Crypto | `SyncCrypto` |
| Storage, stamping | `SyncMetaRow`/`SyncMetaDao`, `ChangeTracker`, `SyncSettingsStore` |
| Snapshot, merge, repair | `SyncSnapshot`/`SyncData`, `SyncJson`, `SyncRules`, `SyncRepair`, `SyncDao`, `SyncRepository` |
| Network | `Discovery`, `UdpDiscoveryChannel`, `LockedDiscoveryChannel`, `WifiDiscoveryChannels`, `SyncServer`/`SyncClient`, `FrameIO`, `SyncMessage`, `SyncSession` |
| When to sync | `SyncCoordinator` (implements `SyncControls`), `ForegroundTracker` (registered in `AccountingApplication`) |
| UI | `SyncActivity`, `SyncViewModel`, `SyncPeersAdapter`, `SyncStatusFormatter` |

Concurrency uses kotlinx-coroutines (Android API 19 runs it; the app start was checked on a device before building
on it). Database calls stay synchronous, as everywhere else in the app: sessions run them on IO threads.

## Android integration

- **Permissions**: `INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`.
- **Multicast lock**: held by `WifiDiscoveryChannels` for as long as the discovery channel is open; without it Android
  drops incoming broadcasts. Taken only once the port is bound.
- **Foreground**: `ForegroundTracker` counts started activities, so moving between screens never stops and
  restarts sync. Leaving the app stops discovery and the server and clears the peer list.
- **Entry point**: overflow menu of the main screen, *Sync with other devices*.

## Tests (all on the JVM, none needs a device)

Pure: `SyncMergeTest`, `StampClockTest`, `DeviceNameTest`, `SyncIdentityTest`, `PeerRegistryTest`, `FrameIOTest`,
`DiscoveryPacketTest`, `SyncCryptoTest` (RFC 7914 vector), `SyncSnapshotTest`, `SyncRulesTest`, `SyncRepairTest`,
`SyncJsonTest`, `SyncMessageJsonTest`, `ForegroundTrackerTest`. Persistence (Robolectric, in-memory Room):
`Migration19To20Test`, `SyncMetaDaoTest`, `ChangeTrackerTest`, `StampedWritesTest`, `SyncSettingsStoreTest`,
`SyncBackupRulesTest`. Merge: `SyncRepositoryTest` runs two in-memory databases (`SyncDevice`) through snapshots.
Protocol: `SyncSessionTest` and `SyncTransportTest` (real loopback sockets), `DiscoveryTest` (virtual time),
`UdpDiscoveryChannelTest`, `LockedDiscoveryChannelTest`, `WifiDiscoveryChannelsTest`. Coordinator:
`SyncCoordinatorTest` (two devices, fake network, real sessions). UI: `SyncViewModelTest`, `SyncStatusFormatterTest`,
`SyncPeersAdapterTest`, `SyncActivityTest`, `MainMenuSyncTest`, `AccountingApplicationTest`, `SyncManifestTest`.

## Manual checks on real devices

Not coverable on the JVM: broadcast delivery on real Wi-Fi, the multicast lock, API 19 behaviour, two phones.

1. Install on two phones on the same Wi-Fi, set the same passphrase: each lists the other with a name.
2. Refresh clears and re-lists the peers.
3. Create an instrument, an account, a transaction and a template on one phone: they appear on the other, balances correct.
4. Debug build: *Clear all transactions* on one phone empties the other after a sync, and balances go to 0.
5. Edit the same account on both while apart, then reconnect: the later edit wins on both.
6. Restore a backup on one phone, then sync: the other follows the restored data.
7. Wrong passphrase on one: they do not see each other.
8. Auto-sync off: nothing moves until a peer is tapped or *Sync all* is used.
9. Airplane mode or no Wi-Fi: the screen reports the problem; Refresh recovers.
10. Install the debug (`.dev`) and the release build side by side with the same passphrase, on one phone and across two
    phones: debug never lists release, and the other way round.
11. Leave the app: the phone stops being listed on the other one.
12. Open the Sync screen on an API 19 device: it renders and the list updates.
