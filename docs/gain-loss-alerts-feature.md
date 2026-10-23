# Gain/loss alerts

A notification when an account's gain/loss % (the one shown per account on Home) crosses a threshold you set.
Per account only: there is no alert on the Home total.

## Configuring

Home shortcut **Alerts** opens the Alerts screen (`ui/alerts/AlertsActivity`). **Add alert** (or tapping an alert)
opens a dialog: account, type (*Crossing above* / *Crossing below*) and the threshold in percent. Enter a positive
number for a gain and a negative one for a loss. `0` and unreadable values are rejected (`AlertsViewModel.parseThreshold`;
a comma is accepted as the decimal separator). The type is separate from the sign: *crossing below +10%* warns when
a gain shrinks. Only accounts that have a gain/loss on Home can be chosen (`AccountGainLoss.positions`). On Android
13+ the first *Add alert* asks for the notification permission.

Data: `GainLossAlert` (`gain_loss_alerts`: `id` UUID, `accountId` FK with cascade delete, `type`, `thresholdPercent`,
`createdAt`), added by `MIGRATION_22_23` (schema v23). It is synced and backed up like every other table.

## When it fires (`features/alerts/AlertEvaluator`)

The gain/loss % comes from `features/balances/AccountGainLoss`, the same code `HomeItemBuilder` uses, so a
notification never disagrees with the screen.

Each alert has an *armed* flag, with a hysteresis of 1 percentage point (`AlertEvaluator.HYSTERESIS`):

- *crossing above*: fires when armed and `% >= threshold`; re-arms when `% <= threshold - 1`.
- *crossing below*: fires when armed and `% <= threshold`; re-arms when `% >= threshold + 1`.
- It fires once per crossing, then stays quiet until re-armed.
- No % (no cached rate, zero book value, account not eligible): nothing changes, nothing fires.
- An alert without a usable state is **seeded silently**: armed unless the % is already past the threshold. So a new
  alert never fires on creation, and neither does one that arrived by sync or restore.

The armed flag lives in `alert_states` (`alertId`, `armed`, `signature`), a **device-local** table: not synced, not
backed up, deleted with its alert. `signature` is `accountId|type|threshold` of the alert when the state was written;
a state that no longer matches (the alert was edited here or on another device) is discarded and re-seeded.

## When it is checked (`features/alerts/AlertCheckManager`)

1. **After the Home rate fetch.** `HomeViewModel.refreshRates` (app launch and pull to refresh) refreshes the rates
   through `RateRefresher`, then calls `check()`, which only reads the cached rates.
2. **Hourly alarm** (`AlarmManagerAlertScheduler`, request code 2, `AlertCheckReceiver`): `onAlarm()` arms the next
   alarm first, then refreshes the rates (skipped when there are no alerts) and checks. Inexact, like the backup
   alarms (`setAndAllowWhileIdle` on API 23+, `set` below), so "hourly" is approximate and Doze may defer it.
3. **Saving an alert** arms the alarm and runs `check()` straight away, which seeds the new alert.
4. **App start and boot** re-arm the alarm (`MainActivity` through `AlertsViewModel.ensureScheduled`, `BootReceiver`),
   since a reboot or a force stop clears it.

A failed fetch or check never throws out of the alarm; the next alarm tries again. States are saved before the
notification is posted, so a crash cannot notify the same crossing twice.

## Notification

`AndroidAlertNotifier`: channel `gain_loss_alerts` (API 26+), one notification per alert (id from the alert id, so a
repeat replaces it), title "<account> is at +12.3%", text "Crossed above +10.0%". Tapping opens the app. Nothing is
posted without the permission (Android 13+) or with notifications turned off.

## Tests

`AccountGainLossTest`, `RateRefresherTest`, `AlertEvaluatorTest`, `AlertCheckManagerTest`, `AlertDaoTest`,
`AlertRepositoryTest`, `Migration22To23Test`, `AlertAlarmTest` (scheduler, receiver, boot, notifier),
`AlertsViewModelTest`, and the alert cases of `BackupRepositoryTest`, `StampedWritesTest`, `SyncRepositoryTest`,
`SyncRepairTest`, `SyncRulesTest` and `SyncJsonTest`.
