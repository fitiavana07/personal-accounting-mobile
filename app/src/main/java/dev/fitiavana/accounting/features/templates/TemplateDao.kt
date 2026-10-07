package dev.fitiavana.accounting.features.templates

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction

@Dao
interface TemplateDao {

    @RoomTransaction
    @Query("SELECT * FROM transaction_templates ORDER BY createdAt DESC")
    fun getAllWithEntriesSync(): List<TemplateWithEntries>

    @RoomTransaction
    @Query("SELECT * FROM transaction_templates WHERE id = :id")
    fun getWithEntriesSync(id: String): TemplateWithEntries?

    @Query("SELECT * FROM transaction_templates")
    fun getAllTemplatesSync(): List<TransactionTemplate>

    @Query("SELECT * FROM template_entries")
    fun getAllEntriesSync(): List<TemplateEntry>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTemplate(template: TransactionTemplate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTemplates(templates: List<TransactionTemplate>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEntries(entries: List<TemplateEntry>)

    @Query("DELETE FROM transaction_templates WHERE id = :id")
    fun deleteTemplate(id: String)

    @Query("DELETE FROM template_entries")
    fun deleteAllEntries()

    @Query("DELETE FROM transaction_templates")
    fun deleteAllTemplates()
}
