# Transaction templates

A template remembers **which Add Transaction mode and which accounts** you used, so a recurring transaction
(rent, withdrawing cash, a monthly transfer) takes one tap instead of re-picking accounts. Amounts, dates and
notes are never stored.

## Using it

- **Save:** on the form step of *New transaction* (any mode), tap **Save as template**, pick every account first
  (all different; Classic needs at least two rows), then give it a name. It works without amounts, and no
  transaction is created.
- **Use:** the mode-selection step lists saved templates under the four mode cards (newest first; the section
  is hidden when there are none). Tap one: the form opens in that mode with the accounts already selected.
- **Delete:** the trash icon on a template row, with a confirmation. Transactions already made are not affected.
- If an account in a template can no longer be picked (for example an instrument account that now has an
  intermediary instrument, or the TO account no longer fits the FROM account's instrument), the form still opens
  and a message asks you to pick the missing accounts again.

## Data model (`features/templates/`, schema v18)

| Table | Columns |
|---|---|
| `transaction_templates` | `id`, `name`, `mode`, `createdAt` |
| `template_entries` | `id`, `templateId` (FK, cascade), `accountId` (FK to `accounts`, cascade), `slot`, `position` |

- `mode` is one of `TemplateModes` (`classic`, `simple_transfer`, `instrument_transfer`, `instrument_income`).
- `slot` says which account picker an entry belongs to (`TemplateSlots`): `from`/`to` for the two transfer modes,
  `asset`/`revenue` for Instrument Income, `row` for Classic.
- **The debit or credit side is not stored.** It follows from the slot (`from` is credited, `to` is debited, as
  the controllers already do), and Classic rows only have an account until you type an amount into the debit or
  credit field, so there is no side to remember. Classic rows keep their order in `position`.
- Deleting an **account** cascades to its template entries. The template row stays but no longer has all the
  slots its mode needs, so `TemplateWithEntries.isComplete()` is false and `TemplateRepository.getAll()` hides it.
  (The account delete screen only blocks accounts that have transactions; a restrict key would have made
  deleting an account fail with an unrelated database error.)
- `MIGRATION_17_18` creates both tables. `Migration17To18Test` compares its SQL with the schema Room generates
  for a fresh database, because Room refuses to open a migrated database whose tables differ from the entities.
- `BackupRepository` exports/restores both tables (`transactionTemplates`, `templateEntries`); a backup without
  those arrays still restores. Restore deletes templates before accounts and inserts them after.

## Code map

- `TemplateRepository` (`save`, `getAll`, `get`, `delete`; `getAll` sorts entries by position and drops incomplete
  templates) → `TemplateDao`. Wired in `AppContainer.templateRepository`.
- `AddTransactionViewModel` exposes `loadTemplates` / `loadTemplate` / `saveTemplate` / `deleteTemplate` (all
  synchronous, call off the main thread, like its other methods).
- Controllers each got `templateSlots()` (the chosen accounts, or null until all are picked) and
  `applyTemplate(template)` (selects the accounts; false and no change if one is unavailable):
  `SimpleTransferController`, `InstrumentTransferController`, `InstrumentIncomeController`; Classic rows use
  `EntryRowController.selectedAccountId()` / `selectAccount()`, and the activity adds or removes rows to match.
  `AccountSideController` gained `selectedAccountId()` / `select()`.
- **Instrument Transfer ordering:** the TO list depends on FROM, and on a real device a spinner selection fires its
  listener asynchronously. `InstrumentTransferController.applyTemplate` therefore keeps the TO account as
  `pendingToAccountId` and selects it when FROM's choice has repopulated the TO list.
- `AddTransactionActivity.intent(context, templateId)` opens a template's form directly (also handy in tests).
  Templates are listed and applied only after the controllers exist, i.e. after the accounts have loaded.

## Tests

`TemplateDaoTest` (in-memory Room: cascade, ordering), `TemplateRepositoryTest` (save, ordering, completeness per
mode), `BackupRepositoryTest` (round trip, ordering), `Migration17To18Test`, `AddTransactionViewModelTest`,
`SimpleTransferTemplateTest`, `InstrumentTransferTemplateTest`, `InstrumentIncomeTemplateTest`,
`EntryRowTemplateTest`, and `AddTransactionTemplatesTest` (list, apply per mode, unavailable account, intent
extra, delete and cancel, save with name, validation, blank name, Classic needs two different accounts).

### Manual checklist (on a device, run it yourself)

1. New transaction → Simple Transfer → pick two accounts → **Save as template** → name it → toast *Template saved*.
2. Go back to the mode cards: the template is listed under **Templates**. Tap it: the Simple Transfer form opens
   with both accounts selected and the amount empty.
3. Repeat for Classic (3+ rows, accounts in order), Instrument Transfer (FROM then TO) and Instrument Income.
4. Try saving with a missing account, the same account twice, and a blank name: each shows a message.
5. Delete a template (confirm, then cancel on another one).
6. Make a backup, restore it: templates are still there. Delete an account used by a template (one without
   transactions): the template disappears from the list.
