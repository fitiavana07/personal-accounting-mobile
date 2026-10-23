package dev.fitiavana.accounting.features.sync

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.alerts.GainLossAlert
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.settings.AppSettings
import dev.fitiavana.accounting.features.templates.TemplateEntry
import dev.fitiavana.accounting.features.templates.TransactionTemplate
import dev.fitiavana.accounting.features.transactions.Transaction
import dev.fitiavana.accounting.features.transactions.TransactionEntry

/**
 * How a merge writes rows. Upserts update a row in place (an `INSERT OR REPLACE` would delete it
 * first and fire the foreign key cascades); deletes take ids in chunks because older SQLite
 * versions allow few variables per statement (see [SyncRepository]).
 */
@Dao
interface SyncDao {
    @Upsert
    fun upsertInstruments(rows: List<Instrument>)

    @Upsert
    fun upsertAccounts(rows: List<Account>)

    @Upsert
    fun upsertTransactions(rows: List<Transaction>)

    @Upsert
    fun upsertTransactionEntries(rows: List<TransactionEntry>)

    @Upsert
    fun upsertTemplates(rows: List<TransactionTemplate>)

    @Upsert
    fun upsertTemplateEntries(rows: List<TemplateEntry>)

    @Upsert
    fun upsertAlerts(rows: List<GainLossAlert>)

    @Upsert
    fun upsertSettings(settings: AppSettings)

    @Query("DELETE FROM instruments WHERE code IN (:codes)")
    fun deleteInstruments(codes: List<String>)

    @Query("DELETE FROM accounts WHERE id IN (:ids)")
    fun deleteAccounts(ids: List<String>)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    fun deleteTransactions(ids: List<String>)

    @Query("DELETE FROM transaction_entries WHERE id IN (:ids)")
    fun deleteTransactionEntries(ids: List<String>)

    @Query("DELETE FROM transaction_templates WHERE id IN (:ids)")
    fun deleteTemplates(ids: List<String>)

    @Query("DELETE FROM template_entries WHERE id IN (:ids)")
    fun deleteTemplateEntries(ids: List<String>)

    @Query("DELETE FROM gain_loss_alerts WHERE id IN (:ids)")
    fun deleteAlerts(ids: List<String>)

    @Query("DELETE FROM app_settings")
    fun deleteSettings()
}
