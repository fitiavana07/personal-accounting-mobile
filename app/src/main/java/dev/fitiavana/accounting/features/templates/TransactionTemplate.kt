package dev.fitiavana.accounting.features.templates

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import dev.fitiavana.accounting.features.accounts.Account

/** Which Add Transaction mode a template pre-fills. Stored as text so new modes need no migration. */
object TemplateModes {
    const val CLASSIC = "classic"
    const val SIMPLE_TRANSFER = "simple_transfer"
    const val INSTRUMENT_TRANSFER = "instrument_transfer"
    const val INSTRUMENT_INCOME = "instrument_income"
}

/**
 * Which account picker of a mode an entry belongs to. The debit or credit side follows from the slot
 * (e.g. FROM is credited, TO is debited), so it is not stored; Classic rows are free-form and only
 * remember their order.
 */
object TemplateSlots {
    const val FROM = "from"
    const val TO = "to"
    const val ASSET = "asset"
    const val REVENUE = "revenue"
    const val ROW = "row"
}

/** A named, reusable choice of mode and accounts. Amounts and notes are never stored. */
@Entity(tableName = "transaction_templates")
data class TransactionTemplate(
    @PrimaryKey val id: String,
    val name: String,
    val mode: String,
    val createdAt: Long
)

/**
 * One account chosen in a template. Deleting the account removes the entry (and the template then
 * reads as incomplete, see [TemplateWithEntries.isComplete]); deleting the template removes its entries.
 */
@Entity(
    tableName = "template_entries",
    foreignKeys = [
        ForeignKey(
            entity = TransactionTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index("accountId")]
)
data class TemplateEntry(
    @PrimaryKey val id: String,
    val templateId: String,
    val accountId: String,
    val slot: String,
    val position: Int
)

/** A slot and the account picked for it, before it gets an id (see [TemplateRepository.save]). */
data class TemplateSlot(val slot: String, val accountId: String)

data class TemplateWithEntries(
    @Embedded val template: TransactionTemplate,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val entries: List<TemplateEntry>
) {
    /** The account chosen for [slot], or null when the template has none (e.g. the account was deleted). */
    fun accountFor(slot: String): String? = entries.firstOrNull { it.slot == slot }?.accountId

    /** Classic rows in their saved order. */
    fun rowAccountIds(): List<String> =
        entries.filter { it.slot == TemplateSlots.ROW }.sortedBy { it.position }.map { it.accountId }

    /** False when an account was deleted and the template can no longer fill its mode's form. */
    fun isComplete(): Boolean = when (template.mode) {
        TemplateModes.SIMPLE_TRANSFER, TemplateModes.INSTRUMENT_TRANSFER ->
            accountFor(TemplateSlots.FROM) != null && accountFor(TemplateSlots.TO) != null

        TemplateModes.INSTRUMENT_INCOME ->
            accountFor(TemplateSlots.ASSET) != null && accountFor(TemplateSlots.REVENUE) != null

        TemplateModes.CLASSIC -> rowAccountIds().size >= MIN_CLASSIC_ROWS
        else -> false
    }

    private companion object {
        const val MIN_CLASSIC_ROWS = 2
    }
}
