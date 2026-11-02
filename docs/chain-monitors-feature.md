# Chain monitors

A notification when a contract's ERC-20 balance on **Base** goes above an amount. Built for "the Available amount of
Acres 40 (DeFi) Aero", which is the USDC balance of the vault contract, but generic: any holder address, any token.

## Configuring

Home shortcut **Monitors** (or the header of the Monitors block on Home) opens the Monitors screen
(`ui/chainmonitors/ChainMonitorsActivity`). **Add monitor** (or tapping one) opens a dialog:

- **Label**: shown on Home and in the notification.
- **Contract address**: the holder whose balance is watched (`0x` + 40 hex digits; the checksum casing is not enforced).
- **Token contract address**: Base USDC (`0x8335…2913`) by default.
- **Notify when greater than**: a decimal in token units, `0` or more (`0` is valid: notify as soon as it is above 0).
  A comma is accepted as the decimal separator. Kept as text, so it is exact (`ChainMonitorsViewModel.parseThreshold`).
- **Interval**: every 15 or every 30 minutes.

Saving reads the token's `symbol()` and `decimals()` from the chain (so the address must be a real ERC-20 on Base;
`NOT_A_TOKEN`, `NO_NETWORK` and `FAILED` are shown on the token field and nothing is saved), re-arms the alarm and
checks right away. On Android 13+ the first *Add monitor* asks for the notification permission.

Data: `ChainMonitor` (`chain_monitors`: `id` UUID, `label`, `holderAddress`, `tokenAddress`, `tokenSymbol`,
`tokenDecimals`, `threshold`, `intervalMinutes`, `createdAt`), added by `MIGRATION_24_25` (schema v25). It is synced
and backed up like every other table (`SyncKind.CHAIN_MONITOR`, optional `chainMonitors` key in backups so older
backups still restore). It has no foreign key to accounts.

## Reading the balance (`network/evm`)

`HttpErc20BalanceFetcher` posts a JSON-RPC `eth_call` (`balanceOf(holder)`, `symbol()`, `decimals()`) to the public
`https://mainnet.base.org` through the shared OkHttp client (`AppContainer.httpClient`), no web3 library. `Erc20Codec`
builds the call data and decodes the results (uint256 via `BigInteger`, scaled with `BigDecimal.movePointLeft`;
`symbol()` accepts both a `string` and a `bytes32`). The result is exact, never a `Double`.

Errors are typed (`RpcException`): `NoNetwork` (unknown host, connect failure), `Timeout`, `HttpStatus(code)`, `Rpc`
(the node's `error`, e.g. reverted) and `Malformed`. `RetryPolicy` retries timeouts, HTTP 5xx and 429 up to twice
(1 s, then 2 s); no internet and everything else fail straight away.

mainnet.base.org's chain ends at GlobalSign Root CA, which API 19 devices already trust, so no certificate is bundled.

## When it fires (`features/chainmonitors/MonitorEvaluator`)

- Fires once when the balance is **strictly greater** than the threshold while armed, then disarms.
- Re-arms when the balance is back at or below the threshold, so it can fire again on the next rise.
- Unlike gain/loss alerts, a monitor with no usable state is **seeded armed**: a balance already above the threshold
  notifies on the first check (creation, or after an edit).
- No balance (a failed fetch): nothing changes, nothing fires.

The state lives in `chain_monitor_states` (`monitorId`, `armed`, `signature`, `lastCheckedAt`, `lastBalance`), a
**device-local** table: not synced, not backed up, deleted with its monitor. `signature` is
`holder|token|threshold`; a state that no longer matches (the monitor was edited here or on another device) is
discarded and re-seeded. Editing the label or the interval keeps the state.

## When it is checked (`features/chainmonitors/ChainMonitorCheckManager`)

1. **Alarm** (`AlarmManagerMonitorScheduler`, request code 3, `ChainMonitorCheckReceiver`): `onAlarm()` arms the next
   tick first, at the shortest interval among the monitors (nothing is armed, and the alarm is cancelled, when no
   monitor is left), then `check()` fetches only the monitors that are **due** (their interval has elapsed since
   `lastCheckedAt`, with a 1 minute slack for early ticks). Inexact like the other alarms (`setAndAllowWhileIdle` on
   API 23+, `set` below), so "every 15 minutes" is approximate and Doze may defer it.
2. **Home refresh** (launch and pull to refresh): `HomeViewModel.refreshMonitors` runs `check(force = true)`, which
   fetches every monitor, then publishes `rows()` (the last known balance of each) for the Home block.
3. **Saving or deleting a monitor** re-arms (`refreshSchedule`) and, for a save, checks right away.
4. **App start** (`MainActivity` through `ChainMonitorsViewModel.ensureScheduled`, also asking for the notification
   permission when a monitor exists) and **boot** (`BootReceiver`, `ensureScheduled`, which does not touch the database)
   re-arm the alarm.

A failed fetch leaves that monitor's state alone, so it stays due and the next tick retries; no internet stops the
remaining fetches of that run; nothing throws out of the alarm. States are saved before the notification is posted,
so a crash cannot notify the same crossing twice. A run with many monitors and retries can take a while inside the
receiver (`goAsync`).

## Home

A **Monitors** block after the P2P prices (`ui/home/HomeMonitorsAdapter`): one line per monitor, label and last balance
with the token (`1,234.5 USDC`), `–` before the first reading, and under the label when it was last read: "Updated
14:32" for a reading from today, "Updated 11/1/26, 14:32" for an older one, "Not fetched yet" before the first one.
That is the time of the last **successful** fetch (`MonitorState.lastCheckedAt`), so after a failed fetch the old time
stays, which shows the balance is stale. It is hidden when there is no monitor. Tapping its
header opens the Monitors screen. The fifth shortcut (`button_monitors`) opens it too.

## Notification

`AndroidMonitorNotifier`: channel `chain_monitors` (API 26+), one notification per monitor (id from the monitor id, so
a repeat replaces it), title "<label>: 1,234.5 USDC", text "Above 0 USDC". Tapping opens the app. Nothing is posted
without the permission (Android 13+) or with notifications turned off.

## Tests

`Erc20CodecTest`, `JsonRpcTest`, `HttpErc20BalanceFetcherTest`, `RetryPolicyTest`, `MonitorEvaluatorTest`,
`ChainMonitorDaoTest`, `ChainMonitorRepositoryTest`, `Migration24To25Test`, `ChainMonitorCheckManagerTest`,
`ChainMonitorAlarmTest` (scheduler, receiver, boot, notifier, amount format), `ChainMonitorsViewModelTest`,
`EditChainMonitorDialogTest`, `ChainMonitorsAdapterTest`, `HomeMonitorsAdapterTest`, and the monitor cases of
`SyncRepositoryTest`, `SyncJsonTest`, `SyncRulesTest`, `StampedWritesTest`, `BackupRepositoryTest`,
`HomeViewModelTest`, `HomeShortcutsAdapterTest` and `HomeFragmentTest`.
