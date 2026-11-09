# Bank accounts

Tracks bank accounts on their own, outside the double-entry accounts
(`features/accounts`). Single entry: every transaction is on one bank account;
a **credit** is a positive amount (balance up), a **debit** a negative one
(balance down). Everything is in the base currency (Ar, whole numbers).

## Data (`features/bankaccounts/`)

- `BankAccount` (`bank_accounts`): `id` (UUID), `name`, `balance`,
  `balanceDate`. Unlike a normal account, the balance is stored on the entity:
  it is the sum of the account's transactions. `balanceDate` is the latest
  `transactionDateTime` (null while there is no transaction). It may go
  negative.
- `BankAccountTransaction` (`bank_account_transactions`): `id`, `accountId`
  (FK, cascade), signed `amount`, `note`, `createdAt`, `transactionDateTime`.
  Transactions are never modified or deleted once added, same as the normal
  transactions; there is no entry table.
- `BankAccountRepository`: `createAccount`, `rename`, `addTransaction`. Adding
  a transaction inserts it and updates the balance and balance date in one
  `ChangeTracker.inTransaction`.
- Schema version 26 (`MIGRATION_25_26`).

**Never `REPLACE` a bank account row**: it deletes the row first and the
foreign key cascade would delete its transactions. The DAO uses `@Upsert`.

## Sync and backup

Both are included.

- Two `SyncKind`s: `BANK_ACCOUNT` (name) and `BANK_ACCOUNT_TRANSACTION`.
- `addTransaction` stamps only the transaction, not the account. The balance and
  balance date are *derived*: `SyncRepair` recomputes them from the merged
  transactions after every merge (like balances of normal accounts), so two
  devices adding transactions at the same time converge on the sum, and a
  rename on one device is not overwritten by a transaction on another.
- `SyncRepair` also drops (tombstones) a transaction whose account is gone.
- Backup: `bankAccounts` and `bankAccountTransactions` arrays, optional on
  restore so older backups still restore.

## UI (`ui/bankaccounts/`)

- Home shortcuts (second row, "Bank accounts") → `BankAccountsActivity`: list of
  name, balance and "As of <date>" (or "No transactions yet"); the overflow
  menu "Add bank account" opens `EditBankAccountDialog` (name only); a FAB opens
  `AddBankAccountTransactionActivity` without a preselected account.
- Tap an account → `BankAccountDetailActivity`: name, balance and balance date
  on top, transactions below (newest first; green `+` credit, red `-` debit,
  each with the running "Balance: Ar …" after it, computed in the ViewModel
  from the sum of that transaction and all older ones),
  a FAB to add a transaction, and an overflow menu "Edit" that renames the
  account (name is the only editable field).
- FAB → `AddBankAccountTransactionActivity`: Debit (left, default) / Credit
  (right) toggle, positive whole amount, optional note, date and time (defaults
  to now). Opened from the detail screen it targets that account; opened from
  the list (no `account_id` extra) it shows an account spinner, defaulting to
  the first account (so the only one, when there is one).
- Layering: Activity → ViewModel → `BankAccountRepository` → `BankAccountDao`.

## Tests

`BankAccountDaoTest`, `BankAccountRepositoryTest`, `Migration25To26Test`, the
ViewModel and adapter tests in `ui/bankaccounts/`, and the bank account cases in
`SyncRepositoryTest`, `SyncRepairTest`, `SyncRulesTest`, `SyncJsonTest`,
`BackupRepositoryTest` and `StampedWritesTest`.
